package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FragmentParamsTest {
    private val reality = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#R"
    private val hy2 = "hy2://authpass@example.com:443?sni=example.com&insecure=1#HY"

    private fun cfg(link: String, r: RoutingOptions): JSONObject {
        val p = Links.parse(link)
        return JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), r))
    }
    private fun outs(c: JSONObject) = c.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test fun validRanges() {
        for (ok in listOf("100-200", "10", "1-1", " 50-100 ")) assertTrue(ok, FragmentParams.valid(ok))
        for (bad in listOf("", "0", "200-100", "abc", "1-", "-5", "10000", "1-2-3", "1,2")) assertFalse(bad, FragmentParams.valid(bad))
        assertEquals("100-200", FragmentParams.orDefault("junk", "100-200"))
        assertEquals("30-60", FragmentParams.orDefault(" 30-60 ", "100-200"))
    }

    @Test fun customFragmentValuesReachConfigAndBadOnesFallBack() {
        fun frag(r: RoutingOptions) = outs(cfg(reality, r)).first { it.getString("tag") == "fragment" }.getJSONObject("settings").getJSONObject("fragment")
        val f = frag(RoutingOptions(fragment = true, fragLength = "30-60", fragInterval = "5"))
        assertEquals("30-60", f.getString("length")); assertEquals("5", f.getString("interval"))
        val d = frag(RoutingOptions(fragment = true, fragLength = "x", fragInterval = "9-1"))
        assertEquals("100-200", d.getString("length")); assertEquals("10-20", d.getString("interval"))
    }

    @Test fun fingerprintOverridesLinkOnlyWhenSetAndKnown() {
        fun fp(r: RoutingOptions) = outs(cfg(reality, r)).first { it.getString("tag") == "proxy" }.getJSONObject("streamSettings").getJSONObject("realitySettings").getString("fingerprint")
        assertEquals("chrome", fp(RoutingOptions()))
        assertEquals("firefox", fp(RoutingOptions(fingerprint = "firefox")))
        assertEquals("неизвестное значение игнорируется", "chrome", fp(RoutingOptions(fingerprint = "netscape")))
    }

    @Test fun fingerprintDoesNotTouchQuicOutbounds() {
        val s = cfg(hy2, RoutingOptions(fingerprint = "safari")).toString()
        assertFalse(s.contains("\"safari\""))
    }
}

class TunParamsTest {
    @Test fun mtuBounds() {
        for (ok in listOf(1280, 1400, 1500)) assertTrue(TunParams.valid(ok))
        for (bad in listOf(0, 1279, 1501, -5, 9999)) { assertFalse(TunParams.valid(bad)); assertEquals(1500, TunParams.orDefault(bad)) }
        assertEquals(1400, TunParams.orDefault(1400))
    }

    @Test fun mtuReachesTunInboundAndBadValueFallsBack() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
        fun mtu(v: Int): Int { val p = Links.parse(link)
            val c = org.json.JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(mtu = v)))
            return c.getJSONArray("inbounds").getJSONObject(0).getJSONObject("settings").getInt("MTU") }
        assertEquals(1400, mtu(1400)); assertEquals(1500, mtu(100)); assertEquals(1500, RoutingOptions().mtu)
    }
}
