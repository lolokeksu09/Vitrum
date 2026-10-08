package app.rayclient

import org.junit.Assert.*
import org.junit.Test

/** Правки по аудиту 0.25.38: доверенная сеть и открытая Wi-Fi, адреса из панели, очки Shield. */
class AuditFixesTest {
    @Test fun trustedNetworkDoesNotApplyToOpenWifi() {
        val list = listOf("Home")
        assertTrue(Scenarios.trustApplies(Scenarios.WIFI, "Home", list, open = false))
        assertFalse("открытая точка с чужим именем «Home» не доверенная", Scenarios.trustApplies(Scenarios.WIFI, "Home", list, open = true))
    }

    @Test fun fallbackMustBePublicHost() {
        listOf("https://backup.example.net/sub/Q", "https://a.b.example/x").forEach { assertTrue(it, SubMetaParser.publicHost(it)) }
        listOf("https://127.0.0.1/x", "https://192.168.1.5/x", "https://localhost/x", "https://nas.local/x", "https://router.lan/x", "https://printer/x", "https://x.internal/").forEach { assertFalse(it, SubMetaParser.publicHost(it)) }
        val m = SubMetaParser.parse({ if (it == "fallback-url") "https://192.168.1.5/sub" else "" }, "https://old.example/sub/T")
        assertNull(m.fallback)
    }

    @Test fun supportUrlOnlyHttps() {
        assertNull(SubMetaParser.httpsUrl("tel:+100")); assertNull(SubMetaParser.httpsUrl("market://details?id=x")); assertNull(SubMetaParser.httpsUrl("http://t.me/x"))
        assertEquals("https://t.me/x", SubMetaParser.httpsUrl("https://t.me/x"))
    }

    @Test fun ipv6CheckGivesNoPoints() {
        val ipv6 = Shield.evaluate(ShieldInput(true, true, false, emptyList(), false, null, false, "app.rayclient", null, 0, null, null, null, true, null, 1, 0, 0L, null)).first { it.key == "ipv6" }
        assertEquals(0, ipv6.weight); assertEquals(0, ipv6.points)
    }
}

class AuditFixes2Test {
    @Test fun leakCheckReadsOnlyRealAddresses() {
        assertEquals("203.0.113.5", LeakCheck.parseIp("203.0.113.5\n"))
        assertEquals("2001:db8::1", LeakCheck.parseIp("your ip: 2001:db8::1"))
        assertNull("слово из hex-букв не адрес", LeakCheck.parseIp("<html>facade decade</html>"))
        assertNull(LeakCheck.parseIp("version 1.2.3 build 999.1.1.1"))
    }

    @Test fun mappedIpv6LoopbackIsLocal() {
        listOf("::ffff:7f00:1", "::ffff:127.0.0.1", "[::ffff:0.0.0.0]", "localhost.", "LOCALHOST").forEach { assertTrue(it, AutoSub.isLocalAddr(it)) }
        listOf("::ffff:cb00:7105", "::ffff:203.0.113.5", "2001:db8::1").forEach { assertFalse(it, AutoSub.isLocalAddr(it)) }
    }

    @Test fun ipv6PrefixesAreValidated() {
        assertTrue(AsnPrefixes.valid("2a04:82c0::/29")); assertFalse(AsnPrefixes.valid(":::/64")); assertFalse(AsnPrefixes.valid("abc:def/32"))
    }

    @Test fun reportKeepsClassNamesButHidesHosts() {
        val t = ProblemReport.sanitize("tcp:notion.so:443 tcp:android.myvpn-server.ru:443 java.net.UnknownHostException libxray.so [::ffff:1.2.3.4]:443")
        assertFalse(t, "notion.so" in t); assertFalse(t, "myvpn-server" in t); assertFalse(t, "1.2.3.4" in t)
        assertTrue(t, "java.net.UnknownHostException" in t); assertTrue(t, "libxray.so" in t)
    }

    @Test fun newDomainKeepsThePort() {
        val m = SubMetaParser.parse({ if (it == "new-domain") "new.example" else "" }, "https://old.example:8443/sub/T")
        assertEquals("https://new.example:8443/sub/T", m.newUrl)
    }
}

class TimeChangeTest {
    @Test fun timeChangeEventsRescheduleTheVpnWindow() {
        assertEquals(setOf("android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED"), BootReceiver.TIME_CHANGES)
        assertFalse("android.intent.action.BOOT_COMPLETED" in BootReceiver.TIME_CHANGES)
    }
}
