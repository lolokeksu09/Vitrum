package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FragmentTest {
    private val reality = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#Reality"
    private val wsTls = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=ws&security=tls&path=%2Fws&host=example.com&sni=example.com#WS"
    private val hy2 = "hy2://authpass@example.com:443?sni=example.com&insecure=1#HY"
    private val ss = "ss://" + java.util.Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:pass123".toByteArray()) + "@example.com:8388#SS"

    private fun cfg(link: String, fragment: Boolean): JSONObject {
        val p = Links.parse(link)
        return JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(fragment = fragment)))
    }
    private fun outs(c: JSONObject) = c.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun proxy(c: JSONObject) = outs(c).first { it.getString("tag") == "proxy" }
    private fun fragOut(c: JSONObject) = outs(c).firstOrNull { it.getString("tag") == "fragment" }

    @Test fun tlsAndRealityGoThroughFragmentOutbound() {
        for (l in listOf(reality, wsTls)) {
            val c = cfg(l, true)
            assertEquals("fragment", proxy(c).getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
            val f = fragOut(c)!!
            assertEquals("freedom", f.getString("protocol"))
            val fr = f.getJSONObject("settings").getJSONObject("fragment")
            assertEquals("tlshello", fr.getString("packets")); assertEquals("100-200", fr.getString("length")); assertEquals("10-20", fr.getString("interval"))
        }
    }

    @Test fun offByDefaultAndNotForUdpOrPlainServers() {
        assertNull("выключено: выхода fragment нет", fragOut(cfg(reality, false)))
        assertFalse(proxy(cfg(reality, false)).getJSONObject("streamSettings").optJSONObject("sockopt")?.has("dialerProxy") ?: false)
        for (l in listOf(hy2, ss)) {
            val c = cfg(l, true)
            assertNull("hy2 (QUIC) и shadowsocks без TLS не фрагментируем", fragOut(c))
            assertFalse(proxy(c).optJSONObject("streamSettings")?.optJSONObject("sockopt")?.has("dialerProxy") ?: false)
        }
    }

    @Test fun existingDialerProxyChainIsKept() {
        val p = Links.parse(reality)
        val ob = Links.withAddress(p, "203.0.113.5")
        ob.getJSONObject("streamSettings").put("sockopt", JSONObject().put("dialerProxy", "bridge"))
        val c = JSONObject(ConfigBuilder.build(p, ob, RoutingOptions(fragment = true)))
        assertEquals("bridge", proxy(c).getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        assertNull(fragOut(c))
    }
}
