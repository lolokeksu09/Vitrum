package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteExplainTest {
    private val pkg = "com.example.browser"
    private val own = "app.rayclient.clone"

    private fun explain(
        options: RoutingOptions = RoutingOptions(bypassLan = false),
        target: String = "example.org",
        port: Int? = 443,
        appMode: Int = 0,
        selected: Set<String> = emptySet(),
        candidate: String = pkg
    ) = RouteExplain.evaluate(options, appMode, selected, candidate, own, target, port, "tcp")

    @Test fun excludedAppsNeverInheritXrayProxyPrediction() {
        assertEquals(RouteVerdict.EXCLUDED, explain(appMode = 2, selected = setOf(pkg)).verdict)
        assertEquals(RouteVerdict.EXCLUDED, explain(appMode = 1).verdict)
        assertEquals(RouteVerdict.EXCLUDED, explain(candidate = own).verdict)
        assertEquals(RouteVerdict.VPN, explain(appMode = 1, selected = setOf(pkg)).verdict)
    }

    @Test fun firstMatchingRuleWinsAndReorderingChangesAnswer() {
        val direct = Rule(0, "domain:example.org", 1)
        val proxy = Rule(0, "full:api.example.org", 0)
        val a = explain(RoutingOptions(rules = listOf(direct, proxy), bypassLan = false), "api.example.org")
        val b = explain(RoutingOptions(rules = listOf(proxy, direct), bypassLan = false), "api.example.org")
        assertEquals(RouteVerdict.DIRECT, a.verdict)
        assertEquals(RouteVerdict.VPN, b.verdict)
        assertTrue(a.reason.contains("domain:example.org"))
    }

    @Test fun domainSuffixIsNotAnUnsafeSubstring() {
        val o = RoutingOptions(rules = listOf(Rule(0, "domain:example.org", 1)), bypassLan = false, domainStrategy = "AsIs")
        assertEquals(RouteVerdict.DIRECT, explain(o, "a.example.org").verdict)
        assertEquals(RouteVerdict.VPN, explain(o, "badexample.org").verdict)
    }

    @Test fun disabledRulesIgnoredAndDefaultActionsObserved() {
        val rules = listOf(Rule(0, "example.org", 1, enabled = false))
        assertEquals(RouteVerdict.BLOCK, explain(RoutingOptions(rules = rules, defaultAction = 2, bypassLan = false, domainStrategy = "AsIs")).verdict)
        assertEquals(RouteVerdict.DIRECT, explain(RoutingOptions(rules = rules, defaultAction = 1, bypassLan = false, domainStrategy = "AsIs")).verdict)
    }

    @Test fun ipCidrAndIpv6BlockAreBasedOnActualGeneratedOrder() {
        val ip = RoutingOptions(rules = listOf(Rule(1, "192.0.2.0/24", 1)), bypassLan = false)
        assertEquals(RouteVerdict.DIRECT, explain(ip, "192.0.2.18").verdict)
        assertEquals(RouteVerdict.VPN, explain(ip, "198.51.100.18").verdict)
        val v6 = RoutingOptions(blockV6 = true, bypassLan = false)
        assertEquals(RouteVerdict.BLOCK, explain(v6, "2001:db8::3").verdict)
        assertEquals(RouteVerdict.VPN, explain(v6, "198.51.100.3").verdict)
    }

    @Test fun unknownGeoDataAndUnresolvedDomainAreNotReportedAsDirectOrVpn() {
        val unknownGeo = RoutingOptions(rules = listOf(Rule(0, "geosite:telegram", 1)), bypassLan = false)
        assertEquals(RouteVerdict.UNKNOWN, explain(unknownGeo, "example.org").verdict)
        val unresolvedIp = RoutingOptions(rules = listOf(Rule(1, "203.0.113.0/24", 1)), bypassLan = false)
        assertEquals(RouteVerdict.UNKNOWN, explain(unresolvedIp, "server.example").verdict)
        val unknownPrivate = RoutingOptions(bypassLan = true)
        assertEquals(RouteVerdict.UNKNOWN, explain(unknownPrivate, "198.51.100.1").verdict)
    }

    @Test fun portMatchingAndUnknownPortBehaveConservatively() {
        val o = RoutingOptions(rules = listOf(Rule(2, "1000-2000", 2)), bypassLan = false)
        assertEquals(RouteVerdict.BLOCK, explain(o, port = 1234).verdict)
        assertEquals(RouteVerdict.VPN, explain(o, port = 443).verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(o, port = null).verdict)
    }

    @Test fun dnsBranchesRemainUnknownAndNoNetworkProbeIsPerformed() {
        val o = RoutingOptions(doh = true, bypassLan = false)
        assertEquals(RouteVerdict.UNKNOWN, explain(o, target = "1.1.1.1", port = 53).verdict)
        assertEquals(RouteVerdict.UNKNOWN, RouteExplain.dnsIpv4)
        assertEquals(RouteVerdict.UNKNOWN, RouteExplain.dnsIpv6)
        assertEquals(RouteVerdict.UNKNOWN, RouteExplain.actualTraffic)
    }

    @Test fun invalidInputsNeverProduceVerifiedRoute() {
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "a b").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "a.example,b.example").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(port = 65536).verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "999.1.2.3").verdict)
    }
}
