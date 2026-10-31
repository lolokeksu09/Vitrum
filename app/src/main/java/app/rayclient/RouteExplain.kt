package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/**
 * Read-only prediction from ConfigBuilder's generated Xray rules. No DNS, geodata lookup,
 * socket access or network request is performed. UNKNOWN is deliberately sticky: when an
 * earlier rule could match but cannot be decided locally, later rules cannot prove a route.
 */
enum class RouteVerdict { VPN, DIRECT, BLOCK, EXCLUDED, UNKNOWN }
data class RouteExplanation(val verdict: RouteVerdict, val reason: String)

object RouteExplain {
    /** These are observations, not inferred from a generated config. Nothing measured here. */
    val dnsIpv4 = RouteVerdict.UNKNOWN
    val dnsIpv6 = RouteVerdict.UNKNOWN
    val actualTraffic = RouteVerdict.UNKNOWN

    private enum class Match { YES, NO, UNKNOWN }
    private fun items(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }

    fun evaluate(
        options: RoutingOptions,
        appMode: Int,
        selectedApps: Set<String>,
        candidateApp: String,
        ownPackage: String,
        destination: String,
        port: Int?,
        protocol: String
    ): RouteExplanation {
        if (candidateApp.isBlank()) return RouteExplanation(RouteVerdict.UNKNOWN, "Не выбрано приложение.")
        if (AppRoutePreview.evaluate(appMode, selectedApps, candidateApp, ownPackage) == AppTunPolicy.EXCLUDED)
            return RouteExplanation(RouteVerdict.EXCLUDED, "Android VPN: приложение исключено из TUN. Правила Xray к нему не применяются.")

        val target = destination.trim().lowercase().trimEnd('.')
        if (target.isEmpty()) return RouteExplanation(RouteVerdict.UNKNOWN, "Введите домен или IP-адрес.")
        val ipv4 = RuleCheck.isIpv4(target)
        val ipv6 = RuleCheck.isIpv6(target)
        if (!ipv4 && !ipv6 && (target.all { it.isDigit() || it == '.' } || RuleCheck.invalid(0, target).isNotEmpty() || ':' in target))
            return RouteExplanation(RouteVerdict.UNKNOWN, "Некорректный домен или IP-адрес.")
        if (port != null && port !in 1..65535) return RouteExplanation(RouteVerdict.UNKNOWN, "Порт вне диапазона 1–65535.")
        if (protocol !in setOf("tcp", "udp")) return RouteExplanation(RouteVerdict.UNKNOWN, "Укажите TCP или UDP.")

        val ip = ipv4 || ipv6
        val rules = ConfigBuilder.previewRules(options)
        for ((index, rule) in rules.withIndex()) {
            // Service-only HTTP and internal DNS inputs are not incoming Android TUN traffic.
            if (rule.has("inboundTag")) continue
            val matches = mutableListOf<Match>()
            if (rule.has("network")) {
                val allowed = rule.optString("network").split(',').map { it.trim() }
                matches += if (protocol in allowed) Match.YES else Match.NO
            }
            if (rule.has("port")) matches += matchPort(rule.optString("port"), port)
            if (rule.has("domain")) matches += matchDomain(items(rule.getJSONArray("domain")), target, ip, options.sniff || options.fakeDns)
            if (rule.has("ip")) matches += matchIp(items(rule.getJSONArray("ip")), target, ip, options.domainStrategy != "AsIs" && !options.fakeDns)
            if (Match.NO in matches) continue
            if (Match.UNKNOWN in matches) {
                return RouteExplanation(RouteVerdict.UNKNOWN,
                    "Правило Xray №${index + 1} может сработать, но для точного решения нужны DNS, геобаза или данные соединения: ${label(rule)}.")
            }
            val verdict = when {
                rule.has("balancerTag") -> RouteVerdict.VPN
                rule.optString("outboundTag") == "proxy" -> RouteVerdict.VPN
                rule.optString("outboundTag") == "direct" -> RouteVerdict.DIRECT
                rule.optString("outboundTag") == "block" -> RouteVerdict.BLOCK
                else -> RouteVerdict.UNKNOWN // dns-out, other outbounds need distinct validation
            }
            val catchAll = options.defaultAction != 0 && rule.has("network") &&
                !rule.has("domain") && !rule.has("ip") && !rule.has("port")
            return RouteExplanation(verdict, if (catchAll)
                "«Прочий трафик»: правило Xray №${index + 1}, ${label(rule)}."
                else "Первое совпадение — правило Xray №${index + 1}: ${label(rule)}.")
        }
        val fallback = when (options.defaultAction) {
            1 -> RouteVerdict.DIRECT
            2 -> RouteVerdict.BLOCK
            else -> RouteVerdict.VPN
        }
        return RouteExplanation(fallback,
            "Совпадений нет: применяется «Прочий трафик» (${when (fallback) {
                RouteVerdict.DIRECT -> "напрямую"
                RouteVerdict.BLOCK -> "блокировать"
                else -> "через VPN"
            }}).")
    }

    private fun matchPort(spec: String, port: Int?): Match {
        if (port == null) return Match.UNKNOWN
        val entries = spec.split(',')
        for (s in entries) {
            val ends = s.trim().split('-')
            val a = ends.firstOrNull()?.toIntOrNull() ?: return Match.UNKNOWN
            val b = if (ends.size == 1) a else ends.getOrNull(1)?.toIntOrNull() ?: return Match.UNKNOWN
            if (port in a..b) return Match.YES
        }
        return Match.NO
    }

    private fun matchDomain(tokens: List<String>, target: String, isIp: Boolean, sniff: Boolean): Match {
        if (isIp) return if (sniff) Match.UNKNOWN else Match.NO
        var uncertain = false
        for (raw in tokens) {
            val token = raw.lowercase()
            val value = token.substringAfter(':', token)
            val match = when {
                token.startsWith("full:") -> target == value
                token.startsWith("domain:") -> target == value || target.endsWith(".$value")
                token.startsWith("keyword:") -> value.isNotEmpty() && target.contains(value)
                token.startsWith("geosite:") || token.startsWith("ext:") || token.startsWith("regexp:") -> {
                    uncertain = true; false // geodata/Go regex is not evaluated by Kotlin
                }
                else -> { uncertain = true; false }
            }
            if (match) return Match.YES
        }
        return if (uncertain) Match.UNKNOWN else Match.NO
    }

    private fun matchIp(tokens: List<String>, target: String, isIp: Boolean, lookupPossible: Boolean): Match {
        if (!isIp) return if (lookupPossible) Match.UNKNOWN else Match.NO
        var uncertain = false
        for (token in tokens) {
            if (token.startsWith("geoip:", true) || token.startsWith("ext:", true)) {
                uncertain = true; continue
            }
            when (matchCidr(target, token)) {
                Match.YES -> return Match.YES
                Match.UNKNOWN -> uncertain = true
                Match.NO -> Unit
            }
        }
        return if (uncertain) Match.UNKNOWN else Match.NO
    }

    private fun matchCidr(ip: String, token: String): Match {
        val parts = token.split('/')
        if (parts.size > 2) return Match.UNKNOWN
        val network = parts[0]
        val addr = literal(ip) ?: return Match.UNKNOWN
        val subnet = literal(network) ?: return Match.UNKNOWN
        if (addr.size != subnet.size) return Match.NO
        val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: return Match.UNKNOWN else addr.size * 8
        if (prefix !in 0..addr.size * 8) return Match.UNKNOWN
        for (i in addr.indices) {
            val bits = (prefix - 8 * i).coerceIn(0, 8)
            val mask = (0xff shl (8 - bits)) and 0xff
            if ((addr[i].toInt() and mask) != (subnet[i].toInt() and mask)) return Match.NO
        }
        return Match.YES
    }

    private fun literal(s: String): ByteArray? = when {
        RuleCheck.isIpv4(s) -> s.split('.').map { it.toInt().toByte() }.toByteArray()
        RuleCheck.isIpv6(s) -> runCatching { InetAddress.getByName(s).address }.getOrNull()
        else -> null
    }

    private fun label(rule: JSONObject): String {
        val filters = listOf("domain", "ip", "port", "network").filter { rule.has(it) }
        val name = filters.joinToString("; ") { k ->
            val raw = if (k == "domain" || k == "ip") items(rule.getJSONArray(k)).joinToString(", ") else rule.optString(k)
            "$k: $raw"
        }
        val target = if (rule.has("balancerTag")) "proxy" else rule.optString("outboundTag")
        return "${(name.ifBlank { "без фильтра" } + " → " + target).take(180)}"
    }
}
