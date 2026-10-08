package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoutingRulesTest {
    private val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
    private fun cfg(r: RoutingOptions): JSONObject { val p = Links.parse(link); return JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), r)) }
    private fun rules(c: JSONObject) = c.getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun entry(tag: String): ByteArray {
        val body = byteArrayOf(0x0a, tag.length.toByte()) + tag.toByteArray() + byteArrayOf(0x12, 0x02, 0x7f, 0x7f.toByte())
        return byteArrayOf(0x0a, body.size.toByte()) + body
    }
    private fun dat(vararg tags: String) = tags.fold(ByteArray(0)) { a, t -> a + entry(t) }
    private val known = GeoTags.Known(setOf("ru", "private"), setOf("netflix", "category-ads-all", "steam"))

    @Test fun lastRuleFollowsTheDefaultAction() {
        fun catchAll(r: JSONObject) = r.optString("network") == "tcp,udp" && !r.has("ip") && !r.has("domain") && !r.has("port") && !r.has("inboundTag") && r.optString("outboundTag") in listOf("direct", "block")
        assertFalse("по умолчанию общего правила нет", rules(cfg(RoutingOptions())).any { catchAll(it) })
        for ((a, tag) in listOf(1 to "direct", 2 to "block")) {
            val last = rules(cfg(RoutingOptions(defaultAction = a))).last()
            assertEquals(tag, last.getString("outboundTag")); assertEquals("tcp,udp", last.getString("network")); assertFalse(last.has("domain") || last.has("ip") || last.has("port"))
        }
    }

    @Test fun defaultActionComesAfterEveryUserRule() {
        val rs = rules(cfg(RoutingOptions(rules = listOf(Rule(0, "netflix.com", 0)), defaultAction = 1, exceptRu = true)))
        val user = rs.indexOfFirst { it.optJSONArray("domain")?.toString()?.contains("netflix.com") == true }
        assertTrue(user in 0 until rs.size - 1)
        assertEquals("direct", rs.last().getString("outboundTag"))
    }

    @Test fun userRulesReachTheConfigInListOrder() {
        val order = listOf("first.example", "second.example", "third.example")
        val rs = rules(cfg(RoutingOptions(rules = listOf(Rule(0, order[0], 1), Rule(0, order[1], 0), Rule(0, order[2], 2)))))
        val at = order.map { d -> rs.indexOfFirst { it.optJSONArray("domain")?.toString()?.contains(d) == true } }
        assertTrue(at.toString(), at.all { it >= 0 } && at == at.sorted())
        val swapped = rules(cfg(RoutingOptions(rules = RuleOrder.move(listOf(Rule(0, order[0], 1), Rule(0, order[1], 0)), 1, -1)!!)))
        assertTrue(swapped.indexOfFirst { it.toString().contains(order[1]) } < swapped.indexOfFirst { it.toString().contains(order[0]) })
    }

    @Test fun disabledRulesAreSkippedAndOldRulesStayEnabled() {
        assertTrue(Rule(0, "a.example", 1).enabled)
        val off = rules(cfg(RoutingOptions(rules = listOf(Rule(0, "off.example", 1, enabled = false), Rule(0, "on.example", 1)))))
        assertTrue(off.any { it.optJSONArray("domain")?.toString()?.contains("on.example") == true })
        assertFalse(off.any { it.toString().contains("off.example") })
    }

    @Test fun datTagsAreReadFromTheFraming() {
        assertEquals(setOf("netflix", "category-ads-all"), GeoTags.parse(dat("NETFLIX", "Category-Ads-All")))
        assertTrue(GeoTags.parse(ByteArray(0)).isEmpty())
        assertEquals(setOf("ru"), GeoTags.parse(dat("RU") + byteArrayOf(0x0a, 0x7f)))   // обрезанная запись не мешает
    }

    @Test fun unknownGeoTagsAreFoundAndDropped() {
        val s = GeoTags.sanitize(Rule(0, "example.org, geosite:netflix, geosite:nope, geosite:steam@cn", 0), known)
        assertEquals(listOf("geosite:nope"), s.dropped); assertEquals("example.org, geosite:netflix, geosite:steam@cn", s.rule!!.value)
        val ip = GeoTags.sanitize(Rule(1, "geoip:ru 10.0.0.0/8 geoip:!private geoip:xx", 1, enabled = false), known)
        assertEquals(listOf("geoip:xx"), ip.dropped); assertFalse(ip.rule!!.enabled); assertEquals("geoip:ru, 10.0.0.0/8, geoip:!private", ip.rule!!.value)
        val none = GeoTags.sanitize(Rule(0, "geosite:nope", 2), known)
        assertNull(none.rule); assertEquals(listOf("geosite:nope"), none.dropped)
    }

    @Test fun nothingIsDroppedWithoutBasesAndPortsAreNeverChecked() {
        assertTrue(GeoTags.sanitize(Rule(0, "geosite:nope", 0), null).dropped.isEmpty())
        assertTrue(GeoTags.sanitize(Rule(2, "geosite:nope", 0), known).dropped.isEmpty())
        assertTrue(GeoTags.known("ext:file.dat:tag", known)); assertTrue(GeoTags.known("domain:example.org", known))
    }

    @Test fun basesAreLoadedFromFilesAndCachedByDate() {
        val d = File(System.getProperty("java.io.tmpdir"), "geotags-" + System.nanoTime()).apply { mkdirs() }
        try {
            assertNull(GeoTags.load(d.absolutePath))
            File(d, "geoip.dat").writeBytes(dat("RU", "PRIVATE")); File(d, "geosite.dat").writeBytes(dat("NETFLIX"))
            val k = GeoTags.load(d.absolutePath)!!
            assertEquals(setOf("ru", "private"), k.geoip); assertEquals(setOf("netflix"), k.geosite)
            File(d, "geosite.dat").writeBytes(dat("NETFLIX", "STEAM"))
            assertEquals(setOf("netflix", "steam"), GeoTags.load(d.absolutePath)!!.geosite)
            File(d, "geoip.dat").writeBytes(ByteArray(0))
            assertNull("пустая база не считается прочитанной", GeoTags.load(d.absolutePath))
        } finally { d.deleteRecursively() }
    }
}
