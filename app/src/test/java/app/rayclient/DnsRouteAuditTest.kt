package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class DnsRouteAuditTest {
    private fun inspect(opts: RoutingOptions, vararg ips: String) =
        DnsRouteAudit.inspect(opts, ips.toList())

    @Test fun guardRoutesBothProtocolsBeforeCatchAllDirect() {
        val r = inspect(RoutingOptions(defaultAction = 1, bypassLan = true), "9.9.9.9")
        assertEquals(DnsAuditKind.PROXY, r.udp)
        assertEquals(DnsAuditKind.PROXY, r.tcp)
        assertEquals(1, r.resolvers)
        assertEquals(DnsAuditKind.PROXY, r.overall)
        assertFalse(r.isLeakProof)
    }

    @Test fun explicitDestinationIpBypassBeforePortGuardIsReportedDirect() {
        val r = inspect(RoutingOptions(rules = listOf(Rule(1, "9.9.9.9", 1)), defaultAction = 1),
            "9.9.9.9")
        assertEquals(DnsAuditKind.DIRECT, r.udp)
        assertEquals(DnsAuditKind.DIRECT, r.tcp)
    }

    @Test fun explicitPortRuleCanBlockDns() {
        val r = inspect(RoutingOptions(rules = listOf(Rule(2, "53", 2))), "9.9.9.9")
        assertEquals(DnsAuditKind.BLOCK, r.overall)
    }

    @Test fun dohAndFakeDnsAndBlockV6AreRecognizedAsInternalDnsRoute() {
        for (opts in listOf(RoutingOptions(doh = true), RoutingOptions(fakeDns = true),
            RoutingOptions(blockV6 = true))) {
            val r = inspect(opts, "1.1.1.1")
            assertEquals(DnsAuditKind.XRAY_DNS, r.udp)
            assertEquals(DnsAuditKind.XRAY_DNS, r.tcp)
            assertFalse(r.isLeakProof)
        }
    }

    @Test fun differentResolversWithDifferentFirstMatchRoutesAreMixed() {
        val r = inspect(RoutingOptions(rules = listOf(Rule(1, "9.9.9.9", 1))),
            "9.9.9.9", "1.1.1.1")
        assertEquals(DnsAuditKind.MIXED, r.udp)
        assertEquals(DnsAuditKind.MIXED, r.overall)
        assertEquals(2, r.resolvers)
    }

    @Test fun geodataRuleBeforeGuardMustRemainUnknown() {
        val r = inspect(RoutingOptions(rules = listOf(Rule(1, "geoip:private", 1))),
            "9.9.9.9")
        assertEquals(DnsAuditKind.UNKNOWN, r.overall)
        assertFalse(r.isLeakProof)
    }

    @Test fun malformedDnsCannotBeClaimedRouted() {
        val r = inspect(RoutingOptions(), "999.1.1.1")
        assertEquals(DnsAuditKind.UNKNOWN, r.overall)
        assertEquals(0, r.resolvers)
    }

    @Test fun evaluationForAlreadyIncludedAppDoesNotOverrideActualAppExclusionLogic() {
        val r = RoutingOptions(defaultAction = 1)
        assertEquals(RouteVerdict.VPN,
            RouteExplain.evaluateTunRoute(r, "1.1.1.1", 53, "udp").verdict)
        assertEquals(RouteVerdict.EXCLUDED,
            RouteExplain.evaluate(r, 2, setOf("com.example.browser"), "com.example.browser",
                "app.rayclient", "1.1.1.1", 53, "udp").verdict)
    }
}
