package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class IcmpPingTest {
    @Test fun timeIsTakenFromPingOutput() {
        assertEquals(13, IcmpPing.parseTime("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms"))
        assertEquals(1, IcmpPing.parseTime("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=0.4 ms"))
        assertEquals(1, IcmpPing.parseTime("reply: time<1 ms"))
        assertEquals(12, IcmpPing.parseTime("time=11,2 ms"))
        assertNull(IcmpPing.parseTime("")); assertNull(IcmpPing.parseTime("1 packets transmitted, 0 received, 100% packet loss"))
    }

    @Test fun outputIsSortedIntoReplyNoReplyAndCannotRun() {
        assertTrue(IcmpPing.classify("64 bytes from 1.1.1.1: time=20 ms", true) is IcmpPing.Result.Ok)
        assertTrue(IcmpPing.classify("PING 1.2.3.4\n1 packets transmitted, 0 received, 100% packet loss", true) is IcmpPing.Result.NoReply)
        assertTrue(IcmpPing.classify("ping: socket: Operation not permitted", true) is IcmpPing.Result.Unavailable)
        assertTrue(IcmpPing.classify("ping: Permission denied", true) is IcmpPing.Result.Unavailable)
        assertTrue(IcmpPing.classify("", false) is IcmpPing.Result.Unavailable)
    }

    @Test fun rootCommandPassesTheRootShellCheckOnlyForPlainAddresses() {
        assertEquals("ping -c 1 -W 3 203.0.113.7", IcmpPing.command("203.0.113.7"))
        assertEquals("ping -c 1 -W 3 2001:db8::1", IcmpPing.command("2001:db8::1"))
        listOf("1.2.3.4; reboot", "1.2.3.4 -f", "a`id`", "\$(id)", "1.2.3.4|cat", "", "1.2.3.4\nid").forEach { assertNull(it, IcmpPing.command(it)) }
    }

    @Test fun threadSettingFallsBackToTheOldNumbers() {
        val old = AppState.pingThreads
        try {
            AppState.pingThreads = 0; assertEquals(16, AppState.pingWorkers(16)); assertEquals(6, AppState.pingWorkers(6))
            AppState.pingThreads = 1; assertEquals(1, AppState.pingWorkers(16))
            AppState.pingThreads = 8; assertEquals(8, AppState.pingWorkers(6))
            AppState.pingThreads = 99; assertEquals(6, AppState.pingWorkers(6))
        } finally { AppState.pingThreads = old }
    }
}
