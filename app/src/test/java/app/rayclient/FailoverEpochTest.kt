package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class FailoverEpochTest {
    @Test fun currentWatchMayApplyCandidateWhileTunnelIsAlive() {
        assertTrue(FailoverEpoch.canModify(7, 7, true))
    }

    @Test fun oldWatchCannotApplyResultAfterStopOrNewStart() {
        val watchStarted = 7
        assertFalse(FailoverEpoch.canModify(watchStarted, 8, true)) // STOP
        assertFalse(FailoverEpoch.canModify(watchStarted, 9, true)) // newer START
    }

    @Test fun resultCannotBeAppliedWhenVpnTunnelWasRevoked() {
        assertFalse(FailoverEpoch.canModify(7, 7, false))
        assertFalse(FailoverEpoch.canModify(7, 8, false))
    }
}
