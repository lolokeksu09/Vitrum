package app.rayclient

import org.junit.Assert.*
import org.junit.Test

/** Consecutive server failures must be measured only while network is available. */
class FailoverRecoveryTest {

    @Test fun offlineBreaksConsecutiveServerFailureStreak() {
        val watch = Failover.Watch(threshold = 3)
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(2, watch.fails)

        assertEquals(Failover.Verdict.IGNORED, watch.onResult(false, "", false))
        assertEquals(0, watch.fails)
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(1, watch.fails)
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(Failover.Verdict.SWITCH, watch.onResult(false, "", true))
    }

    @Test fun privateSocketProblemWhileOfflineDoesNotPermanentlyDisableFailover() {
        val watch = Failover.Watch(threshold = 2)
        assertEquals(
            Failover.Verdict.IGNORED,
            watch.onResult(false, HeadProbe.PROXY_UNAVAILABLE, false)
        )
        assertFalse(watch.disabled)
        assertEquals(Failover.Verdict.OK, watch.onResult(true, "", true))
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
    }

    @Test fun newNetworkReEnablesChecksDisabledByEarlierSocketFailure() {
        val watch = Failover.Watch(threshold = 2)
        assertEquals(
            Failover.Verdict.DISABLED,
            watch.onResult(false, HeadProbe.PROXY_UNAVAILABLE, true)
        )
        assertTrue(watch.disabled)
        assertEquals(Failover.Verdict.IGNORED, watch.onResult(true, "", true))

        // Called by RayVpnService.watchLoop on network transitions and Xray reloads.
        watch.reset()
        assertFalse(watch.disabled)
        assertEquals(Failover.Verdict.OK, watch.onResult(true, "", true))
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(Failover.Verdict.SWITCH, watch.onResult(false, "", true))
    }

    @Test fun healthyResponseResetsFailuresWhileStillOnline() {
        val watch = Failover.Watch(threshold = 2)
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
        assertEquals(Failover.Verdict.OK, watch.onResult(true, "", true))
        assertEquals(0, watch.fails)
        assertEquals(Failover.Verdict.FAIL_COUNTED, watch.onResult(false, "", true))
    }
}
