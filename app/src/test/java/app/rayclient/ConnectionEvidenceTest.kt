package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ConnectionEvidenceTest {
    private val good = EvidenceSnapshot(ProbeOutcome.REACHABLE, 180, 100_000L, "server-A", 500L, 7L)

    @Test fun currentPositiveProbeIsVisibleOnlyForSameSession() {
        assertTrue(EvidencePolicy.isCurrent(good, true, 500L, "server-A", 7L, 125_000L))
        assertFalse(EvidencePolicy.isCurrent(good, false, 500L, "server-A", 7L, 125_000L))
        assertFalse(EvidencePolicy.isCurrent(good, true, 501L, "server-A", 7L, 125_000L))
        assertFalse(EvidencePolicy.isCurrent(good, true, 500L, "server-B", 7L, 125_000L))
        assertFalse(EvidencePolicy.isCurrent(good, true, 500L, "server-A", 8L, 125_000L))
    }

    @Test fun resultsExpireAndClockRollbackDoesNotKeepThemValid() {
        assertTrue(EvidencePolicy.isCurrent(good, true, 500L, "server-A", 7L, 160_000L))
        assertFalse(EvidencePolicy.isCurrent(good, true, 500L, "server-A", 7L, 160_001L))
        assertFalse(EvidencePolicy.isCurrent(good, true, 500L, "server-A", 7L, 99_999L))
    }

    @Test fun connectionProcessAloneIsNeverEvidence() {
        assertFalse(EvidencePolicy.isCurrent(null, true, 500L, "server-A", 7L, 110_000L))
        val noReply = good.copy(outcome = ProbeOutcome.NO_REPLY, elapsedMs = -1)
        assertTrue(EvidencePolicy.isCurrent(noReply, true, 500L, "server-A", 7L, 110_000L))
        assertEquals(ProbeOutcome.NO_REPLY, noReply.outcome)
    }

    @Test fun generationChangeInvalidatesStaleProbeAfterReconnect() {
        val old = good.copy(token = 99L)
        assertFalse(EvidencePolicy.isCurrent(old, true, 500L, "server-A", 100L, 105_000L))
    }
}
