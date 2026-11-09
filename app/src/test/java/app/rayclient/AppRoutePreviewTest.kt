package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutePreviewTest {
    private val own = "app.rayclient"
    private val bank = "com.example.bank"
    private val browser = "com.example.browser"

    @Test fun allAppsExceptVpnProcessEnterTun() {
        assertEquals(AppTunPolicy.IN_TUN, AppRoutePreview.evaluate(0, emptySet(), browser, own))
        assertEquals(AppTunPolicy.EXCLUDED, AppRoutePreview.evaluate(0, emptySet(), own, own))
    }

    @Test fun allowListSendsOnlySelectedAppsIntoVpn() {
        val selected = setOf(bank)
        assertEquals(AppTunPolicy.IN_TUN, AppRoutePreview.evaluate(1, selected, bank, own))
        assertEquals(AppTunPolicy.EXCLUDED, AppRoutePreview.evaluate(1, selected, browser, own))
        assertEquals(AppTunPolicy.EXCLUDED, AppRoutePreview.evaluate(1, setOf(own), own, own))
    }

    @Test fun denyListExcludesSelectedApps() {
        val selected = setOf(bank)
        assertEquals(AppTunPolicy.EXCLUDED, AppRoutePreview.evaluate(2, selected, bank, own))
        assertEquals(AppTunPolicy.IN_TUN, AppRoutePreview.evaluate(2, selected, browser, own))
    }

    @Test fun invalidModesFollowBuilderDefaultDisallowBehavior() {
        assertEquals(AppTunPolicy.IN_TUN, AppRoutePreview.evaluate(-1, emptySet(), browser, own))
        assertEquals(AppTunPolicy.EXCLUDED, AppRoutePreview.evaluate(99, emptySet(), own, own))
    }
}
