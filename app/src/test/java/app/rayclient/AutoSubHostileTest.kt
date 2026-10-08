package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** JSON-подписка от враждебной или взломанной панели: подмена регистром ключей, служебные теги, лишние поля, глубокий JSON. */
class AutoSubHostileTest {
    private fun vless(tag: String, addr: String = "203.0.113.5") = JSONObject().put("tag", tag).put("protocol", "vless")
        .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().put("address", addr).put("port", 443)
            .put("users", JSONArray().put(JSONObject().put("id", "00000000-0000-0000-0000-000000000001").put("encryption", "none"))))))

    private fun bal(selector: List<String> = listOf("proxy")) = JSONObject().put("tag", "bal").put("selector", JSONArray(selector)).put("fallbackTag", "direct")

    private fun body(outs: List<JSONObject>, b: JSONObject = bal(), burst: JSONObject? = null) = JSONArray().put(JSONObject().put("remarks", "Авто")
        .put("outbounds", JSONArray(outs))
        .put("routing", JSONObject().put("balancers", JSONArray().put(b)))
        .apply { burst?.let { put("burstObservatory", it) } }).toString()

    private fun configs(b: String) = AutoSub.parse(b, "sub")

    @Test fun duplicateKeyInOtherCaseIsRejected() {
        val o = vless("proxy-a").put("Protocol", "freedom")
        assertEquals(0, configs(body(listOf(vless("proxy-b"), o))).configs.size)
        val b = bal().put("FallbackTag", "direct")
        assertEquals(0, configs(body(listOf(vless("proxy-a")), b)).configs.size)
    }

    @Test fun guardedKeyInOtherCaseAloneIsRejected() {
        val o = JSONObject().put("tag", "proxy-a").put("Protocol", "freedom")
        assertEquals(0, configs(body(listOf(vless("proxy-b"), o))).configs.size)
        val s = vless("proxy-c"); s.put("Settings", s.remove("settings"))
        assertEquals(0, configs(body(listOf(s))).configs.size)
    }

    @Test fun fallbackIsAlwaysOurServer() {
        val p = configs(body(listOf(vless("proxy-a"), vless("proxy-b")))).configs[0].plan
        assertEquals("proxy-a", p.balancers[0].getString("fallbackTag"))
    }

    @Test fun balancerKeepsOnlyKnownFields() {
        val b = bal().put("strategy", JSONObject().put("type", "leastLoad")).put("extra", "x")
        val r = configs(body(listOf(vless("proxy-a")), b)).configs[0].plan.balancers[0]
        assertFalse(r.has("extra")); assertEquals("leastLoad", r.getJSONObject("strategy").getString("type"))
        val b2 = bal().put("strategy", JSONObject().put("type", "evil"))
        assertFalse(configs(body(listOf(vless("proxy-a")), b2)).configs[0].plan.balancers[0].has("strategy"))
    }

    @Test fun selectorThatMatchesServiceTagIsRejected() {
        for (sel in listOf("", "d", "b", "di", "fr", "dns")) assertEquals(sel, 0, configs(body(listOf(vless("proxy-a")), bal(listOf(sel)))).configs.size)
        assertEquals(1, configs(body(listOf(vless("proxy-a")), bal(listOf("proxy")))).configs.size)
    }

    @Test fun serverWithServiceTagIsRejected() {
        for (t in listOf("direct", "block", "dns-out", "fragment")) assertEquals(t, 0, configs(body(listOf(vless(t)), bal(listOf(t)))).configs.size)
    }

    @Test fun dangerousOutboundFieldsAreRemoved() {
        val o = vless("proxy-a").put("sendThrough", "0.0.0.0").put("weird", 1)
            .put("streamSettings", JSONObject().put("network", "tcp").put("security", "tls")
                .put("sockopt", JSONObject().put("interface", "wlan0").put("mark", 255).put("tcpFastOpen", true))
                .put("tlsSettings", JSONObject().put("serverName", "a.example").put("masterKeyLog", "/x").put("certificates", JSONArray().put(JSONObject().put("certificateFile", "/y")))))
        val r = configs(body(listOf(o))).configs[0].plan.outbounds[0]
        assertFalse(r.has("sendThrough")); assertFalse(r.has("weird"))
        val ss = r.getJSONObject("streamSettings")
        assertFalse(ss.getJSONObject("sockopt").has("interface")); assertFalse(ss.getJSONObject("sockopt").has("mark"))
        assertTrue(ss.getJSONObject("sockopt").getBoolean("tcpFastOpen"))
        assertFalse(ss.getJSONObject("tlsSettings").has("masterKeyLog")); assertFalse(ss.getJSONObject("tlsSettings").has("certificates"))
        assertEquals("a.example", ss.getJSONObject("tlsSettings").getString("serverName"))
    }

    @Test fun burstIsCleaned() {
        fun burst(ping: JSONObject) = JSONObject().put("subjectSelector", JSONArray(listOf("proxy"))).put("pingConfig", ping)
        val ok = configs(body(listOf(vless("proxy-a")), burst = burst(JSONObject().put("destination", "http://detectportal.firefox.com/success.txt")
            .put("interval", "1s").put("connectivity", "http://evil.example/").put("sampling", 3).put("timeout", "5s")))).configs[0].plan.burst!!
        val ping = ok.getJSONObject("pingConfig")
        assertEquals("слишком частый замер поднимается до минуты", "1m", ping.getString("interval"))
        assertFalse(ping.has("connectivity")); assertEquals(3, ping.getInt("sampling"))
        assertNull(configs(body(listOf(vless("proxy-a")), burst = burst(JSONObject().put("destination", "ftp://x/").put("interval", "1m")))).configs[0].plan.burst)
        assertEquals("2m", configs(body(listOf(vless("proxy-a")), burst = burst(JSONObject().put("destination", "https://a.example/g").put("interval", "2m")))).configs[0].plan.burst!!
            .getJSONObject("pingConfig").getString("interval"))
    }

    @Test fun deepJsonIsRejectedBeforeParsing() {
        val deep = "[".repeat(100000) + "]".repeat(100000)
        assertTrue(AutoSub.maxDepth(deep) > AutoSub.MAX_DEPTH)
        assertThrows(IllegalArgumentException::class.java) { AutoSub.parse(deep, "sub") }
        assertEquals("скобки внутри строк не считаются", 2, AutoSub.maxDepth("""[{"a":"[[[[{{{"}]"""))
    }
}
