package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class NetworkEvidencePolicyTest {
    @Test fun initialNetworkIsNotAReconnect() {
        val event = NetworkEvidencePolicy.available(NetworkEvidenceState<String>(), "wifi", true, true)
        assertEquals("wifi", event.state.last)
        assertFalse(event.state.lost)
        assertFalse(event.invalidateProof)
        assertFalse(event.reconnect)
    }

    @Test fun newNetworkArrivesBeforeOldOneIsLost() {
        val initial = NetworkEvidenceState(last = "wifi")
        val switched = NetworkEvidencePolicy.available(initial, "lte", true, true)
        assertTrue(switched.invalidateProof)
        assertTrue(switched.reconnect)
        assertEquals("lte", switched.state.last)
        val lateLost = NetworkEvidencePolicy.lost(switched.state, "wifi", true)
        assertFalse(lateLost.invalidateProof)
        assertFalse(lateLost.state.lost)
        assertEquals("lte", lateLost.state.last)
    }

    @Test fun lossBeforeReplacementImmediatelyInvalidatesProofButDefersRestart() {
        val initial = NetworkEvidenceState(last = "wifi")
        val lost = NetworkEvidencePolicy.lost(initial, "wifi", true)
        assertTrue(lost.invalidateProof)
        assertTrue(lost.state.lost)
        assertFalse(lost.reconnect)
        val switched = NetworkEvidencePolicy.available(lost.state, "lte", true, true)
        assertTrue(switched.invalidateProof)
        assertTrue(switched.reconnect)
        assertFalse(switched.state.lost)
    }

    @Test fun sameNetworkReturnsAfterLossNeedsReconnect() {
        val lost = NetworkEvidencePolicy.lost(NetworkEvidenceState(last = "wifi"), "wifi", true)
        val back = NetworkEvidencePolicy.available(lost.state, "wifi", true, true)
        assertTrue(back.invalidateProof)
        assertTrue(back.reconnect)
        assertFalse(back.state.lost)
    }

    @Test fun autoReconnectOffStillInvalidatesManualEvidence() {
        val changed = NetworkEvidencePolicy.available(NetworkEvidenceState(last = "wifi"), "lte", true, false)
        assertTrue(changed.invalidateProof)
        assertFalse(changed.reconnect)
        val lost = NetworkEvidencePolicy.lost(NetworkEvidenceState(last = "lte"), "lte", true)
        assertTrue(lost.invalidateProof)
        assertFalse(lost.reconnect)
    }

    @Test fun inactiveVpnOrUnrelatedNetworkLossCannotVerifyAnything() {
        val old = NetworkEvidenceState(last = "lte")
        assertFalse(NetworkEvidencePolicy.lost(old, "wifi", true).invalidateProof)
        val stopped = NetworkEvidencePolicy.lost(old, "lte", false)
        assertFalse(stopped.invalidateProof)
        assertTrue(stopped.state.lost)
        assertFalse(NetworkEvidencePolicy.available(old, "wifi", false, true).reconnect)
    }
}
