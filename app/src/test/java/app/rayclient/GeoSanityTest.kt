package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream

class GeoSanityTest {
    private fun varint(o: ByteArrayOutputStream, v0: Long) { var v = v0; while (v >= 0x80) { o.write(((v and 0x7f) or 0x80).toInt()); v = v shr 7 }; o.write(v.toInt()) }
    private fun field(o: ByteArrayOutputStream, f: Int, data: ByteArray) { varint(o, (f.toLong() shl 3) or 2); varint(o, data.size.toLong()); o.write(data) }
    private fun cidr(ip: String, prefix: Int): ByteArray {
        val o = ByteArrayOutputStream(); field(o, 1, java.net.InetAddress.getByName(ip).address); varint(o, (2L shl 3) or 0); varint(o, prefix.toLong()); return o.toByteArray()
    }
    private fun entry(code: String, vararg c: Pair<String, Int>): ByteArray {
        val e = ByteArrayOutputStream(); field(e, 1, code.toByteArray()); c.forEach { field(e, 2, cidr(it.first, it.second)) }
        val o = ByteArrayOutputStream(); field(o, 1, e.toByteArray()); return o.toByteArray()
    }
    /** Файл с достаточным числом тестовых меток и заданными ключевыми. */
    private fun dat(private_: List<Pair<String, Int>> = listOf("10.0.0.0" to 8, "192.168.0.0" to 16, "fc00::" to 7),
                    ru: List<Pair<String, Int>> = listOf("5.8.0.0" to 16, "2a00::" to 29), extra: List<ByteArray> = emptyList()): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(entry("PRIVATE", *private_.toTypedArray())); o.write(entry("RU", *ru.toTypedArray()))
        for (i in 0 until 60) o.write(entry("x$i", "203.0.113.${i}" to 32))
        extra.forEach { o.write(it) }
        return o.toByteArray()
    }

    @Test fun normalFileIsAccepted() { assertNull(GeoSanity.check(dat())) }

    @Test fun privateOutsideReservedRangesIsRejected() {
        assertNotNull(GeoSanity.check(dat(private_ = listOf("0.0.0.0" to 0))))
        assertNotNull(GeoSanity.check(dat(private_ = listOf("10.0.0.0" to 8, "8.8.8.0" to 24))))
        assertNotNull(GeoSanity.check(dat(private_ = listOf("::" to 0))))
        assertNull("часть зарезервированного диапазона допустима", GeoSanity.check(dat(private_ = listOf("10.1.0.0" to 16))))
    }

    @Test fun broadOrHugeRuIsRejected() {
        assertTrue(GeoSanity.check(dat(ru = listOf("0.0.0.0" to 0)))!!.contains("ru"))
        assertTrue(GeoSanity.check(dat(ru = listOf("2.0.0.0" to 7)))!!.contains("ru"))
        assertTrue(GeoSanity.check(dat(ru = listOf("2000::" to 3)))!!.contains("ru"))
        val many = (1..8).map { "${20 + it}.0.0.0" to 8 }   // 8 x /8 = 3,1% адресов
        assertTrue(GeoSanity.check(dat(ru = many))!!.contains("слишком большую долю"))
        assertNull("США и другие страны могут быть любыми", GeoSanity.check(dat(extra = listOf(entry("us", "3.0.0.0" to 7, "4.0.0.0" to 6)))))
    }

    @Test fun missingTagsAndBrokenFilesAreRejected() {
        val o = ByteArrayOutputStream(); for (i in 0 until 60) o.write(entry("x$i", "203.0.113.${i}" to 32))
        assertEquals("нет меток private или ru", GeoSanity.check(o.toByteArray()))
        assertEquals("файл не разобрался", GeoSanity.check(ByteArray(0)))
        val d = dat(); assertEquals("файл не разобрался", GeoSanity.check(d.copyOf(d.size - 3)))
    }

    @Test fun bundledDatabasePasses() {
        val f = File("src/main/assets/geoip.dat")
        if (f.isFile) assertNull("встроенная база должна проходить проверку", GeoSanity.check(f.readBytes()))
    }
}
