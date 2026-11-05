package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class DnsAddressPolicyTest {
    @Test fun numericIpv4AndIpv6AreAcceptedWithoutResolvingHostnames() {
        assertTrue(DnsAddressPolicy.isLiteral("1.1.1.1"))
        assertTrue(DnsAddressPolicy.isLiteral("2001:4860:4860::8888"))
        assertTrue(DnsAddressPolicy.isLiteral("2606:4700:4700::1111"))
        assertTrue(DnsAddressPolicy.isLiteral("::1"))
        assertFalse(DnsAddressPolicy.isLiteral("one.one.one.one"))
    }

    @Test fun impossibleIpv4AndIncompleteIpv6AreRejected() {
        listOf("999.1.1.1", "256.0.0.1", "1.2.3", "1.2.3.444",
            "1:2:3", "2001:db8:1:2:3:4:5", "2001:::1", "1::2::3",
            "::gggg", "[2001:db8::1]", "fe80::1%wlan0", "1.2.3.4:53").forEach { bad ->
            assertFalse("Unexpectedly accepted: $bad", DnsAddressPolicy.isLiteral(bad))
        }
    }

    @Test fun mixedInputKeepsValidResolversDeduplicatedAndReportsInvalidCount() {
        val parsed = DnsAddressPolicy.parse(" 1.1.1.1, 999.1.1.1; 1.1.1.1\n2001:4860:4860::8888 host.name ")
        assertEquals(listOf("1.1.1.1", "2001:4860:4860::8888"), parsed.valid)
        assertEquals(2, parsed.invalidCount)
        assertFalse(parsed.fallbackRequired)
    }

    @Test fun invalidOnlyCustomConfigurationUsesExistingPresetWithoutDataLoss() {
        val fallback = listOf("1.1.1.1", "8.8.8.8")
        val parsed = DnsAddressPolicy.parse("999.999.999.999, bad.dns")
        assertEquals(2, parsed.invalidCount)
        assertTrue(parsed.fallbackRequired)
        assertEquals(fallback, parsed.select(fallback))
        assertEquals(fallback, DnsAddressPolicy.parse("").select(fallback))
    }

    @Test fun invalidHostMustNotTriggerNetworkLookup() {
        val bad = "dns.example.invalid"
        assertFalse(DnsAddressPolicy.isLiteral(bad))
        assertEquals(1, DnsAddressPolicy.parse(bad).invalidCount)
    }
}
