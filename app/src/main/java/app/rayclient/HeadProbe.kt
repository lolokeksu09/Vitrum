package app.rayclient

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.URL

class HeadResult(val ms: Int, val note: String, val body: String = "", val detail: String = "")

interface Conn : Closeable { val input: InputStream; val output: OutputStream }
fun interface Connector { @Throws(IOException::class) fun open(timeoutMs: Int): Conn }

/** HEAD/GET через HTTP-прокси Xray на unix-сокете. Адрес сайта целиком уходит на сервер, на телефоне имя не резолвится. Только http://. */
object HeadProbe {
    const val PROXY_UNAVAILABLE = "локальный прокси недоступен"
    fun plain(url: String) = if (url.startsWith("https://")) "http://" + url.removePrefix("https://") else url

    private fun once(url: String, connector: Connector, timeout: Int, method: String): HeadResult {
        val u = URL(plain(url)); val host = u.host
        val hostPort = if (u.port > 0) "$host:${u.port}" else host
        val path = u.file.ifEmpty { "/" }
        val t0 = System.nanoTime()
        val conn = try { connector.open(timeout) } catch (e: Exception) { return HeadResult(-1, PROXY_UNAVAILABLE, detail = "${e.javaClass.simpleName}: ${e.message}") }
        try {
            conn.output.write("$method http://$hostPort$path HTTP/1.1\r\nHost: $hostPort\r\nUser-Agent: RayClient\r\nConnection: close\r\n\r\n".toByteArray())
            conn.output.flush()
            val rd = conn.input.bufferedReader()
            val line = rd.readLine() ?: return HeadResult(-1, "сервер закрыл соединение")
            if (!line.startsWith("HTTP/")) return HeadResult(-1, "странный ответ")
            val code = line.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            if (code == 0 || code >= 500) return HeadResult(-1, "сервер ответил кодом $code")
            val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
            var body = ""
            if (method == "GET") { while (true) { val l = rd.readLine() ?: break; if (l.isEmpty()) break }; val buf = CharArray(400); var n = 0; while (n < buf.size) { val k = rd.read(buf, n, buf.size - n); if (k < 0) break; n += k }; body = String(buf, 0, n) }
            return HeadResult(ms, "", body)
        } catch (e: SocketTimeoutException) { return HeadResult(-1, "таймаут")
        } catch (e: EOFException) { return HeadResult(-1, "сервер закрыл соединение")
        } catch (e: IOException) {
            // На Android таймаут чтения из LocalSocket приходит как IOException «Try again» (EAGAIN), а не SocketTimeoutException.
            val m = (e.message ?: "").lowercase()
            return HeadResult(-1, if ("try again" in m || "eagain" in m || "time" in m) "таймаут" else "сервер не ответил", detail = "${e.javaClass.simpleName}: ${e.message}")
        } catch (e: Exception) { return HeadResult(-1, e.javaClass.simpleName)
        } finally { runCatching { conn.close() } }
    }

    /** Первый удачный запрос прогревает соединение, время берём из второго. Повтор только после таймаута. */
    fun head(url: String, connector: Connector, timeout: Int = 8000, retry: Boolean = true): HeadResult {
        var last = HeadResult(-1, "нет ответа"); var warm: HeadResult? = null
        for (k in 0 until 2) {
            if (warm == null && (k == 0 || (retry && last.note == "таймаут"))) { val r = once(url, connector, timeout, "HEAD"); if (r.ms >= 0) warm = r else last = r }
        }
        val w = warm ?: return last
        if (!retry) return w
        val m = once(url, connector, timeout, "HEAD")
        return if (m.ms >= 0) m else w
    }

    /** GET с телом ответа (проверка внешнего IP). */
    fun get(url: String, connector: Connector): HeadResult = once(url, connector, 8000, "GET")

    /** Серверы с одним IP (у «мостов» он общий) проверяются по очереди, остальные группы параллельно. */
    fun runAll(ips: List<String>, connectors: List<Connector>, url: String, workers: Int = 6, timeout: Int = 8000, retry: Boolean = true, onResult: (Int, HeadResult) -> Unit) {
        val groups = ips.indices.groupBy { ips[it] }.values.toList()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        try { pool.invokeAll(groups.map { g -> java.util.concurrent.Callable { g.forEach { i -> onResult(i, head(url, connectors[i], timeout, retry)) } } }) }
        finally { pool.shutdown() }
    }
}
