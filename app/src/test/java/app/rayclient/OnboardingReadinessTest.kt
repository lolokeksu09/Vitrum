package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class OnboardingReadinessTest {
    @Test fun missingServerNeverStartsConnection() {
        assertFalse(OnboardingReadiness.evaluate(emptyList(), null, true, true).canRequestConnection)
        assertFalse(OnboardingReadiness.evaluate(listOf("s1"), "deleted", true, true).canRequestConnection)
    }

    @Test fun permissionPendingDoesNotBlockSystemPrompt() {
        val status = OnboardingReadiness.evaluate(listOf("s1"), "s1", false, false)
        assertTrue(status.canRequestConnection)
        assertFalse(status.vpnGranted)
        assertFalse(status.notificationsGranted)
    }

    @Test fun fullyPreparedFirstRunReportsFacts() {
        val status = OnboardingReadiness.evaluate(listOf("s1"), "s1", true, true)
        assertTrue(status.hasServer)
        assertTrue(status.vpnGranted)
        assertTrue(status.notificationsGranted)
    }
}
