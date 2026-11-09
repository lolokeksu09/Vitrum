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
        candidate: String = pkg,
        protocol: String = "tcp"
    ) = RouteExplain.evaluate(options, appMode, selected, candidate, own, target, port, protocol)

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

    @Test fun generatedRulePriorityIsHonoredAcrossConflictingRules() {
        // Transport restrictions are earlier than user rules and are not «other traffic».
        val transport = explain(
            RoutingOptions(
                rules = listOf(Rule(0, "example.org", 1)),
                transport = 2,
                defaultAction = 1,
                bypassLan = false
            )
        )
        assertEquals(RouteVerdict.BLOCK, transport.verdict)
        assertTrue(transport.reason.contains("network: tcp"))
        assertTrue(!transport.reason.contains("Прочий трафик"))

        // The generated IPv6 safety block precedes explicit user IP rules.
        val ipv6 = explain(
            RoutingOptions(
                rules = listOf(Rule(1, "2001:db8::/32", 1)),
                blockV6 = true,
                bypassLan = false
            ),
            target = "2001:db8::5"
        )
        assertEquals(RouteVerdict.BLOCK, ipv6.verdict)

        // Explicit user rules precede the normal DNS and encrypted-DNS guards.
        val dns = explain(
            RoutingOptions(rules = listOf(Rule(2, "53", 1)), bypassLan = false),
            target = "1.1.1.1",
            port = 53
        )
        assertEquals(RouteVerdict.DIRECT, dns.verdict)
        val dot = explain(
            RoutingOptions(rules = listOf(Rule(2, "853", 2)), bypassLan = false),
            target = "1.1.1.1",
            port = 853
        )
        assertEquals(RouteVerdict.BLOCK, dot.verdict)

        // The same user-first ordering applies to ad, LAN, RU and preset bypass rules.
        val ads = explain(
            RoutingOptions(
                rules = listOf(Rule(0, "ads.example.org", 0)),
                blockAds = true,
                domainStrategy = "AsIs",
                bypassLan = false
            ),
            target = "ads.example.org"
        )
        assertEquals(RouteVerdict.VPN, ads.verdict)

        val lan = explain(
            RoutingOptions(
                rules = listOf(Rule(1, "10.0.0.0/8", 2)),
                bypassLan = true
            ),
            target = "10.10.20.30"
        )
        assertEquals(RouteVerdict.BLOCK, lan.verdict)

        val ru = explain(
            RoutingOptions(
                rules = listOf(Rule(0, "full:bank.example.ru", 2)),
                exceptRu = true,
                domainStrategy = "AsIs",
                bypassLan = false
            ),
            target = "bank.example.ru"
        )
        assertEquals(RouteVerdict.BLOCK, ru.verdict)

        val preset = explain(
            RoutingOptions(
                rules = listOf(Rule(0, "full:store.steampowered.com", 2)),
                bypassPresets = setOf("steam"),
                domainStrategy = "AsIs",
                bypassLan = false
            ),
            target = "store.steampowered.com"
        )
        assertEquals(RouteVerdict.BLOCK, preset.verdict)
    }

    @Test fun uncertainEarlierRulePreventsAFalseLaterMatch() {
        val options = RoutingOptions(
            rules = listOf(
                Rule(0, "geosite:telegram", 1),
                Rule(0, "full:example.org", 2)
            ),
            domainStrategy = "AsIs",
            bypassLan = false
        )
        val result = explain(options, "example.org")
        assertEquals(RouteVerdict.UNKNOWN, result.verdict)
        assertTrue(result.reason.contains("geosite:telegram"))
    }

    @Test fun ipv6CidrBoundariesAndMappedAddressesRemainSixteenByteMatches() {
        val subnet = RoutingOptions(
            rules = listOf(Rule(1, "2001:db8:abcd::/48", 1)),
            domainStrategy = "AsIs",
            bypassLan = false
        )
        assertEquals(RouteVerdict.DIRECT, explain(subnet, "2001:db8:abcd:1::1").verdict)
        assertEquals(RouteVerdict.VPN, explain(subnet, "2001:db8:abce::1").verdict)

        val mapped = RoutingOptions(
            rules = listOf(Rule(1, "::ffff:192.0.2.0/120", 1)),
            domainStrategy = "AsIs",
            bypassLan = false
        )
        assertEquals(RouteVerdict.DIRECT, explain(mapped, "::ffff:192.0.2.42").verdict)
        assertEquals(RouteVerdict.VPN, explain(mapped, "::ffff:192.0.3.42").verdict)
    }

    @Test fun defaultActionCatchAllIsExplainedSeparatelyFromEarlierRestrictions() {
        val result = explain(
            RoutingOptions(defaultAction = 1, bypassLan = false, domainStrategy = "AsIs")
        )
        assertEquals(RouteVerdict.DIRECT, result.verdict)
        assertTrue(result.reason.contains("Прочий трафик"))
    }

    @Test fun builtInDirectBypassesArePredictedOnlyWhenLocallyDecidable() {
        val ru = explain(
            RoutingOptions(exceptRu = true, domainStrategy = "AsIs", bypassLan = false),
            target = "bank.example.ru"
        )
        assertEquals(RouteVerdict.DIRECT, ru.verdict)

        val whitelist = explain(
            RoutingOptions(
                whitelistOn = true,
                whitelistDomains = listOf("bank.example"),
                domainStrategy = "AsIs",
                bypassLan = false
            ),
            target = "secure.bank.example"
        )
        assertEquals(RouteVerdict.DIRECT, whitelist.verdict)

        // LAN/geoip and preset/geosite matches need a local geo database, so stay UNKNOWN.
        val lan = explain(RoutingOptions(bypassLan = true), target = "10.0.0.8")
        assertEquals(RouteVerdict.UNKNOWN, lan.verdict)
        val preset = explain(
            RoutingOptions(bypassPresets = setOf("steam"), bypassLan = false),
            target = "store.steampowered.com"
        )
        assertEquals(RouteVerdict.UNKNOWN, preset.verdict)
    }

    @Test fun transportPolicyDistinguishesTcpUdpAndGameOverride() {
        val tcpOnly = RoutingOptions(transport = 1, bypassLan = false)
        assertEquals(RouteVerdict.BLOCK, explain(tcpOnly, "1.1.1.1", 443, protocol = "udp").verdict)
        assertEquals(RouteVerdict.VPN, explain(tcpOnly, "1.1.1.1", 443, protocol = "tcp").verdict)

        val game = RoutingOptions(transport = 1, game = true, bypassLan = false)
        assertEquals(RouteVerdict.VPN, explain(game, "1.1.1.1", 443, protocol = "udp").verdict)
    }

    @Test fun invalidInputsNeverProduceVerifiedRoute() {
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "a b").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "a.example,b.example").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(port = 65536).verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "999.1.2.3").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = "1::2:3:4:5:6:7:8").verdict)
        assertEquals(RouteVerdict.UNKNOWN, explain(target = ":2001:db8::1").verdict)
    }
    @Test fun domainRulesAreNotPredictedWhenSniffingIsDisabled() {
        val rule = Rule(0, "domain:example.org", 1)
        val noSniff = RoutingOptions(
            rules = listOf(rule), sniff = false, fakeDns = false,
            defaultAction = 0, bypassLan = false
        )
        assertEquals(RouteVerdict.VPN, explain(noSniff, "example.org").verdict)

        // fake DNS forces Xray sniffing even when the explicit sniff switch is off.
        val fakeDns = RoutingOptions(
            rules = listOf(rule), sniff = false, fakeDns = true,
            defaultAction = 0, bypassLan = false
        )
        assertEquals(RouteVerdict.DIRECT, explain(fakeDns, "example.org").verdict)
    }

}
