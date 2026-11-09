package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

class SubRouting(val name: String, val rules: List<Rule>, val domainStrategy: Int, val dropped: List<String> = emptyList())

/**
 * Маршрутизация, которую панель передаёт в заголовке подписки `routing`.
 * Формат (как у Happ): JSON с полями DirectSites/DirectIp/ProxySites/ProxyIp/BlockSites/BlockIp/DomainStrategy,
 * либо base64 от него, либо ссылка happ://routing/add/<base64>. Поля разбираются без учёта регистра.
 */
object HappRouting {
    private fun decode(s: String): String? {
        var t = s.trim()
        if (t.isEmpty()) return null
        t = t.removePrefix("base64:").trim()
        for (p in listOf("happ://routing/onadd/", "happ://routing/add/")) if (t.startsWith(p)) t = t.removePrefix(p)
        if (t.startsWith("{")) return t
        // «+» в base64 не пробел: URL-декодер трогаем, только если есть %-последовательности, и плюс при этом сохраняем
        val u = (if ('%' in t) runCatching { URLDecoder.decode(t.replace("+", "%2B"), "UTF-8") }.getOrDefault(t) else t).filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/')
        return runCatching { String(Base64.getDecoder().decode(u + "=".repeat((4 - u.length % 4) % 4)), Charsets.UTF_8) }.getOrNull()?.takeIf { it.trim().startsWith("{") }
    }

    private fun list(j: Map<String, Any?>, key: String): List<String> {
        val a = j[key] as? JSONArray ?: return emptyList()
        return (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }
    }

    fun parse(raw: String): SubRouting? {
        val json = decode(raw) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val m = o.keys().asSequence().associate { it.lowercase() to o.opt(it) }
        val rules = mutableListOf<Rule>()
        val dropped = mutableListOf<String>()
        // «напрямую» из чужой подписки не должно выпускать мимо VPN почти всё: слишком широкие записи отбрасываются
        fun add(kind: Int, key: String, action: Int) {
            val all = list(m, key)
            val ok = if (action == 1) all.filter { t -> !RuleCheck.tooBroad(kind, t).also { if (it) dropped += t } } else all
            ok.takeIf { it.isNotEmpty() }?.let { rules += Rule(kind, it.joinToString(","), action) }
        }
        add(1, "blockip", 2); add(0, "blocksites", 2)
        add(1, "directip", 1); add(0, "directsites", 1)
        add(1, "proxyip", 0); add(0, "proxysites", 0)
        if (rules.isEmpty()) return null
        val ds = when ((m["domainstrategy"] as? String)?.lowercase()) { "asis" -> 0; "ipondemand" -> 2; else -> 1 }
        return SubRouting((m["name"] as? String).orEmpty(), rules, ds, dropped)
    }
}
