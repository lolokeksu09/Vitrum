package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EncryptedDnsGuardTest {
    private fun rules(options: RoutingOptions): List<JSONObject> = ConfigBuilder.previewRules(options)
    private fun is853(rule: JSONObject): Boolean =
        rule.optString("port") == "853" && rule.optString("network") == "tcp,udp"
    private fun target(rule: JSONObject): String =
        rule.optString("outboundTag").ifEmpty { "BALANCER:" + rule.optString("balancerTag") }

    @Test fun catchAllDirectLanAndGeoNeverPrecedePort853Guard() {
        val options = RoutingOptions(defaultAction = 1, bypassLan = true, exceptRu = true)
        val all = rules(options)
        val guard = all.indexOfFirst(::is853)
        val broad = all.indexOfLast { it.optString("outboundTag") == "direct" && !it.has("port") }
        assertTrue(guard >= 0)
        assertEquals("proxy", target(all[guard]))
        assertTrue(guard < broad)
        assertEquals(RouteVerdict.VPN, RouteExplain.evaluateTunRoute(options, "9.9.9.9", 853, "tcp").verdict)
        assertEquals(RouteVerdict.VPN, RouteExplain.evaluateTunRoute(options, "9.9.9.9", 853, "udp").verdict)
    }

    @Test fun guardExistsForAllDefaultActionsAndDohDoesNotHijackEncrypted853() {
        for (action in 0..2) for (doh in listOf(false, true)) {
            val all = rules(RoutingOptions(defaultAction = action, doh = doh))
            val guards = all.filter(::is853)
            assertEquals(1, guards.size)
            assertEquals("proxy", target(guards.single()))
            assertFalse(guards.single().optString("outboundTag") == "dns-out")
        }
    }

    @Test fun explicitUserRulesRetainPrecedenceAndAreAuditable() {
        for ((action, kind) in listOf(1 to DnsAuditKind.DIRECT, 2 to DnsAuditKind.BLOCK)) {
            val options = RoutingOptions(rules = listOf(Rule(2, "853", action)), defaultAction = 1)
            val all = rules(options)
            assertTrue(all.indexOfFirst { it.optString("port") == "853" && target(it) != "proxy" }
                < all.indexOfFirst(::is853))
            val evidence = EncryptedDnsRouteAudit.inspect(options, listOf("9.9.9.9"))
            assertEquals(kind, evidence.tcp)
            assertEquals(kind, evidence.udp)
            assertFalse(evidence.isLeakProof)
        }
    }

    @Test fun transportRestrictionsAndIpv6BlockAreNotOverridden() {
        val tcp = RoutingOptions(transport = 1, defaultAction = 1)
        assertEquals(RouteVerdict.BLOCK, RouteExplain.evaluateTunRoute(tcp, "1.1.1.1", 853, "udp").verdict)
        assertEquals(RouteVerdict.VPN, RouteExplain.evaluateTunRoute(tcp, "1.1.1.1", 853, "tcp").verdict)
        val udp = RoutingOptions(transport = 2, defaultAction = 1)
        assertEquals(RouteVerdict.BLOCK, RouteExplain.evaluateTunRoute(udp, "1.1.1.1", 853, "tcp").verdict)
        assertEquals(RouteVerdict.VPN, RouteExplain.evaluateTunRoute(udp, "1.1.1.1", 853, "udp").verdict)
        val ipv6 = RoutingOptions(blockV6 = true, defaultAction = 1)
        assertEquals(RouteVerdict.BLOCK, RouteExplain.evaluateTunRoute(ipv6, "2001:4860:4860::8888", 853, "tcp").verdict)
        assertEquals(RouteVerdict.VPN, RouteExplain.evaluateTunRoute(ipv6, "1.1.1.1", 853, "tcp").verdict)
    }

    @Test fun directIpExceptionAndGeoUncertaintyAreNotClassifiedAsProtected() {
        val direct = RoutingOptions(rules = listOf(Rule(1, "9.9.9.9", 1)), defaultAction = 1)
        val mixed = EncryptedDnsRouteAudit.inspect(direct, listOf("9.9.9.9", "1.1.1.1"))
        assertEquals(DnsAuditKind.MIXED, mixed.tcp)
        assertEquals(DnsAuditKind.MIXED, mixed.udp)
        val uncertain = EncryptedDnsRouteAudit.inspect(
            RoutingOptions(rules = listOf(Rule(1, "geoip:private", 1))),
            listOf("9.9.9.9"))
        assertEquals(DnsAuditKind.UNKNOWN, uncertain.overall)
        assertEquals(DnsAuditKind.UNKNOWN,
            EncryptedDnsRouteAudit.inspect(RoutingOptions(), listOf("999.9.9.9")).overall)
    }

    @Test fun auditNeverInventsSystemPrivateDnsEndpoint() {
        val info = EncryptedDnsRouteAudit.inspect(RoutingOptions(), listOf("1.1.1.1"))
        assertEquals(DnsAuditKind.PROXY, info.overall)
        assertFalse(info.isLeakProof)
        assertEquals(1, info.resolvers)
    }
}
