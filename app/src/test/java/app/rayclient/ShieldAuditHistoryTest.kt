package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ShieldAuditHistoryTest {
    @Test fun historyNeverContainsServerIdentityOrPublicAddresses() {
        val network = ShieldNet(listOf(10808), "203.0.113.4", "198.51.100.7", "",
            1000L, "secretServerName", 17L, proofToken = 4)
        val family = FamilyHttpSnapshot(
            FamilyHttpResult(FamilyHttpStatus.REACHABLE, 123),
            FamilyHttpResult(FamilyHttpStatus.UNKNOWN, -1, FamilyHttpIssue.TIMEOUT),
            1000L, 17L, "secretServerName", "/private/probe.sock", 4L, 11L, false)
        val entry = ShieldAuditPolicy.capture(family, network, 1010L)
        assertEquals(PortObservation.OPEN, entry.ports)
        assertEquals(ProbeObservation.RESPONSE, entry.ipv4)
        assertEquals(ProbeObservation.UNKNOWN, entry.ipv6)
        val report = ShieldAuditPolicy.report(listOf(entry))
        listOf("secretServerName", "203.0.113.4", "198.51.100.7", "/private/probe.sock", "10808").forEach {
            assertFalse("Report leaked $it", report.contains(it))
        }
    }

    @Test fun staleOrDifferentSessionPortSampleIsUnknown() {
        val family = FamilyHttpSnapshot(null, null, 250000L, 55L, "s1", "/sock", 10, 2, false)
        val network = ShieldNet(emptyList(), null, null, "", 249000L, "s1", 55, proofToken = 9)
        assertEquals(PortObservation.UNKNOWN, ShieldAuditPolicy.capture(family, network, 250000L).ports)
        assertEquals(PortObservation.UNKNOWN,
            ShieldAuditPolicy.capture(family, ShieldNet(emptyList(), null, null, "",
                249000L, "s2", 55, proofToken = 10), 250000L).ports)
    }

    @Test fun keepsOnlyLastTwelveObservationsAndComparesFacts() {
        var history = emptyList<ShieldAuditEntry>()
        repeat(17) { i ->
            history = ShieldAuditPolicy.append(history,
                ShieldAuditEntry(i.toLong(), if (i < 8) PortObservation.OPEN else PortObservation.NONE_FOUND,
                    if (i < 9) ProbeObservation.UNKNOWN else ProbeObservation.RESPONSE,
                    ProbeObservation.UNKNOWN))
        }
        assertEquals(12, history.size)
        assertEquals(5L, history.first().atMs)
        assertEquals(16L, history.last().atMs)
        assertEquals("IPv4: получен ответ", ShieldAuditPolicy.diff(history[3], history[4]).single())
        assertFalse(ShieldAuditPolicy.diff(history[5], history[6]).isNotEmpty())
    }
}
