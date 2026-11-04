package app.rayclient

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DnsRouteGuardTest {
    private fun routes(opts: RoutingOptions): List<JSONObject> = ConfigBuilder.previewRules(opts)
    private fun isDns53(rule: JSONObject) =
        rule.optString("port") == "53" && rule.optString("network") == "tcp,udp"
    private fun tag(rule: JSONObject) =
        rule.optString("outboundTag").ifBlank { "BALANCER:" + rule.optString("balancerTag") }
    private fun guardIndex(opts: RoutingOptions) = routes(opts).indexOfFirst { isDns53(it) }

    @Test fun defaultDirectMustNotTakeDns53AwayFromProxy() {
        val r = RoutingOptions(defaultAction = 1, exceptRu = true, bypassLan = true)
        val rules = routes(r)
        val guard = rules.indexOfFirst { isDns53(it) && tag(it) == "proxy" }
        val direct = rules.indexOfLast { it.optString("outboundTag") == "direct" && !it.has("port") }
        assertTrue("Missing DNS 53 proxy guard", guard >= 0)
        assertTrue("DNS guard must run before global and geo direct rules", guard < direct)
        assertEquals("direct", tag(rules.last()))
    }

    @Test fun everyDefaultPolicyHasDns53Guard() {
        for (action in 0..2) {
            val rules = routes(RoutingOptions(defaultAction = action))
            assertTrue("defaultAction=$action", rules.any { isDns53(it) && tag(it) == "proxy" })
            if (action == 2) assertEquals("block", tag(rules.last()))
        }
    }

    @Test fun specializedDnsInterceptRetainsHighestPriority() {
        val modes = listOf(
            RoutingOptions(doh = true, defaultAction = 1),
            RoutingOptions(fakeDns = true, defaultAction = 1),
            RoutingOptions(blockV6 = true, defaultAction = 1)
        )
        modes.forEach { opts ->
            val rules = routes(opts)
            val capture = rules.indexOfFirst { isDns53(it) }
            assertTrue(capture >= 0)
            assertEquals("dns-out", tag(rules[capture]))
            assertEquals(1, rules.count(::isDns53))
        }
    }

    @Test fun explicitUserPort53DirectOrBlockRuleKeepsPriorityInNormalDnsMode() {
        for ((action, expected) in listOf(1 to "direct", 2 to "block")) {
            val rules = routes(RoutingOptions(
                rules = listOf(Rule(2, "53", action)), defaultAction = 1, exceptRu = true))
            val captures = rules.withIndex().filter { it.value.optString("port") == "53" }
            assertEquals(2, captures.size)
            assertEquals(expected, tag(captures.first().value))
            assertEquals("proxy", tag(captures.last().value))
        }
    }

    @Test fun defaultDirectNeverReordersUserPolicyAndIpv6Blocking() {
        val r = RoutingOptions(
            rules = listOf(Rule(2, "853", 1), Rule(2, "53", 0)),
            blockV6 = true, defaultAction = 1)
        val rules = routes(r)
        assertEquals("dns-out", tag(rules.first { isDns53(it) }))
        val ipv6 = rules.indexOfFirst { it.optJSONArray("ip")?.toString()?.contains("::/0") == true }
        val catchAll = rules.indexOfLast { it.optString("outboundTag") == "direct" && !it.has("port") }
        assertTrue(ipv6 >= 0 && ipv6 < catchAll)
        assertEquals("block", tag(rules[ipv6]))
    }

    @Test fun writeRepresentativeDnsGuardConfigsForRealXrayValidation() {
        val dir = java.io.File("/tmp/vitrum-test/dns-route-guard").apply { mkdirs() }
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=ws&security=tls&path=%2Fws&host=example.com&sni=example.com#DNS"
        val parsed = Links.parse(link)
        val outbound = Links.withAddress(parsed, "203.0.113.5")
        val modes = mapOf(
            "default-direct" to RoutingOptions(defaultAction = 1),
            "default-block-doh" to RoutingOptions(defaultAction = 2, doh = true),
            "default-direct-v6blocked" to RoutingOptions(defaultAction = 1, blockV6 = true)
        )
        modes.forEach { (name, opts) ->
            val json = ConfigBuilder.build(parsed, outbound, opts)
            val config = JSONObject(json)
            assertTrue(config.getJSONObject("routing").getJSONArray("rules").length() > 0)
            java.io.File(dir, "$name.json").writeText(json)
        }
    }

    @Test fun diagnosticExplainsPolicyButNeverClaimsLeakProof() {
        val d = DnsRoutePolicy.describe(RoutingOptions(defaultAction = 1))
        assertTrue(d.contains("53"))
        assertFalse(d.contains("утечек нет"))
        assertTrue(DnsRoutePolicy.describe(RoutingOptions(rules = listOf(Rule(2, "50-60", 1))))
            .contains("пользовательск"))
        assertTrue(DnsRoutePolicy.describe(RoutingOptions(doh = true)).contains("DoH"))
    }
}
