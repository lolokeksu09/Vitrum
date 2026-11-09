package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class SubMetaTest {
    private val cur = "https://vip.dualizm.space/sub/TOKEN123"
    private fun meta(h: Map<String, String>, url: String = cur) = SubMetaParser.parse({ h[it] ?: "" }, url)

    @Test fun realPanelHeaders() {
        // заголовки настоящей панели (значения из живого ответа): новых адресов нет
        val m = meta(mapOf("profile-update-interval" to "1", "subscription-refill-date" to "1793923800", "profile-web-page-url" to "https://vip.dualizm.space/abc", "support-url" to "https://t.me/x"))
        assertEquals(1, m.intervalH); assertEquals(1793923800L, m.refill); assertEquals("https://vip.dualizm.space/abc", m.page)
        assertNull(m.newUrl); assertNull(m.fallback)
    }

    @Test fun intervalAndRefillAreRangeChecked() {
        assertEquals(0, meta(mapOf("profile-update-interval" to "0")).intervalH); assertEquals(0, meta(mapOf("profile-update-interval" to "9999")).intervalH)
        assertEquals(0, meta(mapOf("profile-update-interval" to "abc")).intervalH); assertEquals(168, meta(mapOf("profile-update-interval" to "168")).intervalH)
        assertEquals(0L, meta(mapOf("subscription-refill-date" to "12")).refill); assertEquals(0L, meta(mapOf("subscription-refill-date" to "99999999999999")).refill)
    }

    @Test fun newUrlAndNewDomain() {
        assertEquals("https://new.example.org/sub/AAA", meta(mapOf("new-url" to "https://new.example.org/sub/AAA")).newUrl)
        // new-domain подставляет домен в прежний путь и параметры
        assertEquals("https://new.example.org/sub/TOKEN123", meta(mapOf("new-domain" to "new.example.org")).newUrl)
        assertEquals("https://b.example.org/sub/T?x=1", meta(mapOf("new-domain" to "b.example.org"), "https://a.example.org/sub/T?x=1").newUrl)
        // new-url важнее new-domain
        assertEquals("https://u.example.org/z", meta(mapOf("new-url" to "https://u.example.org/z", "new-domain" to "d.example.org")).newUrl)
        // тот же адрес — это не переезд
        assertNull(meta(mapOf("new-url" to cur)).newUrl); assertNull(meta(mapOf("new-url" to "$cur/")).newUrl); assertNull(meta(mapOf("new-domain" to "vip.dualizm.space")).newUrl)
    }

    @Test fun unsafeAddressesAreRejected() {
        for (bad in listOf("http://evil.example/sub", "ftp://evil.example/x", "javascript:alert(1)", "file:///etc/passwd", "https://user:pass@evil.example/x", "https:///nohost", "https://exa mple.com/x", "https://evil.example\\@good.example/", "", "not a url", "intent://x#Intent;end"))
            assertNull("«$bad» не должен приниматься", meta(mapOf("new-url" to bad, "fallback-url" to bad, "profile-web-page-url" to bad)).let { it.newUrl ?: it.fallback ?: it.page.ifEmpty { null } })
        for (bad in listOf("evil", "ev il.com", "-x.com", "x..com", "a.com/path", "a.com:8080@b.com")) assertNull("домен «$bad»", meta(mapOf("new-domain" to bad)).newUrl)
    }

    @Test fun fallbackAddress() {
        assertEquals("https://backup.example.net/sub/Q", meta(mapOf("fallback-url" to "https://backup.example.net/sub/Q")).fallback)
        assertNull(meta(mapOf("fallback-url" to cur)).fallback)
    }

    @Test fun serverDescriptionFromLinks() {
        assertEquals("Быстрый узел", Links.serverDescription("vless://u@h:443?type=tcp&serverDescription=" + java.net.URLEncoder.encode("Быстрый узел", "UTF-8") + "#n"))
        assertEquals("Fast node NL", Links.serverDescription("vless://u@h:443?serverDescription=" + java.util.Base64.getEncoder().encodeToString("Fast node NL".toByteArray()) + "#n"))
        assertEquals("Узел в Нидерландах", Links.serverDescription("trojan://p@h:443?serverDescription=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("Узел в Нидерландах".toByteArray()) + "#n"))
        assertNull(Links.serverDescription("vless://u@h:443?type=tcp#n")); assertNull(Links.serverDescription("vmess://eyJhZGQiOiJ4In0=")); assertNull(Links.serverDescription("vless://u@h:443?serverDescription=#n"))
        assertEquals(120, Links.serverDescription("vless://u@h:443?serverDescription=" + "а".repeat(500))!!.length)
    }
}

class SubMetaCommentsTest {
    @Test fun readsKnownKeysFromCommentLines() {
        val c = SubMetaParser.comments("﻿#profile-title: base64:0JzQvtGP\n#Subscription-Userinfo: upload=1; download=2; total=3; expire=4\n#new-url: https://new.example/sub/x\nvless://x@h:1#n\n#profile-update-interval: 12")
        assertEquals("base64:0JzQvtGP", c["profile-title"]); assertEquals("upload=1; download=2; total=3; expire=4", c["subscription-userinfo"])
        assertEquals("https://new.example/sub/x", c["new-url"]); assertEquals("12", c["profile-update-interval"])
    }

    @Test fun ignoresUnknownKeysJsonAndRouting() {
        assertTrue(SubMetaParser.comments("[{\"a\":1}]").isEmpty()); assertTrue(SubMetaParser.comments("{\"#new-url\": \"x\"}").isEmpty())
        val c = SubMetaParser.comments("#routing: happ://routing/add/xyz\n#some key: v\n#random: v\n#announce:\n#support-url: https://t.me/x")
        assertEquals(setOf("support-url"), c.keys)
    }

    @Test fun firstValueWinsAndLongValuesAreDropped() {
        assertEquals("https://a.example/", SubMetaParser.comments("#fallback-url: https://a.example/\n#fallback-url: https://b.example/")["fallback-url"])
        assertTrue(SubMetaParser.comments("#announce: " + "x".repeat(4001)).isEmpty())
    }

    @Test fun commentValuesGoThroughTheSameChecksAsHeaders() {
        val cm = SubMetaParser.comments("#new-url: http://insecure.example/x\n#fallback-url: https://ok.example/sub\n#profile-update-interval: 9999")
        val m = SubMetaParser.parse({ cm[it].orEmpty() }, "https://old.example/sub/T")
        assertNull(m.newUrl); assertEquals("https://ok.example/sub", m.fallback); assertEquals(0, m.intervalH)
    }
}
