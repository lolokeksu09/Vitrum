package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** JSON-подписка: служебные выходы и выходы на сам телефон не становятся «серверами» балансировщика. */
class AutoSubLocalTest {
    private fun vless(tag: String, addr: String) = JSONObject().put("tag", tag).put("protocol", "vless")
        .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().put("address", addr).put("port", 443)
            .put("users", JSONArray().put(JSONObject().put("id", "00000000-0000-0000-0000-000000000001").put("encryption", "none"))))))

    private fun body(vararg outs: JSONObject) = JSONArray().put(JSONObject().put("remarks", "Авто")
        .put("outbounds", JSONArray().apply { outs.forEach { put(it) } })
        .put("routing", JSONObject().put("balancers", JSONArray().put(JSONObject().put("tag", "bal")
            .put("selector", JSONArray().put("proxy")).put("fallbackTag", "direct")))))
        .toString()

    @Test fun serviceAndLocalOutboundsAreDropped() {
        val p = AutoSub.parse(body(
            vless("proxy-a", "203.0.113.5"),
            JSONObject().put("tag", "proxy-b").put("protocol", "Freedom"),                       // другой регистр
            JSONObject().put("tag", "proxy-c").put("protocol", "socks")
                .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", "127.0.0.1").put("port", 1080)))),
            JSONObject().put("tag", "proxy-d").put("protocol", "loopback").put("settings", JSONObject().put("inboundTag", "x")),
            vless("proxy-e", "localhost"),
            JSONObject().put("tag", "direct").put("protocol", "freedom")), "sub")
        assertEquals(1, p.configs.size)
        val plan = p.configs[0].plan
        assertEquals(listOf("proxy-a"), plan.outbounds.map { it.getString("tag") })
        assertEquals("запасной выход — сервер, а не direct", "proxy-a", plan.balancers[0].getString("fallbackTag"))
    }

    @Test fun domainResolvingToDeviceIsDropped() {
        val plan = AutoSub.parse(body(vless("proxy-a", "evil.example"), vless("proxy-b", "good.example")), "sub").configs[0].plan
        val pinned = AutoSub.pinAddresses(plan) { if (it == "evil.example") "127.0.0.1" else "203.0.113.7" }!!
        assertEquals(listOf("proxy-b"), pinned.outbounds.map { it.getString("tag") })
        assertNull("все серверы ведут на телефон: подключать нечего", AutoSub.pinAddresses(plan) { "::1" })
    }

    @Test fun localAddressDetection() {
        listOf("127.0.0.1", "127.1.2.3", "localhost", "a.localhost", "::1", "[::1]", "0.0.0.0", "::", "::ffff:127.0.0.1").forEach { assertTrue(it, AutoSub.isLocalAddr(it)) }
        listOf("203.0.113.5", "10.0.0.1", "example.com", "2001:db8::1").forEach { assertFalse(it, AutoSub.isLocalAddr(it)) }
    }
}
