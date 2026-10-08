package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class WidgetAndWifiTest {
    @Test fun widgetKindPriority() {
        assertEquals(VpnWidget.Companion.Kind.OFF, VpnWidget.kind(false, false, false))
        assertEquals(VpnWidget.Companion.Kind.BUSY, VpnWidget.kind(false, true, false))
        assertEquals(VpnWidget.Companion.Kind.ON, VpnWidget.kind(true, false, false))
        assertEquals("блокировка важнее остальных состояний", VpnWidget.Companion.Kind.BLOCKED, VpnWidget.kind(true, true, true))
        assertEquals("Подключено", VpnWidget.label(VpnWidget.Companion.Kind.ON))
    }

    @Test fun ssidCleaning() {
        assertEquals("Home", Scenarios.cleanSsid("\"Home\"")); assertEquals("Home", Scenarios.cleanSsid(" Home ")); assertEquals("Кафе", Scenarios.cleanSsid("\"Кафе\""))
        assertNull(Scenarios.cleanSsid("<unknown ssid>")); assertNull(Scenarios.cleanSsid("\"<unknown ssid>\"")); assertNull(Scenarios.cleanSsid("")); assertNull(Scenarios.cleanSsid(null)); assertNull(Scenarios.cleanSsid("0x"))
    }

    @Test fun trustedMatchIsExact() {
        val list = listOf("Home", "Office 5G")
        assertTrue(Scenarios.isTrusted("Home", list)); assertTrue(Scenarios.isTrusted("Office 5G", list))
        assertFalse("регистр важен", Scenarios.isTrusted("home", list)); assertFalse(Scenarios.isTrusted("Home2", list))
        assertFalse(Scenarios.isTrusted(null, list)); assertFalse(Scenarios.isTrusted("", listOf("")))
    }

    @Test fun trustedWifiTurnsVpnOffButKeepsTheRestOfTheScenario() {
        val wifi = Scen(vpn = 1, profile = "p", ks = 1)
        val trusted = wifi.copy(vpn = 2)   // то же, что делает Scenarios.apply для доверенной сети
        val plan = Scenarios.plan(trusted, "main", setOf("main", "p"), false, connected = true, busy = false, blocked = false, hasSelected = true, vpnPermitted = true)
        assertEquals(Scenarios.VpnStep.STOP, plan.vpn); assertEquals("p", plan.activateProfile); assertEquals(1, plan.killSwitch)
    }
}
