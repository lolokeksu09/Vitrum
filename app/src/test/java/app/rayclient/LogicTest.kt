package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private fun srv(id: String, link: String = "vless://$id") = Server(id, id, link, null)

    // ---------- автопереключение ----------
    @Test fun failoverOrdering() {
        val a = srv("A"); val b = srv("B"); val c = srv("C"); val d = srv("D"); val cur = srv("CUR")
        val rank = mapOf("A" to 0.2, "B" to 0.9, "C" to 0.5, "D" to 0.7, "CUR" to 1.0)
        val r = Failover.candidates(listOf(a, b, c, d, cur), "CUR", setOf(a.link), { rank[it.id]!! })
        assertEquals("избранный первым, дальше по рейтингу, текущий исключён", listOf("A", "B", "D", "C"), r.map { it.id })
        assertEquals(2, Failover.candidates(listOf(a, b, c, d), null, emptySet(), { rank[it.id]!! }, limit = 2).size)
        assertTrue(Failover.candidates(emptyList(), null, emptySet(), { 0.0 }).isEmpty())
    }

    @Test fun watchCountsOnlyRealFailures() {
        val w = Failover.Watch(3)
        assertEquals(Failover.Verdict.OK, w.onResult(true, "", true))
        assertEquals(Failover.Verdict.FAIL_COUNTED, w.onResult(false, "таймаут", true))
        assertEquals(Failover.Verdict.FAIL_COUNTED, w.onResult(false, "таймаут", true))
        assertEquals("успех обнуляет счётчик", Failover.Verdict.OK, w.onResult(true, "", true)); assertEquals(0, w.fails)
        assertEquals(Failover.Verdict.FAIL_COUNTED, w.onResult(false, "сервер закрыл соединение", true))
        assertEquals("без сети сбой не считается", Failover.Verdict.IGNORED, w.onResult(false, "таймаут", false)); assertEquals(1, w.fails)
        assertEquals(Failover.Verdict.FAIL_COUNTED, w.onResult(false, "таймаут", true))
        assertEquals("третий подряд — переключаемся и счётчик сброшен", Failover.Verdict.SWITCH, w.onResult(false, "таймаут", true)); assertEquals(0, w.fails)
    }

    @Test fun watchDisablesOnBrokenSocket() {
        val w = Failover.Watch()
        w.onResult(false, "таймаут", true)
        assertEquals(Failover.Verdict.DISABLED, w.onResult(false, HeadProbe.PROXY_UNAVAILABLE, true)); assertTrue(w.disabled)
        assertEquals("после отключения не реагирует вообще", Failover.Verdict.IGNORED, w.onResult(false, "таймаут", true))
        w.reset(); assertTrue("reset сбрасывает счётчик, но не снимает отключение", w.disabled)
    }

    // ---------- сценарии ----------
    private fun plan(s: Scen, active: String = "main", ids: Set<String> = setOf("main", "work"), sub: Boolean = false, connected: Boolean = false,
                     busy: Boolean = false, blocked: Boolean = false, selected: Boolean = true, perm: Boolean = true) =
        Scenarios.plan(s, active, ids, sub, connected, busy, blocked, selected, perm)

    @Test fun scenarioVpn() {
        assertEquals(Scenarios.VpnStep.START, plan(Scen(vpn = 1)).vpn)
        assertEquals("уже подключено — ничего", Scenarios.VpnStep.NONE, plan(Scen(vpn = 1), connected = true).vpn)
        assertEquals("идёт подключение — ничего", Scenarios.VpnStep.NONE, plan(Scen(vpn = 1), busy = true).vpn)
        assertEquals("нет выбранного сервера — ничего", Scenarios.VpnStep.NONE, plan(Scen(vpn = 1), selected = false).vpn)
        assertEquals("нет разрешения VPN — просим открыть приложение", Scenarios.VpnStep.NEED_PERMISSION, plan(Scen(vpn = 1), perm = false).vpn)
        assertEquals(Scenarios.VpnStep.STOP, plan(Scen(vpn = 2), connected = true).vpn)
        assertEquals("kill switch держит туннель — тоже выключаем", Scenarios.VpnStep.STOP, plan(Scen(vpn = 2), blocked = true).vpn)
        assertEquals("выключено — ничего", Scenarios.VpnStep.NONE, plan(Scen(vpn = 2)).vpn)
        assertEquals(Scenarios.VpnStep.NONE, plan(Scen(vpn = 0), connected = true).vpn)
    }

    @Test fun scenarioProfileAndKillSwitch() {
        assertEquals("work", plan(Scen(profile = "work")).activateProfile)
        assertNull("профиль уже активен", plan(Scen(profile = "main")).activateProfile)
        assertNull("профиля не существует", plan(Scen(profile = "gone")).activateProfile)
        assertNull("пустой — не менять", plan(Scen(profile = "")).activateProfile)
        assertNull("профиль из подписки недоступен", plan(Scen(profile = "sub"), sub = false).activateProfile)
        assertEquals("sub", plan(Scen(profile = "sub"), sub = true).activateProfile)
        assertNull(plan(Scen(profile = "sub"), active = "sub", sub = true).activateProfile)
        assertEquals(1, plan(Scen(ks = 1)).killSwitch); assertEquals(2, plan(Scen(ks = 2)).killSwitch); assertEquals(0, plan(Scen()).killSwitch)
    }
}
