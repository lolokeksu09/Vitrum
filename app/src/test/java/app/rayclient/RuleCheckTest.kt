package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Правки по аудиту: проверка записей правил, DNS при блокировке остального, base64 с «+», хвост журнала. */
class RuleCheckTest {
    @Test fun ipEntries() {
        assertTrue(RuleCheck.invalid(1, "1.2.3.4, 10.0.0.0/8, 2001:db8::/32 ::1 geoip:ru ext:file.dat:tag").isEmpty())
        assertEquals(listOf("1.2.3.4/99", "999.1.1.1", "google.com", "1.2.3", ":::/64", "abc:def"), RuleCheck.invalid(1, "1.2.3.4/99, 999.1.1.1, google.com, 1.2.3, :::/64, abc:def"))
    }

    @Test fun portEntries() {
        assertTrue(RuleCheck.invalid(2, "80, 443, 1000-2000, 65535").isEmpty())
        assertEquals(listOf("abc", "0", "70000", "20-10", "1-2-3", "-5"), RuleCheck.invalid(2, "abc, 0, 70000, 20-10, 1-2-3, -5"))
    }

    @Test fun domainEntries() {
        assertTrue(RuleCheck.invalid(0, "example.com domain:example.org full:a.b.c keyword:video geosite:netflix regexp:^a.*b$ пример.рф").isEmpty())
        assertEquals(listOf("domain!", "a\"b", "domain:", "geosite:"), RuleCheck.invalid(0, "ok.com,domain!,a\"b,domain:,geosite:"))
    }

    @Test fun blockedRestStillResolvesNames() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
        val p = Links.parse(link)
        val c = JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(defaultAction = 2)))
        val rs = c.getJSONObject("routing").getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val dns = rs.indexOfFirst { it.optString("port") == "53" && it.optString("outboundTag") == "proxy" }
        assertTrue("DNS идёт через VPN", dns >= 0)
        assertEquals("block", rs.last().getString("outboundTag")); assertTrue(dns < rs.size - 1)
    }

    @Test fun happRoutingKeepsPlusInBase64() {
        val enc = java.util.Base64.getEncoder()
        val json = (0..200).map { """{"Name":"n${">".repeat(it)}","DirectSites":["a.example"]}""" }.first { '+' in enc.encodeToString(it.toByteArray()) }
        val b64 = enc.encodeToString(json.toByteArray())
        assertNotNull("стандартный base64 с «+»", HappRouting.parse(b64))
        assertNotNull(HappRouting.parse("happ://routing/add/$b64"))
        assertNotNull("с %-кодированием", HappRouting.parse(java.net.URLEncoder.encode(b64, "UTF-8")))
    }

    @Test fun logTailReadsOnlyTheEnd() {
        val f = File.createTempFile("xray", ".log")
        try {
            f.writeText("начало\n" + "x".repeat(50000) + "\nконец журнала")
            val t = LogTail.read(f, 100)
            assertEquals(100, t.length); assertTrue(t.endsWith("конец журнала")); assertFalse("начало" in t)
            assertEquals("", LogTail.read(File("/nonexistent/none.log")))
        } finally { f.delete() }
    }

    @Test fun broadDirectEntriesFromSubscriptionAreDropped() {
        listOf(1 to "0.0.0.0/0", 1 to "1.0.0.0/4", 1 to "::/0", 1 to "2000::/8", 0 to "regexp:.*", 0 to "keyword:a", 0 to "domain:com", 0 to "ru").forEach { assertTrue("${it.second}", RuleCheck.tooBroad(it.first, it.second)) }
        listOf(1 to "10.0.0.0/8", 1 to "203.0.113.5", 1 to "geoip:ru", 0 to "geosite:category-ru", 0 to "domain:example.ru", 0 to "keyword:video", 0 to "example.org").forEach { assertFalse("${it.second}", RuleCheck.tooBroad(it.first, it.second)) }
        val json = """{"Name":"x","DirectIp":["0.0.0.0/0","10.0.0.0/8"],"DirectSites":["domain:com","example.ru"],"BlockIp":["1.0.0.0/4"],"ProxySites":["domain:ru"]}"""
        val r = HappRouting.parse(json)!!
        assertEquals(listOf("0.0.0.0/0", "domain:com"), r.dropped)
        assertEquals("10.0.0.0/8", r.rules.first { it.kind == 1 && it.action == 1 }.value); assertEquals("example.ru", r.rules.first { it.kind == 0 && it.action == 1 }.value)
        assertTrue("block и «через VPN» не режутся", r.rules.any { it.action == 2 && it.value == "1.0.0.0/4" } && r.rules.any { it.action == 0 && it.value == "domain:ru" })
    }
}
