package app.rayclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.io.InputStream
import java.net.URL

class SpeedResult(val bytes: Long, val ms: Long, val ttfbMs: Long, val note: String) {
    val mbps: Double get() = SpeedTest.mbps(bytes, ms)
    val ok: Boolean get() = note.isEmpty() && bytes > 0
}

/**
 * Тест скорости загрузки через VPN: скачивает тестовый файл через служебный HTTP-прокси Xray (unix-сокет, как проверка IP и пинг «HEAD VPN») и считает Мбит/с.
 * Запускается только по кнопке. Качает не больше MAX_BYTES и не дольше заданного времени. Работает по http (как остальные проверки через прокси).
 */
object SpeedTest {
    const val DEFAULT_URL = "http://speedtest.tele2.net/10MB.zip"
    const val MAX_BYTES = 20L * 1024 * 1024
    var last by mutableStateOf<SpeedResult?>(null)

    /** Сколько можно скачать на сервер при проверке всех серверов (МБ) и на сколько секунд: на мобильной сети это ещё и расход трафика. */
    const val ALL_MAX_BYTES = 3L * 1024 * 1024
    const val ALL_SECONDS = 6

    /** Результаты по id сервера от быстрого к медленному; серверы без результата или с ошибкой в список не входят. */
    fun rank(results: Map<String, SpeedResult>): List<Pair<String, SpeedResult>> =
        results.filter { it.value.ok }.toList().sortedByDescending { it.second.mbps }

    fun fastestId(results: Map<String, SpeedResult>): String? = rank(results).firstOrNull()?.first

    fun mbps(bytes: Long, ms: Long): Double = if (ms <= 0 || bytes <= 0) 0.0 else bytes * 8.0 / 1_000_000.0 / (ms / 1000.0)

    /** Заголовки ответа до пустой строки; null — соединение закрыто раньше или заголовки слишком длинные. */
    internal fun readHeaders(input: InputStream): List<String>? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = input.read(); if (b < 0) return null
            sb.append(b.toChar())
            if (sb.endsWith("\r\n\r\n")) return sb.toString().trimEnd().split("\r\n")
        }
        return null
    }

    internal fun statusCode(line: String): Int? = if (line.startsWith("HTTP/")) line.split(' ').getOrNull(1)?.toIntOrNull() else null

    fun measure(connector: Connector, url: String, seconds: Int = 8, maxBytes: Long = MAX_BYTES): SpeedResult {
        val u = runCatching { URL(HeadProbe.plain(url.trim())) }.getOrNull() ?: return SpeedResult(0, 0, 0, "неверный адрес")
        val hostPort = if (u.port > 0) "${u.host}:${u.port}" else u.host
        val t0 = System.nanoTime()
        val conn = try { connector.open(10_000) } catch (e: Exception) { return SpeedResult(0, 0, 0, HeadProbe.PROXY_UNAVAILABLE) }
        try {
            conn.output.write("GET http://$hostPort${u.file.ifEmpty { "/" }} HTTP/1.1\r\nHost: $hostPort\r\nUser-Agent: RayClient\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n".toByteArray())
            conn.output.flush()
            val headers = readHeaders(conn.input) ?: return SpeedResult(0, 0, 0, "сервер закрыл соединение")
            val code = statusCode(headers.first()) ?: return SpeedResult(0, 0, 0, "странный ответ")
            val ttfb = (System.nanoTime() - t0) / 1_000_000
            if (code in 300..399) return SpeedResult(0, 0, ttfb, "адрес перенаправляет (код $code): укажите прямую ссылку на файл по http")
            if (code !in 200..299) return SpeedResult(0, 0, ttfb, "сервер ответил кодом $code")
            val buf = ByteArray(64 * 1024); val start = System.nanoTime(); val deadline = start + seconds * 1_000_000_000L
            var total = 0L
            while (total < maxBytes && System.nanoTime() < deadline) {
                val n = try { conn.input.read(buf) } catch (e: IOException) { if (total > 0) break else return SpeedResult(0, 0, ttfb, "таймаут") }
                if (n < 0) break
                total += n
            }
            return SpeedResult(total, (System.nanoTime() - start) / 1_000_000, ttfb, if (total == 0L) "сервер не прислал данные" else "")
        } catch (e: IOException) { return SpeedResult(0, 0, 0, "сервер не ответил")
        } finally { runCatching { conn.close() } }
    }
}
