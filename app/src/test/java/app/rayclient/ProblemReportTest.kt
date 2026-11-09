package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ProblemReportTest {
    private val s = ProblemReport::sanitize

    @Test fun linksAddressesAndKeysAreRemoved() {
        val t = s("vless://11111111-2222-3333-4444-555555555555@example.com:443?security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg#Name\nsub https://panel.example.org/sub/TOKEN123 mail me@example.org")
        listOf("vless://", "example.com", "panel.example.org", "TOKEN123", "me@example.org", "pbk=", "11111111-2222").forEach { assertFalse(it, it in t) }
        assertTrue("<ссылка>" in t && "<почта>" in t)
    }

    @Test fun ipsUuidAndLongTokensAreReplaced() {
        val t = s("dial tcp 203.0.113.5:443: i/o timeout; id 11111111-2222-3333-4444-555555555555; v6 2001:db8::1 and fe80::1ff:fe23:4567:890a; key ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcd")
        listOf("203.0.113.5", "11111111-2222", "2001:db8", "fe80::", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").forEach { assertFalse(it, it in t) }
        assertTrue("<ip>" in t && "<uuid>" in t && "<ключ>" in t)
        assertEquals("два IPv6 и один IPv4", 3, Regex("<ip>").findAll(t).count())
    }

    @Test fun hostNamesGoButCodeNamesTimesAndVersionsStay() {
        val t = s("lookup my-server.example.net: no such host\n12:34:56 java.net.UnknownHostException at app.rayclient.RayVpnService.start(RayVpnService.kt:1) file xray.log config.json version 0.25.34 Android 14")
        assertFalse("my-server.example.net" in t); assertTrue("<хост>" in t)
        listOf("12:34:56", "java.net.UnknownHostException", "app.rayclient.RayVpnService.start", "xray.log", "config.json", "0.25.34", "Android 14").forEach { assertTrue(it, it in t) }
    }

    @Test fun plainTextIsNotChanged() {
        val plain = "Подключение: нет, состояние: Отключено\n• Kill switch: true; MTU 1500; правил 3, из них выключено 1"
        assertEquals(plain, s(plain))
    }

    @Test fun sanitizingTwiceChangesNothing() {
        val once = s("https://a.example.org/x 203.0.113.5 me@example.org host.example.net")
        assertEquals(once, s(once))
    }
}
