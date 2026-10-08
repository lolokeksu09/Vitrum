package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SpeedTestTest {
    private class FakeConn(response: ByteArray) : Conn {
        override val input: InputStream = ByteArrayInputStream(response)
        val sent = ByteArrayOutputStream()
        override val output: OutputStream = sent
        override fun close() {}
    }
    private fun reply(code: String, body: ByteArray = ByteArray(0)) = ("HTTP/1.1 $code\r\nContent-Length: ${body.size}\r\n\r\n").toByteArray() + body

    @Test fun mbpsMath() {
        assertEquals(8.0, SpeedTest.mbps(1_000_000, 1000), 1e-9); assertEquals(0.0, SpeedTest.mbps(0, 1000), 0.0); assertEquals(0.0, SpeedTest.mbps(100, 0), 0.0)
    }

    @Test fun countsBodyBytesAndSendsGetThroughProxy() {
        val c = FakeConn(reply("200 OK", ByteArray(300_000) { 1 }))
        val r = SpeedTest.measure({ c }, "https://example.com/file.bin")
        assertTrue(r.ok); assertEquals(300_000L, r.bytes)
        val req = c.sent.toString()
        assertTrue(req.startsWith("GET http://example.com/file.bin HTTP/1.1")); assertTrue("https понижается до http, как у остальных проверок через прокси", !req.contains("https://"))
    }

    @Test fun stopsAtMaxBytes() {
        val r = SpeedTest.measure({ FakeConn(reply("200 OK", ByteArray(500_000))) }, "http://example.com/x", maxBytes = 100_000)
        assertTrue(r.ok); assertTrue("прочитано не больше чем лимит плюс один блок", r.bytes in 100_000..100_000 + 64 * 1024)
    }

    @Test fun errorsHaveReadableNotes() {
        assertTrue(SpeedTest.measure({ FakeConn(reply("404 Not Found")) }, "http://e.com/x").note.contains("404"))
        assertTrue(SpeedTest.measure({ FakeConn(reply("302 Found")) }, "http://e.com/x").note.contains("перенаправляет"))
        assertEquals("сервер закрыл соединение", SpeedTest.measure({ FakeConn(ByteArray(0)) }, "http://e.com/x").note)
        assertEquals("сервер не прислал данные", SpeedTest.measure({ FakeConn(reply("200 OK")) }, "http://e.com/x").note)
        assertEquals(HeadProbe.PROXY_UNAVAILABLE, SpeedTest.measure({ throw IOException("нет") }, "http://e.com/x").note)
        assertEquals("неверный адрес", SpeedTest.measure({ FakeConn(ByteArray(0)) }, "не адрес").note)
    }

    @Test fun parsesStatus() { assertEquals(200, SpeedTest.statusCode("HTTP/1.1 200 OK")); assertNull(SpeedTest.statusCode("мусор")) }
}
