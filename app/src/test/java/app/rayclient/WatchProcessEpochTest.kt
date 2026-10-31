package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchProcessEpochTest {
    private fun decide(
        watch: Int = 8, current: Int = 8, tun: Boolean = true,
        checkedPid: Int = 123, currentPid: Int = 123,
        alive: Boolean = false, restarts: Int = 3
    ) = WatchProcessEpoch.afterAlive(watch, current, tun, checkedPid, currentPid, alive, restarts)

    @Test fun lateDeadPidAfterStopOrNewStartCannotFailNewSession() {
        assertEquals(WatchProcessDecision.IGNORE, decide(current = 9))
        assertEquals(WatchProcessDecision.IGNORE, decide(current = 10))
    }

    @Test fun replacedPidAndRevokedTunDiscardOldObservation() {
        assertEquals(WatchProcessDecision.IGNORE, decide(currentPid = 456))
        assertEquals(WatchProcessDecision.IGNORE, decide(tun = false))
        assertEquals(WatchProcessDecision.IGNORE, decide(checkedPid = 0, currentPid = 0))
    }

    @Test fun currentProcessStillUsesNormalRestartBudget() {
        assertEquals(WatchProcessDecision.RUNNING, decide(alive = true))
        assertEquals(WatchProcessDecision.RETRY, decide(restarts = 0))
        assertEquals(WatchProcessDecision.RETRY, decide(restarts = 2))
        assertEquals(WatchProcessDecision.FAILED, decide(restarts = 3))
        assertEquals(WatchProcessDecision.FAILED, decide(restarts = 4))
    }

    @Test fun staleHealthyPidCannotPublishConnectedState() {
        assertEquals(WatchProcessDecision.IGNORE, decide(alive = true, current = 9))
        assertEquals(WatchProcessDecision.IGNORE, decide(alive = true, currentPid = 456))
    }
}
