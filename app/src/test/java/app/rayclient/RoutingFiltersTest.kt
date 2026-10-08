package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingFiltersTest {
    private val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
    private fun cfg(r: RoutingOptions): JSONObject { val p = Links.parse(link); return JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), r)) }
    private fun rules(c: JSONObject) = c.getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun has(c: JSONObject, key: String, value: String, out: String) =
        rules(c).any { r -> r.optString("outboundTag") == out && r.optJSONArray(key)?.let { a -> (0 until a.length()).any { a.getString(it) == value } } == true }
    private fun tags(c: JSONObject) = c.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("tag") } }

    @Test fun defaultsKeepTheOldBehaviour() {
        val c = cfg(RoutingOptions())
        assertTrue(has(c, "ip", "geoip:private", "direct"))
        assertFalse(has(c, "domain", "geosite:category-ads-all", "block")); assertFalse(has(c, "ip", "::/0", "block"))
        assertTrue(c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("enabled"))
        assertEquals("UseIP", c.getJSONObject("dns").getString("queryStrategy")); assertFalse(tags(c).contains("dns-out"))
    }

    @Test fun adsRuleIsAddedAndUserRulesStayAbove() {
        val c = cfg(RoutingOptions(rules = listOf(Rule(0, "ads.example.com", 0)), blockAds = true))
        assertTrue(has(c, "domain", "geosite:category-ads-all", "block"))
        val rs = rules(c)
        val user = rs.indexOfFirst { it.optJSONArray("domain")?.toString()?.contains("ads.example.com") == true }
        val ads = rs.indexOfFirst { it.optJSONArray("domain")?.toString()?.contains("category-ads-all") == true }
        assertTrue("правило пользователя выше фильтра рекламы", user in 0 until ads)
    }

    @Test fun lanBypassCanBeSwitchedOff() = assertFalse(has(cfg(RoutingOptions(bypassLan = false)), "ip", "geoip:private", "direct"))

    @Test fun sniffingCanBeSwitchedOff() =
        assertFalse(cfg(RoutingOptions(sniff = false)).getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("enabled"))

    @Test fun ipv6OffBlocksLiteralsAndAsksOnlyIpv4() {
        val c = cfg(RoutingOptions(blockV6 = true))
        assertTrue(has(c, "ip", "::/0", "block"))
        assertEquals("UseIPv4", c.getJSONObject("dns").getString("queryStrategy"))
        assertTrue("запросы AAAA перехватываются своим DNS-выходом", tags(c).contains("dns-out"))
        assertTrue(rules(c).any { it.optString("outboundTag") == "dns-out" })
    }

    @Test fun xrayLogLevelReachesConfigAndUnknownFallsBack() {
        fun lvl(v: String) = cfg(RoutingOptions(logLevel = v)).getJSONObject("log").getString("loglevel")
        assertEquals("warning", lvl("warning")); assertEquals("debug", lvl("debug")); assertEquals("error", lvl("error"))
        assertEquals("warning", lvl("trace")); assertEquals("warning", cfg(RoutingOptions()).getJSONObject("log").getString("loglevel"))
        assertEquals("warning", XrayLog.orDefault("")); assertEquals(listOf("error", "warning", "info", "debug"), XrayLog.LEVELS)
    }

    private fun dnsServers(c: JSONObject) = c.getJSONObject("dns").getJSONArray("servers").let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test fun dnsTransportAndStrategyReachConfig() {
        val base = RoutingOptions(dnsServers = listOf("1.1.1.1", "2606:4700:4700::1111"))
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), dnsServers(cfg(base)))
        assertEquals(listOf("tcp://1.1.1.1", "tcp://[2606:4700:4700::1111]"), dnsServers(cfg(RoutingOptions(dnsServers = base.dnsServers, dnsTcp = true))))
        assertEquals("DoH важнее TCP", listOf("https://1.1.1.1/dns-query", "https://[2606:4700:4700::1111]/dns-query"),
            dnsServers(cfg(RoutingOptions(dnsServers = base.dnsServers, dnsTcp = true, doh = true))))
        fun q(choice: Int, v6: Boolean = false) = cfg(RoutingOptions(dnsQuery = choice, blockV6 = v6)).getJSONObject("dns").getString("queryStrategy")
        assertEquals("UseIP", q(0)); assertEquals("UseIPv4", q(1)); assertEquals("UseIPv6", q(2)); assertEquals("UseIP", q(9))
        assertEquals("«Не использовать IPv6» сильнее выбора", "UseIPv4", q(2, true))
    }

    @Test fun fakeDnsIsOffByDefaultAndChangesNothing() {
        val c = cfg(RoutingOptions())
        assertFalse(c.has("fakedns")); assertFalse(dnsServers(c).contains("fakedns")); assertEquals("IPIfNonMatch", c.getJSONObject("routing").getString("domainStrategy"))
        assertFalse(c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getJSONArray("destOverride").toString().contains("fakedns"))
    }

    @Test fun fakeDnsConfig() {
        val c = cfg(RoutingOptions(fakeDns = true, sniff = false, domainStrategy = "IPIfNonMatch"))
        assertEquals("fakedns первым в списке серверов", "fakedns", dnsServers(c).first())
        val pools = c.getJSONArray("fakedns"); assertEquals("198.18.0.0/15", pools.getJSONObject(0).getString("ipPool")); assertEquals(2, pools.length())
        val sn = c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing")
        assertTrue("определение адреса включается само", sn.getBoolean("enabled")); assertTrue(sn.getJSONArray("destOverride").toString().contains("fakedns"))
        assertEquals("домены не разрешаются при выборе маршрута", "AsIs", c.getJSONObject("routing").getString("domainStrategy"))
        assertTrue(tags(c).contains("dns-out")); assertTrue(rules(c).any { it.optString("outboundTag") == "dns-out" })
    }

    @Test fun fakeDnsHasNoIpv6PoolWhenIpv6IsOff() {
        assertEquals(1, cfg(RoutingOptions(fakeDns = true, blockV6 = true)).getJSONArray("fakedns").length())
        assertEquals(1, cfg(RoutingOptions(fakeDns = true, dnsQuery = 1)).getJSONArray("fakedns").length())
    }
}
