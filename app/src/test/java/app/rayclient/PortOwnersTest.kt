package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class PortOwnersTest {
    private val tcp = """
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0100007F:2A5A 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 11111 1 0000000000000000 100 0 0 10 0
   1: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10200        0 22222 1 0000000000000000 100 0 0 10 0
   2: 0100007F:2A5A 0100007F:9999 01 00000000:00000000 00:00000000 00000000 10123        0 33333 1 0000000000000000 100 0 0 10 0
   3: 0F02A8C0:1F91 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10300        0 44444 1 0000000000000000 100 0 0 10 0
   4: 0100007F:0035 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1000         0 55555 1 0000000000000000 100 0 0 10 0
"""
    private val tcp6 = """
  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 00000000000000000000000001000000:2A5B 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 66666 1 0000000000000000 100 0 0 10 0
   1: 0000000000000000FFFF00000100007F:2A5C 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10124        0 77777 1 0000000000000000 100 0 0 10 0
   2: 0000000000000000FFFF0000C0A8020F:1F92 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10400        0 88888 1 0000000000000000 100 0 0 10 0
"""

    @Test fun listenersAreOnlyListeningSocketsReachableFromLoopback() {
        val l = PortOwners.parseListeners(tcp + tcp6)
        val byPort = l.associate { it.port to it.uid }
        assertEquals(10123, byPort[0x2A5A]); assertEquals("0.0.0.0 тоже достижим по 127.0.0.1", 10200, byPort[0x1F90])
        assertEquals(1000, byPort[0x35])
        assertEquals("::1", 10123, byPort[0x2A5B]); assertEquals("IPv4 в IPv6", 10124, byPort[0x2A5C])
        assertNull("слушает только на адресе Wi-Fi: по 127.0.0.1 не достижим", byPort[0x1F91])
        assertNull(byPort[0x1F92])
        assertEquals("установленное соединение (01) не слушающий сокет, и дубль порта не двоится", 1, l.count { it.port == 0x2A5A })
    }

    @Test fun garbageAndHeadersAreIgnored() {
        assertTrue(PortOwners.parseListeners("").isEmpty())
        assertTrue(PortOwners.parseListeners("sl local_address\n   x: nonsense\n   0: ZZZZ:XXXX 0 0A 0 0 0 5").isEmpty())
    }

    @Test fun packagesParsing() {
        val m = PortOwners.parsePackages("package:com.example.a uid:10123\npackage:com.example.b uid:10124\npackage:com.example.c uid:10124\nmusor\npackage:no.uid")
        assertEquals(listOf("com.example.a"), m[10123]); assertEquals(listOf("com.example.b", "com.example.c"), m[10124]); assertEquals(2, m.size)
    }

    @Test fun ownersAreResolvedAndMissingPortsAreSaid() {
        val pk = PortOwners.parsePackages("package:com.example.a uid:10123")
        val o = PortOwners.owners(listOf(0x2A5A, 0x1F90, 9999), PortOwners.parseListeners(tcp), pk, 10999)
        assertEquals(listOf(0x1F90, 9999, 0x2A5A), o.map { it.port })
        assertEquals("com.example.a", o.first { it.port == 0x2A5A }.label)
        assertTrue(o.first { it.port == 0x1F90 }.label.contains("10200"))
        assertNull(o.first { it.port == 9999 }.uid)
        assertTrue(o.none { it.self })
        assertTrue("порт самого приложения помечается", PortOwners.owners(listOf(0x2A5A), PortOwners.parseListeners(tcp), pk, 10123).single().self)
    }

    @Test fun labelsForSystemUids() {
        assertEquals("root", PortOwners.label(0, emptyMap()))
        assertTrue(PortOwners.label(1000, emptyMap()).contains("системная"))
        assertEquals("a, b, c +1", PortOwners.label(10000, mapOf(10000 to listOf("a", "b", "c", "d"))))
    }

    @Test fun rootSwitchIsNeverRestoredFromABackup() {
        assertFalse("после восстановления копии root-функции всегда выключены", Backup.restorable("rootOn"))
        assertTrue(Backup.restorable("haptics")); assertTrue(Backup.restorable("servers"))
    }

    @Test fun rootShellRefusesAnythingButPlainCommands() {
        listOf("id -u", "cat /proc/net/tcp /proc/net/tcp6", "cmd package list packages -U").forEach { assertTrue(it, RootShell.isSafe(it)) }
        listOf("id; reboot", "cat /proc/net/tcp | sh", "echo `id`", "id && id", "id\nid", "cat \$HOME", "cat 'x'", "cat \"x\"", "", "a".repeat(201), "id > /sdcard/x", "cat <(id)").forEach { assertFalse(it, RootShell.isSafe(it)) }
        assertFalse("запрещённая команда не доходит до su", RootShell.run("id; reboot").ok)
    }
}
