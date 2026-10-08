package app.rayclient

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.SocketAddress
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedChannelException
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

/** Подключение к unix-сокету через NIO (в приложении то же делает LocalSocket). Таймаут — сторожевой поток. */
class NioConn(path: String, timeoutMs: Int) : Conn {
    private val ch = SocketChannel.open(Class.forName("java.net.UnixDomainSocketAddress").getMethod("of", String::class.java).invoke(null, path) as SocketAddress)
    private val dog = Thread { try { Thread.sleep(timeoutMs.toLong()); ch.close() } catch (_: InterruptedException) {} }.apply { isDaemon = true; start() }
    override val input: InputStream = object : InputStream() {
        private val inner = Channels.newInputStream(ch)
        private fun <T> g(f: () -> T): T = try { f() } catch (e: AsynchronousCloseException) { throw SocketTimeoutException("watchdog") } catch (e: ClosedChannelException) { throw SocketTimeoutException("watchdog") }
        override fun read() = g { inner.read() }
        override fun read(b: ByteArray, o: Int, l: Int) = g { inner.read(b, o, l) }
    }
    override val output: OutputStream = Channels.newOutputStream(ch)
    override fun close() { dog.interrupt(); runCatching { ch.close() } }
}

class HeadProbeTest {
    @Test fun head() {
        val src = File(System.getProperty("user.home"), "links.txt")
        assumeTrue("нет ~/links.txt (ссылки подписки): тест пропущен, а не пройден", src.exists())
        val links = src.readLines().filter { it.contains("://") }
        val dir = File("/tmp/uxprobe").apply { deleteRecursively(); mkdirs() }
        val items = links.map { Links.parse(it) }
        val cfg = File(dir, "ping.json").apply { writeText(ConfigBuilder.buildProbe(items.map { it to it.host }, dir.absolutePath)) }
        val pb = ProcessBuilder(File(System.getProperty("user.home"), "dl/xl/xray").absolutePath, "run", "-c", cfg.absolutePath).redirectErrorStream(true).redirectOutput(File(dir, "xray.log"))
        pb.environment()["XRAY_LOCATION_ASSET"] = File(System.getProperty("user.home"), "dl/xray").absolutePath
        val p = pb.start()
        val res = arrayOfNulls<HeadResult>(links.size)
        val t0 = System.currentTimeMillis()
        val out = StringBuilder()
        try {
            var tries = 0; while (!File(dir, "p0.sock").exists() && tries++ < 60) Thread.sleep(100)
            out.append("socket created: ${File(dir, "p0.sock").exists()}\n")
            val conns = links.indices.map { i -> Connector { t -> NioConn(File(dir, "p$i.sock").absolutePath, t) } }
            HeadProbe.runAll(items.map { it.host }, conns, "http://cp.cloudflare.com/generate_204") { i, r -> res[i] = r }
            for (i in listOf(4, 5, 8)) {
                val http = Series_.run(conns[i], Target("cp.cloudflare.com", 80, "/generate_204"), 8, 4000, 100)
                val sl = GameStats.live(http.rtts)
                out.append("series http #$i: connect=${http.connectMs} rtts=${http.rtts} avg=${sl.avg} jitter=${sl.jitter} loss=${sl.loss}% note=${http.note}\n")
            }
            val dead = Series_.run(conns[0], Target("cp.cloudflare.com", 80, "/generate_204"), 3, 3000, 100)
            out.append("series dead #0: connect=${dead.connectMs} rtts=${dead.rtts} note=${dead.note}\n")
            val g = HeadProbe.get("http://api.ipify.org", conns[4])
            out.append("GET ipify via #4: ms=${g.ms} note=${g.note} body=${g.body.replace("\r\n", "|").take(60)}\n")
        } finally { p.destroy() }
        out.append("elapsed_s=${(System.currentTimeMillis() - t0) / 1000}\n")
        res.forEachIndexed { i, r -> out.append("$i ${r?.ms} ${r?.note}\n") }
        File("/tmp/vitrum-test/cfgtest11").mkdirs()
        File("/tmp/vitrum-test/cfgtest11/head-all.txt").writeText(out.toString())
    }
}
