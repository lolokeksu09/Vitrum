package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ShieldTest {
    private val reality = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
    private val plain = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=none#P"
    private val NOW = 1_000_000_000_000L

    private fun input(kill: Boolean = true, reconnect: Boolean = true, link: String? = reality, hasSubs: Boolean = true, httpSubs: List<String> = emptyList(),
                      debuggable: Boolean = false, ao: Boolean? = null, lk: Boolean? = null, net: ShieldNet? = null, connected: Boolean = true,
                      selected: String? = "s1", session: Long = 100L) =
        ShieldInput(kill, reconnect, false, httpSubs, hasSubs, link, debuggable, "app.rayclient", null, 0, ao, lk, net, connected, selected, session, 0, NOW)

    private fun good(ip: String = "5.5.5.5", real: String? = "9.9.9.9", ports: List<Int> = emptyList(), server: String? = "s1", session: Long = 100L, vpnNote: String = "") =
        ShieldNet(ports, real, if (ip.isEmpty()) null else ip, vpnNote, NOW - 60_000, server, session)

    private fun score(i: ShieldInput) = Shield.score(Shield.evaluate(i))
    private fun check(i: ShieldInput, key: String) = Shield.evaluate(i).first { it.key == key }

    @Test fun nothingVerifiedIsNeverTenOutOfTen() {
        val s = score(input())
        assertTrue("без проверки сети оценка не может быть 10 (было: 10/10 без единой проверки)", s.value < 8)
        assertTrue(s.preliminary); assertEquals(St.UNKNOWN, check(input(), "ports").st); assertEquals(St.UNKNOWN, check(input(), "ip").st)
    }

    @Test fun everythingVerifiedAndGoodIsTen() {
        val s = score(input(net = good(), ao = true, lk = true))
        assertEquals(10, s.value); assertFalse(s.preliminary); assertNull(s.capReason); assertEquals(s.scored, s.verified)
    }

    @Test fun realLeakCapsTheScoreAtThree() {
        val s = score(input(net = good(ip = "9.9.9.9"), ao = true, lk = true))
        assertEquals("утечка IP — критично (раньше такой случай давал 8/10)", 3, s.value); assertNotNull(s.capReason); assertEquals(St.BAD, check(input(net = good(ip = "9.9.9.9")), "ip").st)
    }

    @Test fun foreignOpenPortCapsTheScoreAtThree() {
        val s = score(input(net = good(ports = listOf(10808)), ao = true, lk = true))
        assertEquals(3, s.value); assertTrue(s.capReason!!.contains("Открытые порты"))
    }

    @Test fun killSwitchOffCapsAtSix() {
        assertEquals(6, score(input(kill = false, net = good(), ao = true, lk = true)).value)
        assertTrue(score(input(kill = false, net = good(), ao = true, lk = true)).capReason!!.contains("Kill switch"))
    }

    @Test fun staleResultAfterServerSwitchIsUnknownNotOk() {
        val net = good(server = "OLD")
        assertEquals("результат для другого сервера не считается актуальным (раньше оставалась галочка)", St.UNKNOWN, check(input(net = net, selected = "s1"), "ip").st)
        assertEquals(St.UNKNOWN, check(input(net = good(session = 1L), session = 2L), "ip").st)   // переподключились
        assertEquals(St.OK, check(input(net = good()), "ip").st)
    }

    @Test fun ipCheckStates() {
        assertEquals("реальный IP не определён — неизвестно, а не утечка", St.UNKNOWN, check(input(net = good(real = null)), "ip").st)
        assertEquals(St.UNKNOWN, check(input(net = good(), connected = false), "ip").st)
        assertEquals(St.WARN, check(input(net = good(ip = "", vpnNote = "таймаут")), "ip").st)
    }

    @Test fun alwaysOnComesFromTheVpnService() {
        assertEquals(St.OK, check(input(ao = true, lk = true), "alwayson").st)
        assertEquals(St.WARN, check(input(ao = true, lk = false), "alwayson").st)
        assertEquals("не включён — предупреждение, а не молчаливый пропуск", St.WARN, check(input(ao = false, lk = false), "alwayson").st)
        assertEquals(St.UNKNOWN, check(input(ao = null, lk = null), "alwayson").st); assertEquals(0, check(input(), "alwayson").weight)
        // запасной способ (скрытая настройка), когда служба ещё не отвечала
        val fb = ShieldInput(true, true, false, emptyList(), true, reality, false, "app.rayclient", "app.rayclient", 1, null, null, null, true, "s1", 1, 0, NOW)
        assertEquals(St.OK, Shield.evaluate(fb).first { it.key == "alwayson" }.st)
    }

    @Test fun debugBuildAndTransportAndSubscriptions() {
        assertEquals(St.WARN, check(input(debuggable = true), "build").st)
        assertEquals(St.WARN, check(input(link = plain), "transport").st)
        assertEquals(St.WARN, check(input(httpSubs = listOf("panel")), "subs").st)
        assertTrue(Shield.evaluate(input(hasSubs = false)).none { it.key == "subs" })
    }

    @Test fun pointsAndGroupingAreConsistent() {
        val l = Shield.evaluate(input(net = good(), ao = true, lk = true))
        l.forEach { assertEquals(if (it.st == St.OK) it.weight else 0, it.points) }
        assertEquals(l.sumOf { it.points }, l.filter { it.weight > 0 }.sumOf { it.points })
        assertTrue("у каждой проверки есть устойчивый ключ", l.all { it.key.isNotEmpty() })
    }

    @Test fun strengthenOffersOnlyWhatIsNotInOrderAndSafeToSwitch() {
        // всё выключено: предлагаем kill switch и переподключение; DoH — информационный пункт, тоже предлагается
        val bad = Shield.evaluate(input(kill = false, reconnect = false))
        val acts = Shield.fixable(bad)
        assertTrue(Act.KILL in acts); assertTrue(Act.RECONNECT in acts)
        assertFalse("системные настройки VPN сама не меняем", Act.VPN_SETTINGS in acts)
        assertEquals("без повторов", acts.size, acts.toSet().size)
        // всё включено: нечего усиливать (DoH выключен по умолчанию и предлагается отдельно)
        val ok = Shield.fixable(Shield.evaluate(input(kill = true, reconnect = true)))
        assertFalse(Act.KILL in ok); assertFalse(Act.RECONNECT in ok)
    }

    @Test fun problemIsReportedForCriticalPortsOrCap() {
        val withPort = Shield.evaluate(input(net = good(ports = listOf(10808)), ao = true, lk = true))
        assertTrue(Shield.hasProblem(withPort, Shield.score(withPort)))
        val clean = Shield.evaluate(input(net = good(), ao = true, lk = true))
        assertFalse(Shield.hasProblem(clean, Shield.score(clean)))
    }

    @Test fun clockAndInstallerDoNotChangeTheScore() {
        val base = input(net = good(), ao = true, lk = true)
        fun withExtra(auto: Boolean?, known: Boolean, inst: String?) = ShieldInput(base.killSwitch, base.reconnectNet, false, emptyList(), true, reality, false, "app.rayclient",
            null, 0, true, true, base.net, true, "s1", 100L, 0, NOW, autoTime = auto, installerKnown = known, installer = inst)
        val off = Shield.evaluate(withExtra(false, true, null))
        assertEquals(St.WARN, off.first { it.key == "clock" }.st)
        assertEquals(St.INFO, off.first { it.key == "installer" }.st)
        assertEquals(10, Shield.score(off).value)
        assertEquals(St.OK, Shield.evaluate(withExtra(true, true, "com.android.vending")).first { it.key == "clock" }.st)
        val unknown = Shield.evaluate(withExtra(null, false, null))
        assertTrue(unknown.none { it.key == "clock" || it.key == "installer" })
    }

    private fun extra(net: ShieldNet? = null, ifaces: List<String>? = null, apps: List<String>? = null, auto: Boolean? = null) =
        ShieldInput(true, true, false, emptyList(), true, reality, false, "app.rayclient", null, 0, true, true, net, true, "s1", 100L, 0, NOW,
            autoTime = auto, vpnIfaces = ifaces, vpnApps = apps)

    @Test fun clockSkewFromServerDateWinsOverSetting() {
        fun skew(ms: Long) = ShieldNet(emptyList(), "9.9.9.9", "5.5.5.5", "", NOW - 1000, "s1", 100L, ms)
        assertEquals(St.OK, Shield.evaluate(extra(net = skew(60_000), auto = false)).first { it.key == "clock" }.st)
        val ahead = Shield.evaluate(extra(net = skew(20 * 60_000L))).first { it.key == "clock" }
        assertEquals(St.WARN, ahead.st); assertTrue(ahead.detail.contains("спешат"))
        assertTrue(Shield.evaluate(extra(net = skew(-20 * 60_000L))).first { it.key == "clock" }.detail.contains("отстают"))
    }

    @Test fun otherVpnAndVpnAppsAreInfoOnly() {
        val l = Shield.evaluate(extra(ifaces = listOf("tun0"), apps = listOf("Alpha", "Beta")))
        assertEquals(St.WARN, l.first { it.key == "othervpn" }.st)
        assertTrue(l.first { it.key == "vpnapps" }.detail.contains("Alpha, Beta"))
        assertEquals(0, l.first { it.key == "othervpn" }.weight)
        assertEquals(St.INFO, Shield.evaluate(extra(ifaces = emptyList())).first { it.key == "othervpn" }.st)
        assertTrue(Shield.evaluate(extra(apps = emptyList())).none { it.key == "vpnapps" || it.key == "othervpn" })
    }

    @Test fun batteryCheckIsInfoOnlyAndNeverChangesTheScore() {
        fun with(b: Boolean?) = ShieldInput(true, true, false, emptyList(), true, reality, false, "app.rayclient", null, 0, true, true, good(), true, "s1", 100L, 0, NOW, batteryOk = b)
        assertEquals(St.WARN, Shield.evaluate(with(false)).first { it.key == "battery" }.st)
        assertEquals(St.OK, Shield.evaluate(with(true)).first { it.key == "battery" }.st)
        assertTrue(Shield.evaluate(with(null)).none { it.key == "battery" })
        assertEquals(Shield.score(Shield.evaluate(with(true))).value, Shield.score(Shield.evaluate(with(false))).value)
        assertEquals(0, Shield.evaluate(with(false)).first { it.key == "battery" }.weight)
    }
}
