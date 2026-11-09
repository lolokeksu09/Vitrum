package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class XrayOrphansTest {
    private val exe = "/data/app/x/lib/arm64/libxray.so"

    @Test fun cmdlineAndPpidParsing() {
        assertEquals(listOf("a", "b c", "d"), XrayOrphans.parseCmdline("a\u0000b c\u0000d\u0000".toByteArray()))
        assertTrue(XrayOrphans.isOurXray(listOf(exe, "run", "-c", "/f/config.json"), exe))
        assertFalse(XrayOrphans.isOurXray(listOf("/other/libxray.so", "run", "-c", "x"), exe))
        assertFalse(XrayOrphans.isOurXray(listOf(exe, "version"), exe))
        assertEquals(1, XrayOrphans.parsePpid("1234 (libxray.so) S 1 1234 0 0"))
        assertEquals(777, XrayOrphans.parsePpid("1234 (we) ird) name) S 777 1 2"))
        assertNull(XrayOrphans.parsePpid("garbage"))
    }

    @Test fun onlyOrphansOfOurXrayAreFound() {
        val proc = java.nio.file.Files.createTempDirectory("proc").toFile()
        fun proc(pid: String, args: List<String>, ppid: Int) {
            File(proc, pid).mkdirs()
            File(proc, "$pid/cmdline").writeBytes((args.joinToString("\u0000") + "\u0000").toByteArray())
            File(proc, "$pid/stat").writeText("$pid (libxray.so) S $ppid $pid 0")
        }
        try {
            proc("100", listOf(exe, "run", "-c", "a.json"), 1)        // сирота: родитель init
            proc("101", listOf(exe, "run", "-c", "b.json"), 5000)      // наш живой Xray: родитель — приложение (5000)
            proc("102", listOf("/other/libxray.so", "run", "-c", "c"), 1)  // чужой Xray
            proc("5000", listOf("app.rayclient"), 1)                   // само приложение
            File(proc, "self").mkdirs(); File(proc, "notapid.txt").writeText("x")
            assertEquals(listOf(100), XrayOrphans.find(exe, selfPid = 5000, proc = proc))
        } finally { proc.deleteRecursively() }
    }
}
