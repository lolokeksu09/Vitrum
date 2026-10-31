package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class VpnStartEpochTest {
    @Test fun queuedStartMustNotMutateStateAfterStopAndNewStart() {
        val startA = 5
        val stop = 6
        val startB = 7

        assertFalse(VpnStartEpoch.canProceed(startA, stop))
        assertFalse(VpnStartEpoch.canProceed(startA, startB))
        assertFalse(VpnStartEpoch.canProceed(stop, startB))
        assertTrue(VpnStartEpoch.canProceed(startB, startB))
    }

    @Test fun stopRequestedDuringXrayStartupInvalidatesStatusPublication() {
        val starting = 12
        assertTrue(VpnStartEpoch.canProceed(starting, starting))
        val stopRequested = starting + 1
        assertFalse(VpnStartEpoch.canProceed(starting, stopRequested))
    }

    @Test fun successiveRequestsHaveDifferentGeneration() {
        assertFalse(VpnStartEpoch.canProceed(0, 1))
        assertFalse(VpnStartEpoch.canProceed(Int.MAX_VALUE - 1, Int.MAX_VALUE))
    }
}
