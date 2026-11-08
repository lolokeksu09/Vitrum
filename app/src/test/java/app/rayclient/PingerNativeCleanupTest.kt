package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class PingerNativeCleanupTest {
    @Test
    fun doesNotInvokeNativeCleanupWhenNoProcessWasStarted() {
        var stoppedPid = 0

        stopNativeProcessIfStarted(0) { stoppedPid = it }

        assertEquals(0, stoppedPid)
    }

    @Test
    fun doesNotInvokeNativeCleanupForFailedSpawn() {
        var called = false

        stopNativeProcessIfStarted(-1) { called = true }

        assertEquals(false, called)
    }

    @Test
    fun stopsTheStartedProcess() {
        var stoppedPid = 0

        stopNativeProcessIfStarted(42) { stoppedPid = it }

        assertEquals(42, stoppedPid)
    }
}
