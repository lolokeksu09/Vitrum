package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerChoicePolicyTest {
    @Test fun disconnectedSelectionCanChange() {
        assertEquals(ServerChoiceDecision.SELECT, ServerChoicePolicy.decide("server-b", "server-a", false, false, false))
    }

    @Test fun runningVpnCannotSilentlySwitchSelectedServer() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", "server-a", true, false, false))
    }

    @Test fun startingOrStoppingConnectionCannotChangeSelectedServer() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", "server-a", false, true, false))
    }

    @Test fun killSwitchBlockedTunnelCannotBeRelabeledToAnotherServer() {
        assertEquals(ServerChoiceDecision.REQUIRE_DISCONNECT, ServerChoicePolicy.decide("server-b", "server-a", false, false, true))
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
}
