package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerChoicePolicyTest {
    @Test fun disconnectedSelectionCanChange() {
        assertEquals(ServerChoiceDecision.SELECT, ServerChoicePolicy.decide("server-b", "server-a", false, false, false))
    }

    @Test fun runningVpnRequestsGuardedSwitchInsteadOfRelabeling() {
        assertEquals(ServerChoiceDecision.REQUEST_SWITCH, ServerChoicePolicy.decide("server-b", "server-a", true, false, false))
    }

    @Test fun startingOrStoppingConnectionCannotChangeSelectedServer() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", "server-a", false, true, false))
    }

    @Test fun killSwitchBlockedTunnelCannotBeRelabeledToAnotherServer() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", "server-a", false, false, true))
    }

    @Test fun noSwitchDuringBlockedOrBusyService() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("b", "a", true, true, false))
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("b", "a", true, false, true))
    }

    @Test fun alreadySelectedServerIsAlwaysANoop() {
        for (connected in listOf(false, true)) {
            for (busy in listOf(false, true)) {
                for (blocked in listOf(false, true)) {
                    assertEquals(ServerChoiceDecision.NO_CHANGE,
                        ServerChoicePolicy.decide("server-a", "server-a", connected, busy, blocked))
                }
            }
        }
    }

    @Test fun initialSelectionIsBlockedWhileVpnIsActive() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", null, true, false, false))
        assertEquals(ServerChoiceDecision.SELECT, ServerChoicePolicy.decide("server-b", null, false, false, false))
    }

    @Test fun cannotDeleteSelectedServerWhileVpnSessionIsActive() {
        assertEquals(false, ServerDeletionPolicy.mayDelete(
            "server-a", setOf("server-a"), connected = true, busy = false, blocked = false, switching = false
        ))
    }

    @Test fun cannotDeleteSelectedServerWhileServiceIsBusyBlockedOrSwitching() {
        val states = listOf(
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(false, false, false)
        )
        for ((connected, busy, blocked) in states) {
            val switching = !connected && !busy && !blocked
            assertEquals(false, ServerDeletionPolicy.mayDelete(
                "server-a", setOf("server-a"), connected, busy, blocked, switching
            ))
        }
    }

    @Test fun canDeleteInactiveServerWithoutChangingActiveRoute() {
        assertEquals(true, ServerDeletionPolicy.mayDelete(
            "server-a", setOf("server-b"), connected = true, busy = false, blocked = false, switching = false
        ))
    }

    @Test fun canDeleteSelectedServerAfterVpnIsFullyStopped() {
        assertEquals(true, ServerDeletionPolicy.mayDelete(
            "server-a", setOf("server-a"), connected = false, busy = false, blocked = false, switching = false
        ))
    }
}


class SubscriptionRefreshPolicyTest {
    @Test fun activeSelectedServerCannotDisappearDuringRefresh() {
        assertEquals(false, SubscriptionRefreshPolicy.mayApply(
            selectedSubId = "sub-a",
            selectedLink = "vless://active.example",
            updatingSubId = "sub-a",
            incomingLinks = setOf("vless://new.example"),
            routeProtected = true
        ))
    }

    @Test fun activeSelectedServerMayRemainWhenItsLinkIsStillPublished() {
        assertEquals(true, SubscriptionRefreshPolicy.mayApply(
            selectedSubId = "sub-a",
            selectedLink = "vless://active.example",
            updatingSubId = "sub-a",
            incomingLinks = setOf("vless://active.example", "vless://new.example"),
            routeProtected = true
        ))
    }

    @Test fun refreshOfAnotherSubscriptionCannotBlockActiveRoute() {
        assertEquals(true, SubscriptionRefreshPolicy.mayApply(
            selectedSubId = "sub-a",
            selectedLink = "vless://active.example",
            updatingSubId = "sub-b",
            incomingLinks = emptySet(),
            routeProtected = true
        ))
    }

    @Test fun removedSelectionMayBeReplacedAfterVpnIsFullyStopped() {
        assertEquals(true, SubscriptionRefreshPolicy.mayApply(
            selectedSubId = "sub-a",
            selectedLink = "vless://active.example",
            updatingSubId = "sub-a",
            incomingLinks = setOf("vless://new.example"),
            routeProtected = false
        ))
    }
    @Test fun activeAutoServerCannotBeRemovedByDisablingJsonMode() {
        assertEquals(false, SubscriptionRefreshPolicy.mayRemoveAutoServers(
            selectedId = "auto-server-a",
            autoServerIds = setOf("auto-server-a", "auto-server-b"),
            routeProtected = true
        ))
    }

    @Test fun inactiveAutoServersCanBeRemovedWithoutChangingActiveRoute() {
        assertEquals(true, SubscriptionRefreshPolicy.mayRemoveAutoServers(
            selectedId = "manual-server",
            autoServerIds = setOf("auto-server-a", "auto-server-b"),
            routeProtected = true
        ))
    }

    @Test fun activeAutoServerCanBeRemovedAfterVpnIsFullyStopped() {
        assertEquals(true, SubscriptionRefreshPolicy.mayRemoveAutoServers(
            selectedId = "auto-server-a",
            autoServerIds = setOf("auto-server-a"),
            routeProtected = false
        ))
    }
}
