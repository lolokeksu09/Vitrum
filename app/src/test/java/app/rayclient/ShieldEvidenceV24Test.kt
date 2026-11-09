package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ShieldEvidenceV24Test {
    private val NOW = 1_900_000_000_000L
    private fun input(net: ShieldNet?) = ShieldInput(
        true, true, false, emptyList(), true, null, false,
        "app.rayclient", null, 0, true, true,
        net, true, "serverA", 100, 0, NOW, evidenceToken = 11L
    )
    private fun net(ports: List<Int> = emptyList(), real: String? = "198.51.100.1",
                    via: String? = "203.0.113.2", at: Long = NOW - 5_000,
                    token: Long = 11L) =
        ShieldNet(ports, real, via, "", at, "serverA", 100, proofToken = token)

    private fun check(i: ShieldInput, key: String) = Shield.evaluate(i).single { it.key == key }

    @Test fun unknownPortOwnerIsWarningNotCriticalLeak() {
        val items = Shield.evaluate(input(net(ports = listOf(10808))))
        assertEquals(St.WARN, items.single { it.key == "ports" }.st)
        assertTrue(items.none { it.key == "ports" && it.st == St.BAD })
        assertFalse(Shield.score(items).capReason?.contains("Открытые порты") == true)
    }

    @Test fun matchingProxyExitIpIsSuspiciousButNotProofOfLeak() {
        val items = Shield.evaluate(input(net(via = "198.51.100.1")))
        assertEquals(St.WARN, items.single { it.key == "ip" }.st)
        assertFalse(Shield.score(items).capReason?.contains("IP") == true)
    }

    @Test fun staleEvidenceCannotClaimClosedPortsOrSafeIp() {
        val changedNetwork = input(net(token = 10L))
        assertEquals(St.UNKNOWN, check(changedNetwork, "ports").st)
        assertEquals(St.UNKNOWN, check(changedNetwork, "ip").st)
        val expired = input(net(at = NOW - 300_001))
        assertEquals(St.UNKNOWN, check(expired, "ports").st)
        assertEquals(St.UNKNOWN, check(expired, "ip").st)
        val future = input(net(at = NOW + 1))
        assertEquals(St.UNKNOWN, check(future, "ports").st)
    }

    @Test fun withoutExplicitDirectComparisonIpIsUnknown() {
        assertEquals(St.UNKNOWN, check(input(net(real = null)), "ip").st)
        assertEquals(St.OK, check(input(net(real = null)), "ports").st)
    }

    @Test fun resultMustBelongToTheSameNetworkSessionAndClock() {
        assertTrue(ShieldEvidencePolicy.isCurrent(net(), "serverA", 100, 11L, NOW))
        assertFalse(ShieldEvidencePolicy.isCurrent(net(), "serverA", 101, 11L, NOW))
        assertFalse(ShieldEvidencePolicy.isCurrent(net(), "serverB", 100, 11L, NOW))
        assertFalse(ShieldEvidencePolicy.isCurrent(net(), "serverA", 100, 12L, NOW))
        assertFalse(ShieldEvidencePolicy.isCurrent(net(), "serverA", 100, 11L, NOW + 301_000))
    }
}
