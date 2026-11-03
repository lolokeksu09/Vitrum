package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class FamilyHttpProbeTest {
    @Test fun ipv4ResponseMustBeAnEntireLiteralIpv4Address() {
        val ok = FamilyHttpPolicy.classify(HeadResult(90, "", "203.0.113.55\n"), IpFamily.V4)
        assertEquals(FamilyHttpStatus.REACHABLE, ok.status)
        assertEquals(90, ok.ms)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(90, "", "2001:db8::55"), IpFamily.V4).status)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(90, "", "<html>203.0.113.55</html>"), IpFamily.V4).status)
    }

    @Test fun ipv6ResponseMustBeAnEntireLiteralIpv6Address() {
        val ok = FamilyHttpPolicy.classify(HeadResult(120, "", "2001:db8::55"), IpFamily.V6)
        assertEquals(FamilyHttpStatus.REACHABLE, ok.status)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(120, "", "203.0.113.55"), IpFamily.V6).status)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(120, "", "2001:db8::55\nother data"), IpFamily.V6).status)
    }

    @Test fun errorsNeverProveLeakOrProtection() {
        assertEquals(FamilyHttpStatus.UNKNOWN, FamilyHttpPolicy.classify(null, IpFamily.V4).status)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(-1, "таймаут"), IpFamily.V6).status)
        assertEquals(FamilyHttpStatus.UNKNOWN,
            FamilyHttpPolicy.classify(HeadResult(10, "", ""), IpFamily.V4).status)
        assertEquals(FamilyHttpStatus.UNKNOWN, FamilyHttpPolicy.dnsLeak)
    }

    private val snapshot = FamilyHttpSnapshot(
        ipv4 = FamilyHttpResult(FamilyHttpStatus.REACHABLE, 55),
        ipv6 = FamilyHttpResult(FamilyHttpStatus.UNKNOWN, -1),
        atMs = 100_000L, session = 20L, serverId = "server-1",
        probePath = "/data/user/0/app.rayclient/files/probe/main.sock",
        evidenceToken = 7L, requestId = 8L, running = false
    )

    private fun current(
        s: FamilyHttpSnapshot? = snapshot, connected: Boolean = true, busy: Boolean = false,
        blocked: Boolean = false, session: Long = 20L, server: String? = "server-1",
        path: String = snapshot.probePath, token: Long = 7L, request: Long = 8L,
        now: Long = 120_000L
    ) = FamilyHttpPolicy.isCurrent(s, connected, busy, blocked, session, server,
        path, token, request, now)

    @Test fun resultIsBoundToServiceSessionPathAndNetworkEpoch() {
        assertTrue(current())
        assertFalse(current(connected = false))
        assertFalse(current(busy = true))
        assertFalse(current(blocked = true))
        assertFalse(current(session = 21L))
        assertFalse(current(server = "server-2"))
        assertFalse(current(path = "/other.sock"))
        assertFalse(current(token = 8L))
        assertFalse(current(request = 9L))
        assertFalse(current(s = null))
    }

    @Test fun cachedResultsExpireAndRejectClockRollback() {
        assertTrue(current(now = 160_000L))
        assertFalse(current(now = 160_001L))
        assertFalse(current(now = 99_999L))
    }
}
