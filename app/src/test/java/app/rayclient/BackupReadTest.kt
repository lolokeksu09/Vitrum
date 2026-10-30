package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BackupReadTest {
    @Test fun limitIsInclusive() {
        assertEquals("hello", Backup.readLimited(ByteArrayInputStream("hello".toByteArray()), 5))
        assertNull(Backup.readLimited(ByteArrayInputStream("hello!".toByteArray()), 5))
        assertEquals("", Backup.readLimited(ByteArrayInputStream(byteArrayOf()), 0))
    }

    @Test fun oversizedStreamIsNotFullyConsumed() {
        var consumed = 0
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 'A'.code }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'A'.code.toByte())
                consumed += len
                return len
            }
        }
        assertNull(Backup.readLimited(stream, 16))
        assertEquals(17, consumed)
    }
}
