package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class NetworkServerPolicyTest {
    private val wifi = NetworkEnvironment(NetworkKind.WIFI, "Home")
    private val unknownWifi = NetworkEnvironment(NetworkKind.WIFI, null)
    private val mobile = NetworkEnvironment(NetworkKind.MOBILE)
    private val roaming = NetworkEnvironment(NetworkKind.ROAMING)
    private val open = NetworkEnvironment(NetworkKind.OPEN_WIFI, "Home")
    private val ids = setOf("wifi-default", "home", "mobile", "roaming", "open")

    private fun policy() = NetworkServerPolicy(
        enabled = true,
        wifiServerId = "wifi-default",
        mobileServerId = "mobile",
        roamingServerId = "roaming",
        openWifiServerId = "open",
        ssidRules = listOf(NetworkSsidServerRule("Home", "home"))
    )

    @Test fun exactSsidWinsOverGeneralWifi() {
        assertEquals("home", NetworkServerPolicyResolver.resolve(policy(), wifi, ids))
    }

    @Test fun unknownOrUnavailableSsidFallsBackToGeneralWifi() {
        assertEquals("wifi-default", NetworkServerPolicyResolver.resolve(policy(), unknownWifi, ids))
        assertEquals("wifi-default", NetworkServerPolicyResolver.resolve(policy(), NetworkEnvironment(NetworkKind.WIFI, "Cafe"), ids))
    }

    @Test fun mobileAndRoamingHaveIndependentRules() {
        assertEquals("mobile", NetworkServerPolicyResolver.resolve(policy(), mobile, ids))
        assertEquals("roaming", NetworkServerPolicyResolver.resolve(policy(), roaming, ids))
    }

    @Test fun roamingExplicitlyInheritsMobileWhenNoRoamingRuleExists() {
        val p = policy().copy(roamingServerId = null)
        assertEquals("mobile", NetworkServerPolicyResolver.resolve(p, roaming, ids))
    }

    @Test fun openWifiUsesOnlyItsExplicitRuleAndNeverAnSsidRuleOrWifiFallback() {
        assertEquals("open", NetworkServerPolicyResolver.resolve(policy(), open, ids))
        val noOpenRule = policy().copy(openWifiServerId = null)
        assertNull(NetworkServerPolicyResolver.resolve(noOpenRule, open, ids))
    }

    @Test fun disabledPolicyAndMissingServersNeverSelectAnything() {
        assertNull(NetworkServerPolicyResolver.resolve(policy().copy(enabled = false), wifi, ids))
        assertNull(NetworkServerPolicyResolver.resolve(policy(), wifi, emptySet()))
        assertNull(NetworkServerPolicyResolver.resolve(policy(), mobile, setOf("wifi-default")))
    }

    @Test fun manualChoiceIsKeptForCurrentNetworkUntilNetworkChanges() {
        val runtime = NetworkServerPolicyRuntime()
        assertEquals("home", runtime.onNetworkChanged(policy(), wifi, "wifi-default", ids))
        runtime.noteManualSelection(wifi)
        assertNull(runtime.onNetworkChanged(policy(), wifi, "mobile", ids))
        assertEquals("mobile", runtime.onNetworkChanged(policy(), mobile, "manual-choice", ids))
    }

    @Test fun sameNetworkCallbacksDoNotRepeatAutomaticSwitch() {
        val runtime = NetworkServerPolicyRuntime()
        assertEquals("home", runtime.onNetworkChanged(policy(), wifi, "wifi-default", ids))
        assertNull(runtime.onNetworkChanged(policy(), wifi, "wifi-default", ids))
    }

    @Test fun deletedConfiguredServerIsIgnored() {
        assertEquals("wifi-default", NetworkServerPolicyResolver.resolve(policy(), wifi, setOf("wifi-default")))
        assertEquals("wifi-default", NetworkServerPolicyResolver.resolve(policy().copy(ssidRules = listOf(NetworkSsidServerRule("Home", "gone"))), wifi, ids - "home"))
    }

    @Test fun networkIdentityIncludesSsidOnlyForWifiClasses() {
        assertNotEquals(
            NetworkServerRuntimeKey.of(wifi),
            NetworkServerRuntimeKey.of(NetworkEnvironment(NetworkKind.WIFI, "Office"))
        )
        assertEquals(
            NetworkServerRuntimeKey.of(mobile),
            NetworkServerRuntimeKey.of(NetworkEnvironment(NetworkKind.MOBILE, "ignored"))
        )
    }
}
