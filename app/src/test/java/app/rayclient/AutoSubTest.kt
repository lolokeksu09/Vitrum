package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Режим JSON-подписки на выдуманных данных (адреса из блока документации 203.0.113.0/24, UUID нулевые). */
class AutoSubTest {
    private val uuid = "00000000-0000-0000-0000-000000000001"

    private fun vless(tag: String, addr: String, sec: String = "reality", net: String = "tcp", extra: (JSONObject) -> Unit = {}): JSONObject {
        val ss = JSONObject().put("network", net).put("security", sec)
        if (sec == "reality") ss.put("realitySettings", JSONObject().put("serverName", "front.example.org").put("publicKey", "KEY").put("shortId", "ab").put("fingerprint", "chrome"))
        if (sec == "tls") ss.put("tlsSettings", JSONObject().put("fingerprint", "chrome"))
        val o = JSONObject().put("tag", tag).put("protocol", "vless").put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
            .put("address", addr).put("port", 443).put("users", JSONArray().put(JSONObject().put("id", uuid).put("encryption", "none"))))))
            .put("streamSettings", ss)
        extra(ss); return o
    }

    private fun hy2(tag: String, addr: String) = JSONObject().put("tag", tag).put("protocol", "hysteria")
        .put("settings", JSONObject().put("address", addr).put("port", 8443).put("version", 2))
        .put("streamSettings", JSONObject().put("network", "hysteria").put("security", "tls")
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", "x"))
            .put("tlsSettings", JSONObject().put("serverName", "hy.example.org")))

    private fun service() = listOf(JSONObject().put("tag", "direct").put("protocol", "freedom"),
        JSONObject().put("tag", "block").put("protocol", "blackhole"), JSONObject().put("tag", "dns-out").put("protocol", "dns"))

    private fun panelConfig(remarks: String, outs: List<JSONObject>, balTag: String = "Super_Balancer", selector: String = "proxy"): JSONObject =
        JSONObject().put("remarks", remarks)
            .put("inbounds", JSONArray().put(JSONObject().put("tag", "socks").put("port", 10808).put("listen", "127.0.0.1").put("protocol", "socks"))
                .put(JSONObject().put("tag", "http").put("port", 10809).put("listen", "127.0.0.1").put("protocol", "http")))
            .put("dns", JSONObject().put("tag", "dns-internal").put("servers", JSONArray(listOf("9.9.9.9"))))
            .put("outbounds", JSONArray(outs + service()))
            .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch")
                .put("rules", JSONArray().put(JSONObject().put("type", "field").put("domain", JSONArray(listOf("domain:example.ru"))).put("outboundTag", "direct"))
                    .put(JSONObject().put("type", "field").put("network", "tcp,udp").put("balancerTag", balTag)))
                .put("balancers", JSONArray().put(JSONObject().put("tag", balTag).put("selector", JSONArray(listOf(selector)))
                    .put("strategy", JSONObject().put("type", "leastLoad")).put("fallbackTag", "direct"))))
            .put("burstObservatory", JSONObject().put("subjectSelector", JSONArray(listOf(selector)))
                .put("pingConfig", JSONObject().put("destination", "http://detectportal.firefox.com/success.txt").put("interval", "2m").put("sampling", 3).put("timeout", "5s")))

    private fun fixture(): String {
        val auto = panelConfig("🇪🇺 Wi-Fi • Авто-выбор", listOf(
            vless("proxy", "203.0.113.1"),
            vless("proxy-2", "node2.example.org", sec = "tls", net = "xhttp") { ss -> ss.put("xhttpSettings", JSONObject().put("path", "/x").put("mode", "auto")) },
            vless("proxy-3", "node3.example.org", sec = "tls", net = "ws") { ss -> ss.put("wsSettings", JSONObject().put("path", "/w")) },
            vless("proxy-4", "node4.example.org", sec = "tls", net = "xhttp") { ss -> ss.put("xhttpSettings", JSONObject().put("path", "/x").put("host", "cdn.example.org")); ss.getJSONObject("tlsSettings").put("serverName", "sni.example.org") },
            hy2("proxy-5", "203.0.113.5")))
        val bridge = panelConfig("🇩🇪 Россия -> Германия (Мост)", listOf(vless("de-bridge", "203.0.113.9"), vless("de-bridge-2", "203.0.113.10")), "de_bridge_balancer", "de-bridge")
        val plain = JSONObject().put("remarks", "Обычный").put("inbounds", JSONArray()).put("outbounds", JSONArray(listOf(vless("proxy", "203.0.113.20")) + service()))
            .put("routing", JSONObject().put("rules", JSONArray())).put("dns", JSONObject())
        return JSONArray().put(plain).put(auto).put(bridge).toString()
    }

    private fun plans() = AutoSub.parse(fixture(), "sub1")

    @Test fun parseTakesOnlyBalancerConfigs() {
        val p = plans()
        assertEquals(listOf("🇪🇺 Wi-Fi • Авто-выбор", "🇩🇪 Россия -> Германия (Мост)"), p.configs.map { it.name })
        val a = p.configs[0].plan
        assertEquals(listOf("proxy", "proxy-2", "proxy-3", "proxy-4", "proxy-5"), a.outbounds.map { it.getString("tag") })   // без direct, block, dns-out
        assertEquals("Super_Balancer", a.mainBalancer)
        assertNotNull(a.burst); assertEquals("2m", a.burst!!.getJSONObject("pingConfig").getString("interval"))
    }

    @Test fun fallbackNeverPointsToDirect() {
        // у панели запасной выход direct: до первых замеров трафик ушёл бы мимо VPN
        for (c in plans().configs) for (i in 0 until c.plan.balancers.size) {
            val b = c.plan.balancers[i]
            assertNotEquals("direct", b.getString("fallbackTag"))
            assertTrue(c.plan.outbounds.any { it.getString("tag") == b.getString("fallbackTag") })
        }
    }

    @Test fun duplicateNamesGetDistinctStableLinks() {
        val one = panelConfig("Авто", listOf(vless("proxy", "203.0.113.1")))
        val body = JSONArray().put(one).put(JSONObject(one.toString())).toString()
        val l = AutoSub.parse(body, "s").configs.map { it.key }
        assertEquals(2, l.toSet().size); assertTrue(l.all { AutoSub.isAuto(it) })
        assertEquals(l, AutoSub.parse(body, "s").configs.map { it.key })   // повторный разбор даёт те же ссылки (избранное не теряется)
    }

    @Test fun configWithoutResolvableSelectorIsSkipped() {
        val bad = panelConfig("Битый", listOf(vless("x", "203.0.113.1")), selector = "proxy")   // селектор proxy, а выход называется x
        val r = AutoSub.parse(JSONArray().put(bad).toString(), "s")
        assertEquals(0, r.configs.size); assertEquals(1, r.skipped)
    }

    @Test fun brokenChainIsRejected() {
        val chained = vless("proxy", "203.0.113.1") { ss -> ss.put("sockopt", JSONObject().put("dialerProxy", "ghost")) }
        assertEquals(1, AutoSub.parse(JSONArray().put(panelConfig("Цепочка", listOf(chained))).toString(), "s").skipped)
    }

    private val opts = RoutingOptions(rules = listOf(Rule(0, "example.org", 0), Rule(0, "direct.example", 1), Rule(1, "198.51.100.0/24", 2), Rule(2, "8080", 0)),
        dnsServers = listOf("1.1.1.1"), exceptRu = true)

    private fun build(plan: AutoPlan, o: RoutingOptions = opts) = JSONObject(ConfigBuilder.buildAuto(plan, o, "/data/probe/main.sock"))

    @Test fun inboundsAreOursAndPanelPortsAreGone() {
        val c = build(plans().configs[0].plan)
        val ins = c.getJSONArray("inbounds")
        assertEquals(listOf("tun-in", "probe-in"), (0 until ins.length()).map { ins.getJSONObject(it).getString("tag") })
        assertEquals("/data/probe/main.sock", ins.getJSONObject(1).getString("listen"))
        val text = c.toString()
        assertFalse(text.contains("10808")); assertFalse(text.contains("10809")); assertFalse(text.contains("\"socks\""))
        for (i in 0 until ins.length()) { assertFalse("ни одного порта на localhost", ins.getJSONObject(i).has("port")); assertFalse(ins.getJSONObject(i).has("listen") && ins.getJSONObject(i).getString("listen").startsWith("127.")) }
    }

    @Test fun outboundsBalancersObservatoryAndDns() {
        val plan = plans().configs[0].plan
        val c = build(plan)
        val tags = c.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("tag") } }
        assertEquals(plan.outbounds.map { it.getString("tag") } + listOf("direct", "block"), tags)
        assertEquals(tags.size, tags.toSet().size)
        assertEquals("Super_Balancer", c.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).getString("tag"))
        assertEquals("proxy", c.getJSONObject("burstObservatory").getJSONArray("subjectSelector").getString(0))
        assertEquals(listOf("1.1.1.1"), c.getJSONObject("dns").getJSONArray("servers").let { a -> (0 until a.length()).map { a.getString(it) } })   // DNS свой, не 9.9.9.9 панели
        assertEquals("dns-internal", c.getJSONObject("dns").getString("tag"))
        assertFalse(c.toString().contains("example.ru"))   // правила панели не переносятся
    }

    @Test fun routesGoThroughBalancerNotThroughFirstOutbound() {
        val rules = build(plans().configs[0].plan).getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals("Super_Balancer", rules[0].getString("balancerTag")); assertEquals("probe-in", rules[0].getJSONArray("inboundTag").getString(0))
        assertEquals("Super_Balancer", rules[1].getString("balancerTag")); assertEquals("dns-internal", rules[1].getJSONArray("inboundTag").getString(0))
        assertTrue("нигде не остаётся outboundTag=proxy", rules.none { it.optString("outboundTag") == "proxy" })
        val user = rules.first { it.optJSONArray("domain")?.optString(0) == "domain:example.org" }; assertEquals("Super_Balancer", user.getString("balancerTag"))   // «через VPN»
        assertEquals("direct", rules.first { it.optJSONArray("domain")?.optString(0) == "domain:direct.example" }.getString("outboundTag"))
        assertEquals("block", rules.first { it.optJSONArray("ip")?.optString(0) == "198.51.100.0/24" }.getString("outboundTag"))
        assertEquals("Super_Balancer", rules.first { it.optString("port") == "8080" }.getString("balancerTag"))
        val last = rules.last()   // без него непопавший трафик ушёл бы в первый выход списка
        assertEquals("tcp,udp", last.getString("network")); assertEquals("Super_Balancer", last.getString("balancerTag")); assertFalse(last.has("outboundTag"))
    }

    @Test fun secondBalancerNameIsUsedForBridgeConfigs() {
        val c = build(plans().configs[1].plan)
        assertEquals("de_bridge_balancer", c.getJSONObject("routing").getJSONArray("rules").let { it.getJSONObject(it.length() - 1) }.getString("balancerTag"))
    }

    @Test fun dohAndGameOptionsApplyToEveryServer() {
        val c = build(plans().configs[0].plan, RoutingOptions(dnsServers = listOf("9.9.9.9"), doh = true, game = true, transport = 1))
        val outs = c.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertTrue(outs.any { it.getString("tag") == "dns-out" })
        outs.filter { it.getString("tag").startsWith("proxy") }.forEach { assertTrue(it.getString("tag"), it.getJSONObject("streamSettings").getJSONObject("sockopt").getBoolean("tcpNoDelay")) }
        assertTrue(c.getJSONObject("dns").getJSONArray("servers").getString(0).startsWith("https://9.9.9.9"))
    }

    @Test fun singleServerConfigIsUnchangedByTheRefactoring() {
        val p = Links.parse("vless://$uuid@203.0.113.3:443?type=tcp&security=reality&pbk=K&sid=ab&sni=front.example.org#t")
        val c = JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.3"), opts, "/x/main.sock"))
        assertFalse(c.has("burstObservatory")); assertFalse(c.getJSONObject("routing").has("balancers"))
        val rules = c.getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertEquals("proxy", rules[0].getString("outboundTag")); assertTrue(rules.none { it.has("balancerTag") })
        assertEquals("proxy", c.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
    }

    // ---------- адреса ----------

    @Test fun domainsArePinnedWithoutLosingSniAndHost() {
        val plan = plans().configs[0].plan
        assertEquals(setOf("node2.example.org", "node3.example.org", "node4.example.org"), AutoSub.domains(plan))
        val pinned = AutoSub.pinAddresses(plan) { "203.0.113.${it.substringAfter("node").substringBefore('.')}0" }!!
        val by = pinned.outbounds.associateBy { it.getString("tag") }
        fun addr(o: JSONObject) = o.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getString("address")
        assertEquals("203.0.113.20", addr(by.getValue("proxy-2")))
        assertEquals("node2.example.org", by.getValue("proxy-2").getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"))   // SNI остался доменом
        assertEquals("node2.example.org", by.getValue("proxy-2").getJSONObject("streamSettings").getJSONObject("xhttpSettings").getString("host"))
        assertEquals("node3.example.org", by.getValue("proxy-3").getJSONObject("streamSettings").getJSONObject("wsSettings").getJSONObject("headers").getString("Host"))
        // что уже задано в панели, не трогаем
        assertEquals("sni.example.org", by.getValue("proxy-4").getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"))
        assertEquals("cdn.example.org", by.getValue("proxy-4").getJSONObject("streamSettings").getJSONObject("xhttpSettings").getString("host"))
        assertEquals("203.0.113.1", addr(by.getValue("proxy")))   // IP не меняется
        assertEquals("203.0.113.5", by.getValue("proxy-5").getJSONObject("settings").getString("address"))
        assertTrue(AutoSub.domains(pinned).isEmpty())
        assertTrue("исходный план не изменён", AutoSub.domains(plan).isNotEmpty())
    }

    @Test fun unresolvedServerIsDroppedAndFallbackStaysValid() {
        val plan = plans().configs[0].plan
        val pinned = AutoSub.pinAddresses(plan) { if (it.startsWith("node2")) null else "203.0.113.77" }!!
        assertEquals(listOf("proxy", "proxy-3", "proxy-4", "proxy-5"), pinned.outbounds.map { it.getString("tag") })
        // выбывает именно запасной выход (первый, с доменом): балансировщик должен получить другой существующий
        val domainFirst = AutoSub.parse(JSONArray().put(panelConfig("D", listOf(vless("proxy", "node.example.org"), vless("proxy-2", "203.0.113.2")))).toString(), "s").configs[0].plan
        assertEquals("proxy", domainFirst.balancers[0].getString("fallbackTag"))
        val moved = AutoSub.pinAddresses(domainFirst) { null }!!
        assertEquals(listOf("proxy-2"), moved.outbounds.map { it.getString("tag") })
        assertEquals("proxy-2", moved.balancers[0].getString("fallbackTag"))
        val onlyDomains = AutoSub.parse(JSONArray().put(panelConfig("D", listOf(vless("proxy", "node.example.org")))).toString(), "s").configs[0].plan
        assertNull(AutoSub.pinAddresses(onlyDomains) { null })
    }

    @Test fun resolveAllRespectsTimeout() {
        val t0 = System.currentTimeMillis()
        val r = AutoSub.resolveAll(setOf("a", "slow", "bad"), 500) { n -> when (n) { "a" -> "203.0.113.1"; "slow" -> { Thread.sleep(5000); "203.0.113.2" }; else -> null } }
        assertEquals(mapOf("a" to "203.0.113.1"), r)
        assertTrue(System.currentTimeMillis() - t0 < 2500)
    }

    @Test fun jsonUrlKeepsPathAndQueryAndDropsFragment() {
        assertEquals("https://sub.example.org/sub/TOKEN/json", AutoSub.jsonUrl("https://sub.example.org/sub/TOKEN#NAME"))
        assertEquals("https://sub.example.org/sub/TOKEN/json", AutoSub.jsonUrl("https://sub.example.org/sub/TOKEN/"))
        assertEquals("https://sub.example.org:8443/a/json?x=1", AutoSub.jsonUrl("https://sub.example.org:8443/a?x=1#f"))
        assertNull(AutoSub.jsonUrl("http://sub.example.org/a")); assertNull(AutoSub.jsonUrl("не адрес"))
    }

    @Test fun planSurvivesStorageRoundTrip() {
        val plan = plans().configs[0].plan
        val back = AutoSub.fromJson(JSONObject(AutoSub.toJson(plan).toString()))
        assertEquals(plan.name, back.name); assertEquals(plan.outbounds.map { it.toString() }, back.outbounds.map { it.toString() })
        assertEquals(plan.balancers.map { it.toString() }, back.balancers.map { it.toString() }); assertEquals(plan.burst.toString(), back.burst.toString())
    }

    @Test fun balancerStrategyCanBeOverriddenForTheMainBalancerOnly() {
        val plan = plans().configs[0].plan
        fun strategy(choice: Int) = build(plan, RoutingOptions(balancerStrategy = choice)).getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).optJSONObject("strategy")?.optString("type")
        val panel = strategy(0)
        assertEquals("random", strategy(1)); assertEquals("roundRobin", strategy(2))
        assertNotNull("в примере есть burstObservatory, значит быстрый выбор разрешён", plan.burst); assertEquals("leastPing", strategy(3))
        assertEquals("как в панели: ничего не добавляется", panel, strategy(0)); assertEquals(panel, strategy(99))
        val tags = build(plan, RoutingOptions(balancerStrategy = 1)).getJSONObject("routing").getJSONArray("balancers").getJSONObject(0)
        assertEquals("остальные поля балансировщика сохраняются", plan.balancers[0].getString("tag"), tags.getString("tag")); assertTrue(tags.has("selector"))
    }

    @Test fun leastPingNeedsAnObservatory() {
        val b = JSONObject().put("tag", "B").put("selector", JSONArray().put("proxy")).put("strategy", JSONObject().put("type", "leastLoad"))
        assertEquals("leastLoad", BalancerStrategy.apply(b, 3, hasObservatory = false).getJSONObject("strategy").getString("type"))
        assertEquals("leastPing", BalancerStrategy.apply(b, 3, hasObservatory = true).getJSONObject("strategy").getString("type"))
        assertEquals("random", BalancerStrategy.apply(b, 1, hasObservatory = false).getJSONObject("strategy").getString("type"))
        assertEquals("leastLoad", b.getJSONObject("strategy").getString("type"))   // исходный объект не меняется
    }
}
