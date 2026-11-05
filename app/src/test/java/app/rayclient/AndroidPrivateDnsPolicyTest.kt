package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class AndroidPrivateDnsPolicyTest {
    @Test fun oldAndroidHasNoLinkPropertiesPrivateDnsAPI() {
        assertEquals(AndroidPrivateDnsMode.UNSUPPORTED,
            AndroidPrivateDnsPolicy.classify(27, true, true, true))
    }

    @Test fun hiddenOrUnavailableActiveNetworkMustRemainUnknown() {
        assertEquals(AndroidPrivateDnsMode.UNKNOWN,
            AndroidPrivateDnsPolicy.classify(35, false, false, false))
    }

    @Test fun strictAndOpportunisticModesAreDistinct() {
        assertEquals(AndroidPrivateDnsMode.STRICT,
            AndroidPrivateDnsPolicy.classify(35, true, true, true))
        assertEquals(AndroidPrivateDnsMode.STRICT,
            AndroidPrivateDnsPolicy.classify(35, true, true, false))
        assertEquals(AndroidPrivateDnsMode.UPGRADED,
            AndroidPrivateDnsPolicy.classify(35, true, false, true))
        assertEquals(AndroidPrivateDnsMode.NOT_ACTIVE,
            AndroidPrivateDnsPolicy.classify(35, true, false, false))
    }

    @Test fun statusNeverContainsPrivateDnsHostOrClaimsLeaks() {
        for (mode in AndroidPrivateDnsMode.entries) {
            assertFalse(mode.label.contains("dns.google"))
            assertFalse(mode.label.contains("утечек нет"))
        }
    }
}
