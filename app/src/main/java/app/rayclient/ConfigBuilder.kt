package app.rayclient

import org.json.JSONArray
import org.json.JSONObject

class Preset(val id: String, val title: String, val desc: String, val tags: List<String>, val asns: List<String> = emptyList())

/** tags — домены и IP-метки из встроенных баз; asns — автономные системы, подсети которых скачиваются отдельно (матчи идут на IP без доменов). */
val PRESETS = listOf(
    Preset("steam", "Valve: Steam и CS2", "Каталог, закачки и серверы игр Valve", listOf("geosite:steam"), listOf("AS32590")),
    Preset("riot", "Riot Games", "League of Legends, Valorant, Wild Rift (международные серверы)", listOf("geosite:riot"), listOf("AS6507")),
    Preset("blizzard", "Battle.net", "Blizzard: Overwatch, Diablo, World of Warcraft", listOf("geosite:blizzard"), listOf("AS57976")),
    Preset("epic", "Epic Games", "Fortnite и лаунчер Epic Games", listOf("geosite:epicgames")),
    Preset("ea", "EA", "Клиент и серверы Electronic Arts", listOf("geosite:ea")),
    Preset("ubisoft", "Ubisoft", "Клиент и серверы Ubisoft", listOf("geosite:ubisoft")),
    Preset("faceit", "FACEIT", "FACEIT: клиент и матчи", listOf("geosite:faceit")),
    Preset("telegram", "Telegram", "Мессенджер", listOf("geosite:telegram", "geoip:telegram")),
)

/** Стратегия основного балансировщика авто-серверов (JSON-подписка): 0 как в панели, иначе своя. */
object BalancerStrategy {
    val TYPES = listOf("", "random", "roundRobin", "leastPing")

    /** Балансировщик со своей стратегией. «Быстрый» (leastPing) нужен замер серверов (burstObservatory): без него остаётся стратегия панели. */
    fun apply(b: JSONObject, choice: Int, hasObservatory: Boolean): JSONObject {
        val t = TYPES.getOrNull(choice) ?: ""
        if (t.isEmpty() || (t == "leastPing" && !hasObservatory)) return JSONObject(b.toString())
        return JSONObject(b.toString()).put("strategy", JSONObject().put("type", t))
    }
}

class Rule(val kind: Int, val value: String, val action: Int, val enabled: Boolean = true) // kind: 0 домены, 1 IP, 2 порты; action: 0 через VPN, 1 напрямую, 2 блокировать

class RoutingOptions(
    val rules: List<Rule> = emptyList(),
    val domainStrategy: String = "IPIfNonMatch",
    val bypassPresets: Set<String> = emptySet(),
    val whitelistDomains: List<String> = emptyList(),
    val transport: Int = 0, // 0 TCP+UDP, 1 только TCP, 2 только UDP
    val exceptRu: Boolean = false,
    val dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    val doh: Boolean = false,
    val presetIps: List<String> = emptyList(),   // подсети игровых серверов (из RIPEstat)
    val whitelistOn: Boolean = false,            // «белый список» включён
    val geoRu: Boolean = false,                  // подключена база runetfreedom: доступна метка geoip:ru-whitelist
    val fragment: Boolean = false,   // TLS-фрагментация: ClientHello к серверу режется на куски (против DPI), только для tls/reality по TCP
    val fragLength: String = FragmentParams.DEFAULT_LENGTH,     // длина куска ClientHello, байт: "N" или "min-max"
    val fragInterval: String = FragmentParams.DEFAULT_INTERVAL, // пауза между кусками, мс
    val blockAds: Boolean = false,   // реклама и трекеры из geosite:category-ads-all блокируются
    val blockV6: Boolean = false,    // IPv6 не используется: Xray ищет только IPv4, запросы AAAA получают пустой ответ, прямые IPv6-адреса блокируются
    val bypassLan: Boolean = true,   // локальная сеть (geoip:private) идёт напрямую
    val defaultAction: Int = 0,      // что делать с трафиком, не подошедшим ни под одно правило: 0 через VPN, 1 напрямую, 2 блокировать
    val balancerStrategy: Int = 0,   // стратегия основного балансировщика авто-серверов: 0 как в панели, 1 случайный, 2 по очереди, 3 быстрый (по задержке)
    val sniff: Boolean = true,       // определять домен по первым байтам соединения (http, tls, quic) для правил по доменам
    val fakeDns: Boolean = false,    // поддельный DNS: приложения получают временные адреса 198.18.0.0/15, Xray по ним восстанавливает домен (правила по доменам без определения по байтам)
    val dnsTcp: Boolean = false,     // обычный DNS (без DoH) по TCP: tcp://адрес
    val dnsQuery: Int = 0,           // что просит Xray при разборе доменов: 0 IPv4 и IPv6, 1 только IPv4, 2 только IPv6
    val logLevel: String = XrayLog.DEFAULT,   // уровень журнала Xray: error, warning, info, debug
    val mtu: Int = TunParams.DEFAULT_MTU,   // MTU туннеля (должен совпадать с MTU интерфейса VpnService)
    val fingerprint: String = "",    // отпечаток TLS (uTLS) вместо указанного в ссылке; пусто — как в ссылке
    val game: Boolean = false,       // игровой режим: TCP NoDelay на соединении с сервером, UDP разрешён даже при «Только TCP»        // DNS-запросы разбирает сам Xray по HTTPS через сервер
)

/** Параметры DNS Xray. */
object DnsParams {
    val QUERY = listOf("UseIP", "UseIPv4", "UseIPv6")
    /** «Не использовать IPv6» сильнее выбора: IPv6 не должен возвращаться ни при каком значении. */
    fun queryStrategy(choice: Int, blockV6: Boolean) = if (blockV6) "UseIPv4" else QUERY.getOrElse(choice) { "UseIP" }
    /** Пулы временных адресов для поддельного DNS: IPv4 198.18.0.0/15 (диапазон для тестов, в сети не встречается) и, если IPv6 нужен, fc00::/18. */
    fun fakePools(withV6: Boolean): JSONArray = JSONArray().put(JSONObject().put("ipPool", "198.18.0.0/15").put("poolSize", 65535))
        .apply { if (withV6) put(JSONObject().put("ipPool", "fc00::/18").put("poolSize", 65535)) }

    /** Строка сервера для Xray: DoH, TCP или обычный UDP. */
    fun server(ip: String, doh: Boolean, tcp: Boolean): String {
        val host = if (ip.contains(':')) "[$ip]" else ip
        return if (doh) "https://$host/dns-query" else if (tcp) "tcp://$host" else ip
    }
}

/** Уровень журнала Xray. На info и debug в журнал попадают адреса и домены соединений (журнал лежит только в закрытой папке приложения). */
object XrayLog {
    val LEVELS = listOf("error", "warning", "info", "debug")
    const val DEFAULT = "warning"
    fun orDefault(s: String) = if (s in LEVELS) s else DEFAULT
}

/** Параметры туннеля. MTU меньше 1280 нельзя (минимум для IPv6), больше 1500 не нужен. */
object TunParams {
    const val DEFAULT_MTU = 1500
    const val MIN_MTU = 1280
    fun valid(mtu: Int) = mtu in MIN_MTU..DEFAULT_MTU
    fun orDefault(mtu: Int) = if (valid(mtu)) mtu else DEFAULT_MTU
}

/** Параметры TLS-фрагментации и отпечаток TLS: проверка введённого, чтобы кривое значение не сломало конфиг Xray. */
object FragmentParams {
    const val DEFAULT_LENGTH = "100-200"
    const val DEFAULT_INTERVAL = "10-20"
    private val re = Regex("""^(\d{1,4})(?:-(\d{1,4}))?$""")

    /** "N" или "min-max", числа от 1 до 9999, min не больше max. */
    fun valid(s: String): Boolean {
        val m = re.matchEntire(s.trim()) ?: return false
        val a = m.groupValues[1].toInt(); val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
        return a in 1..9999 && b in 1..9999 && a <= b
    }

    fun orDefault(s: String, default: String) = s.trim().takeIf { valid(it) } ?: default

    /** Значения uTLS, которые принимает Xray (список из документации Xray; не проверено живым Xray). */
    val FINGERPRINTS = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized")
}

/** Российские адреса и сервисы идут напрямую («Все, кроме РФ»). */
private val RU_DOMAINS = listOf("geosite:category-ru", "geosite:category-bank-ru", "geosite:category-gov-ru", "geosite:category-retail-ru",
    "geosite:yandex", "geosite:vk", "geosite:mailru", "domain:ru", "domain:su", "domain:xn--p1ai")

object ConfigBuilder {
    private fun tokens(s: String) = s.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** Куда вести: выход по тегу или, в авто-режиме, балансировщик (вместо единственного выхода «proxy»). */
    private fun to(o: JSONObject, tag: String, bal: String?): JSONObject = if (tag == "proxy" && bal != null) o.put("balancerTag", bal) else o.put("outboundTag", tag)

    private fun rule(tag: String, ips: List<String>, domains: List<String>, out: MutableList<JSONObject>, bal: String? = null) {
        if (domains.isNotEmpty()) out += to(JSONObject().put("type", "field").put("domain", JSONArray(domains)), tag, bal)
        if (ips.isNotEmpty()) out += to(JSONObject().put("type", "field").put("ip", JSONArray(ips)), tag, bal)
    }

    /** probePath: путь unix-сокета для служебных запросов из приложения (вместо TCP-порта на localhost). */
    fun build(link: ParsedLink, outbound: JSONObject, r: RoutingOptions, probePath: String? = null, accessLog: String? = null): String =
        assemble(listOf(outbound), null, r, probePath, accessLog)

    /** Авто-сервер из JSON-подписки: серверы, балансировщики и burstObservatory берутся из плана, всё остальное (входы, DNS, правила) своё. */
    fun buildAuto(plan: AutoPlan, r: RoutingOptions, probePath: String? = null, accessLog: String? = null): String =
        assemble(plan.outbounds, plan, r, probePath, accessLog)

    private const val FRAGMENT_TAG = "fragment"

    /** Фрагментируем только TCP с tls/reality и только если у выхода ещё нет своей цепочки (dialerProxy у мостов панели не трогаем). */
    private fun fragmentable(ob: JSONObject): Boolean {
        val st = ob.optJSONObject("streamSettings") ?: return false
        if (st.optString("security") !in listOf("tls", "reality")) return false
        if (st.optString("network", "tcp") in listOf("hysteria", "quic", "kcp")) return false
        return st.optJSONObject("sockopt")?.has("dialerProxy") != true
    }

    private fun assemble(outbounds: List<JSONObject>, plan: AutoPlan?, r: RoutingOptions, probePath: String?, accessLog: String?): String {
        val rules = mutableListOf<JSONObject>()
        val bal = plan?.mainBalancer
        val transport = if (r.game && r.transport == 1) 0 else r.transport
        val useFragment = r.fragment && outbounds.any { fragmentable(it) }
        val obs = outbounds.map { o ->
            JSONObject(o.toString()).also { ob ->
                if (r.fragment && fragmentable(ob)) { val st = ob.optJSONObject("streamSettings")!!; st.put("sockopt", (st.optJSONObject("sockopt") ?: JSONObject()).put("dialerProxy", FRAGMENT_TAG)) }
                if (r.fingerprint in FragmentParams.FINGERPRINTS) ob.optJSONObject("streamSettings")?.let { st ->
                    if (st.optString("network", "tcp") !in listOf("hysteria", "quic", "kcp"))
                        for (k in listOf("tlsSettings", "realitySettings")) st.optJSONObject(k)?.takeIf { it.has("fingerprint") }?.put("fingerprint", r.fingerprint)
                }
                if (r.game) { val st = ob.optJSONObject("streamSettings") ?: JSONObject().also { ob.put("streamSettings", it) }; st.put("sockopt", (st.optJSONObject("sockopt") ?: JSONObject()).put("tcpNoDelay", true)) }
            }
        }
        if (probePath != null) rules += to(JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("probe-in"))), "proxy", bal)
        rules += to(JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("dns-internal"))), "proxy", bal)
        if (r.doh || r.blockV6 || r.fakeDns) rules += JSONObject().put("type", "field").put("port", "53").put("network", "tcp,udp").put("outboundTag", "dns-out")
        when (transport) {
            1 -> rules += JSONObject().put("type", "field").put("network", "udp").put("port", "1-52,54-65535").put("outboundTag", "block")
            2 -> rules += JSONObject().put("type", "field").put("network", "tcp").put("outboundTag", "block")
        }
        // IPv6 отключён: прямые IPv6-адреса блокируются (домены Xray разбирает только в IPv4, поэтому двойные сайты не страдают)
        if (r.blockV6) rules += JSONObject().put("type", "field").put("ip", JSONArray(listOf("::/0"))).put("outboundTag", "block")
        for (u in r.rules) {
            if (!u.enabled) continue
            val tag = when (u.action) { 0 -> "proxy"; 1 -> "direct"; else -> "block" }
            val t = tokens(u.value); if (t.isEmpty()) continue
            when (u.kind) {
                0 -> rule(tag, emptyList(), t.map { if (it.contains(':') && it.substringBefore(':') in setOf("geosite", "domain", "full", "regexp", "keyword")) it else "domain:$it" }, rules, bal)
                1 -> rule(tag, t, emptyList(), rules, bal)
                else -> rules += to(JSONObject().put("type", "field").put("port", t.joinToString(",")), tag, bal)
            }
        }
        // свои правила выше сильнее: пользователь может явно пустить домен из списка рекламы через VPN
        if (r.blockAds) rules += JSONObject().put("type", "field").put("domain", JSONArray(listOf("geosite:category-ads-all"))).put("outboundTag", "block")
        if (r.bypassLan) rules += JSONObject().put("type", "field").put("ip", JSONArray(listOf("geoip:private"))).put("outboundTag", "direct")
        if (r.exceptRu) rule("direct", listOf("geoip:ru"), RU_DOMAINS, rules)
        val pIps = mutableListOf<String>(); val pDom = mutableListOf<String>()
        PRESETS.filter { it.id in r.bypassPresets }.forEach { p -> p.tags.forEach { if (it.startsWith("geoip:")) pIps += it else pDom += it } }
        rule("direct", pIps + r.presetIps, pDom, rules)
        val wlIps = if (r.whitelistOn && r.geoRu) listOf("geoip:ru-whitelist") else emptyList()
        if (r.whitelistDomains.isNotEmpty() || wlIps.isNotEmpty())
            rule("direct", wlIps, r.whitelistDomains.map { "domain:$it" }, rules)

        // Трафик, не попавший ни в одно правило, без этого шёл бы в первый выход списка, а не в балансировщик.
        // «Остальной трафик» (по выбору пользователя) идёт напрямую или блокируется; по умолчанию его ведёт балансировщик или первый выход
        when (r.defaultAction) {
            1 -> rules += JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "direct")
            2 -> {
                // DNS приложений идёт через VPN: иначе блокировка «остального» не даёт разобрать и домены из правил «Через VPN»
                rules += to(JSONObject().put("type", "field").put("network", "tcp,udp").put("port", "53"), "proxy", bal)
                rules += JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "block")
            }
            else -> if (plan != null) rules += JSONObject().put("type", "field").put("network", "tcp,udp").put("balancerTag", bal)
        }

        val tun = JSONObject().put("tag", "tun-in").put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("MTU", TunParams.orDefault(r.mtu)))
            // при поддельном DNS определение адреса включено всегда: по нему из временного адреса возвращается домен
            .put("sniffing", JSONObject().put("enabled", r.sniff || r.fakeDns)
                .put("destOverride", JSONArray(if (r.fakeDns) listOf("http", "tls", "quic", "fakedns") else listOf("http", "tls", "quic"))))
        val cfg = JSONObject()
            .put("log", JSONObject().put("access", accessLog ?: "none").put("loglevel", XrayLog.orDefault(r.logLevel)))
            .apply { if (r.fakeDns) put("fakedns", DnsParams.fakePools(DnsParams.queryStrategy(r.dnsQuery, r.blockV6) != "UseIPv4")) }
            .put("dns", JSONObject().put("tag", "dns-internal").put("queryStrategy", DnsParams.queryStrategy(r.dnsQuery, r.blockV6)).put("servers", JSONArray(
                (if (r.fakeDns) listOf("fakedns") else emptyList<String>()) + r.dnsServers.ifEmpty { listOf("1.1.1.1", "8.8.8.8") }.map { DnsParams.server(it, r.doh, r.dnsTcp) })))
            .put("inbounds", JSONArray().put(tun).apply {
                // Служебный вход: HTTP-прокси на unix-сокете в закрытой папке приложения. TCP-порта на localhost нет.
                if (probePath != null) put(JSONObject().put("tag", "probe-in").put("listen", probePath).put("protocol", "http").put("settings", JSONObject()))
            })
            .put("outbounds", JSONArray().apply { obs.forEach { put(it) } }
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .apply { if (useFragment) put(JSONObject().put("tag", FRAGMENT_TAG).put("protocol", "freedom")
                    .put("settings", JSONObject().put("fragment", JSONObject().put("packets", "tlshello").put("length", FragmentParams.orDefault(r.fragLength, FragmentParams.DEFAULT_LENGTH)).put("interval", FragmentParams.orDefault(r.fragInterval, FragmentParams.DEFAULT_INTERVAL))))) }
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
                .apply { if (r.doh || r.blockV6 || r.fakeDns) put(JSONObject().put("tag", "dns-out").put("protocol", "dns")) })
            // при поддельном DNS домены не разрешаются в адреса при выборе маршрута (иначе Xray получил бы поддельные адреса и пустил домен напрямую по правилу «локальная сеть»)
            .put("routing", JSONObject().put("domainStrategy", if (r.fakeDns) "AsIs" else r.domainStrategy)
                .put("rules", JSONArray(rules.map { it }))
                .apply { if (plan != null) put("balancers", JSONArray(plan.balancers.mapIndexed { i, b -> if (i == 0) BalancerStrategy.apply(b, r.balancerStrategy, plan.burst != null) else JSONObject(b.toString()) })) })
        if (plan?.burst != null) cfg.put("burstObservatory", JSONObject(plan.burst.toString()))
        return cfg.toString(2)
    }

    /** Один процесс Xray на все серверы: сокет dir/p<i>.sock выходит строго через сервер i. Портов нет. */
    fun buildProbe(items: List<Pair<ParsedLink, String>>, dir: String, logLevel: String = "warning"): String {
        val inb = JSONArray(); val outs = JSONArray(); val rules = JSONArray()
        items.forEachIndexed { i, (p, ip) ->
            inb.put(JSONObject().put("tag", "p-$i").put("listen", "$dir/p$i.sock").put("protocol", "http").put("settings", JSONObject()))
            outs.put(Links.withAddress(p, ip).put("tag", "o-$i"))
            rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("p-$i"))).put("outboundTag", "o-$i"))
        }
        outs.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        return JSONObject().put("log", JSONObject().put("loglevel", logLevel)).put("inbounds", inb).put("outbounds", outs)
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules)).toString(2)
    }
}
