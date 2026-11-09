package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class OpenWifiTest {
    private val off = Scen()
    private val on = Scen(vpn = 1, ks = 1)

    @Test fun openTypeIsKnownOnlyFromAndroid12() {
        assertTrue(Scenarios.isOpenType(0, 31)); assertTrue(Scenarios.isOpenType(0, 35))
        assertFalse(Scenarios.isOpenType(0, 30)); assertFalse(Scenarios.isOpenType(null, 34)); assertFalse(Scenarios.isOpenType(2, 34)); assertFalse(Scenarios.isOpenType(4, 34))
    }

    @Test fun openWifiIsASeparateTypeOnlyWhenItsScenarioIsConfigured() {
        val configured = listOf(off, off, off, on); val none = listOf(off, off, off, off)
        assertEquals(Scenarios.OPEN, Scenarios.effectiveClass(Scenarios.WIFI, true, configured))
        assertEquals("без настроенного сценария всё как раньше", Scenarios.WIFI, Scenarios.effectiveClass(Scenarios.WIFI, true, none))
        assertEquals(Scenarios.WIFI, Scenarios.effectiveClass(Scenarios.WIFI, false, configured))
        assertEquals(Scenarios.MOBILE, Scenarios.effectiveClass(Scenarios.MOBILE, true, configured)); assertEquals(Scenarios.ROAMING, Scenarios.effectiveClass(Scenarios.ROAMING, true, configured))
        assertEquals("старое хранилище из трёх сценариев", Scenarios.WIFI, Scenarios.effectiveClass(Scenarios.WIFI, true, listOf(off, off, off)))
    }

    @Test fun anyChangeFromDoNothingCountsAsConfigured() {
        assertFalse(Scenarios.configured(off)); assertFalse(Scenarios.configured(null))
        assertTrue(Scenarios.configured(Scen(vpn = 2))); assertTrue(Scenarios.configured(Scen(profile = "main"))); assertTrue(Scenarios.configured(Scen(ks = 2)))
    }

    @Test fun trustedNetworksNeverApplyToOpenWifi() {
        val list = listOf("Home")
        assertTrue(Scenarios.trustApplies(Scenarios.WIFI, "Home", list))
        assertFalse(Scenarios.trustApplies(Scenarios.OPEN, "Home", list)); assertFalse(Scenarios.trustApplies(Scenarios.MOBILE, "Home", list)); assertFalse(Scenarios.trustApplies(Scenarios.WIFI, "Other", list))
    }

    @Test fun planForOpenWifiStartsVpnWithKillSwitch() {
        val p = Scenarios.plan(on, "main", setOf("main"), false, connected = false, busy = false, blocked = false, hasSelected = true, vpnPermitted = true)
        assertEquals(Scenarios.VpnStep.START, p.vpn); assertEquals(1, p.killSwitch)
    }

    @Test fun fourNetworkNames() = assertEquals(listOf("Wi-Fi", "Мобильная сеть", "Роуминг", "Открытая Wi-Fi"), Scenarios.names)
}
