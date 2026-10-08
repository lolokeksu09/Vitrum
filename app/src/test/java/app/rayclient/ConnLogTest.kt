package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ConnLogTest {
    @Test fun realXrayLines() {
        val f = File("src/test/fixtures/access-sample.log")
        assertTrue("нет фикстуры src/test/fixtures/access-sample.log", f.exists())
        val p = f.readLines().mapNotNull { ConnLog.parse(it) }
        assertEquals(8, p.size)
        assertEquals(listOf("example.com", "www.google.com", "ya.ru", "cdn.blocked.test", "93.184.216.34", "2606:4700:4700::1111", "rr1---sn-abc.googlevideo.com", "example.com"), p.map { it.host })
        assertEquals(listOf(0, 0, 1, 2, 0, 0, 0, 0), p.map { it.out })
        assertEquals(listOf(false, false, true, true, false, false, false, false), p.map { it.viaRule })
        assertEquals(443, p[0].port); assertEquals(80, p[3].port); assertEquals(443, p[5].port)
    }
    @Test fun tunStyleAndNoise() {
        val a = ConnLog.parse("2026/10/04 07:59:59.478143 from tcp:172.19.0.1:41234 accepted tcp:www.google.com:443 [tun-in -> proxy]")!!
        assertEquals("www.google.com", a.host); assertEquals("tcp", a.proto); assertEquals(0, a.out); assertTrue(a.viaRule)
        val u = ConnLog.parse("2026/10/04 08:00:00.1 from udp:172.19.0.1:5000 accepted udp:[2001:db8::1]:443 [tun-in >> direct] email: x")!!
        assertEquals("2001:db8::1", u.host); assertEquals("udp", u.proto); assertEquals(1, u.out); assertFalse(u.viaRule)
        assertNull(ConnLog.parse("2026/10/04 from 0.0.0.0:0 accepted //cp.cloudflare.com:80 [probe-in >> proxy]"))
        assertNull(ConnLog.parse("2026/10/04 from tcp:1.2.3.4:5 accepted udp:1.1.1.1:53 [tun-in -> dns-out]"))
        assertNull(ConnLog.parse("2026/10/04 from DNS accepted https://1.1.1.1/dns-query [dns-internal -> proxy]"))
        assertNull(ConnLog.parse("[Warning] core: Xray 26.3.27 started"))
    }
    @Test fun grouping() {
        ConnLog.clear()
        val n = 1_000L
        ConnLog.add(ParsedConn("rr1.sn-a.googlevideo.com", 443, "tcp", 0, false), n)
        ConnLog.add(ParsedConn("rr2.sn-b.googlevideo.com", 443, "tcp", 0, false), n + 1)
        ConnLog.add(ParsedConn("rr2.sn-b.googlevideo.com", 443, "tcp", 0, false), n + 2)
        ConnLog.add(ParsedConn("bbc.co.uk", 443, "tcp", 1, true), n + 3)
        ConnLog.add(ParsedConn("93.184.216.34", 80, "tcp", 0, false), n + 4)
        val g = ConnLog.snapshot(true)
        val gv = g.first { it.host == "googlevideo.com" }
        assertEquals(3, gv.count); assertEquals(0, gv.out)
        assertTrue(g.any { it.host == "bbc.co.uk" }); assertTrue(g.any { it.host == "93.184.216.34" && it.isIp })
        assertEquals(3, g.size); assertEquals("93.184.216.34", g.first().host)   // свежие сверху
        assertEquals(4, ConnLog.totals()[0]); assertEquals(1, ConnLog.totals()[1])
        assertEquals("googlevideo.com", ConnLog.baseDomain("rr1.sn-a.googlevideo.com")); assertEquals("bbc.co.uk", ConnLog.baseDomain("www.bbc.co.uk"))
        assertEquals(2, ConnLog.snapshot(false).count { it.host.endsWith("googlevideo.com") })
    }
}
