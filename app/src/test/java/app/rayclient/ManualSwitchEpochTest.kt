package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ManualSwitchEpochTest {
    private fun valid(
        connected: Boolean = true, busy: Boolean = false, blocked: Boolean = false,
        hasTun: Boolean = true, pid: Int = 123, currentId: String? = "old",
        targetId: String? = "new", targetExists: Boolean = true
    ) = ManualSwitchEpoch.mayRequest(connected, busy, blocked, hasTun, pid, currentId, targetId, targetExists)

    @Test fun switchCanOnlyBeEnqueuedWhileOldVpnReallyRuns() {
        assertTrue(valid())
        assertFalse(valid(connected = false))
        assertFalse(valid(busy = true))
        assertFalse(valid(blocked = true))
        assertFalse(valid(hasTun = false))
        assertFalse(valid(pid = -1))
        assertFalse(valid(pid = 0))
    }

    @Test fun oldAndMissingTargetsCannotQueue() {
        assertFalse(valid(targetId = "old"))
        assertFalse(valid(targetId = null))
        assertFalse(valid(targetId = ""))
        assertFalse(valid(currentId = null))
        assertFalse(valid(targetExists = false))
    }

    @Test fun stopOrNewStartCancelsObsoleteSwitchBeforeCommitting() {
        assertTrue(ManualSwitchEpoch.mayProceed(41, 41, true))
        assertFalse(ManualSwitchEpoch.mayProceed(41, 42, true))
        assertFalse(ManualSwitchEpoch.mayProceed(41, 41, false))
    }
}
