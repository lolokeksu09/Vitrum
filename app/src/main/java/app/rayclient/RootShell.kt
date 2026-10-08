package app.rayclient

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Вызов su для необязательных root-функций. Только фиксированные команды: строка проходит проверку на допустимые символы
 * (никаких кавычек, `;`, `|`, `$`, `&`, переводов строки), у вызова есть таймаут, вывод ограничен по размеру.
 * Без root всё остальное в приложении работает как раньше; su вызывается только по кнопке пользователя.
 */
object RootShell {
    class Result(val ok: Boolean, val code: Int, val out: String, val err: String)

    private const val MAX_BYTES = 512 * 1024
    private val SAFE = Regex("^[A-Za-z0-9 _./:=,+-]{1,200}$")

    fun isSafe(cmd: String): Boolean = SAFE.matches(cmd)

    private class Drain(stream: InputStream) {
        val buf = ByteArrayOutputStream()
        val thread = Thread {
            runCatching {
                val chunk = ByteArray(8192)
                while (true) {
                    val n = stream.read(chunk); if (n < 0) break
                    if (buf.size() < MAX_BYTES) buf.write(chunk, 0, minOf(n, MAX_BYTES - buf.size()))
                }
            }
        }.apply { isDaemon = true; start() }
        fun text(): String { thread.join(500); return buf.toString(Charsets.UTF_8.name()) }
    }

    fun run(cmd: String, timeoutMs: Long = 5000): Result {
        if (!isSafe(cmd)) return Result(false, -3, "", "команда не прошла проверку")
        val p = try { ProcessBuilder("su", "-c", cmd).start() } catch (e: Exception) { return Result(false, -1, "", "su недоступен") }
        val out = Drain(p.inputStream); val err = Drain(p.errorStream)
        val done = try { p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        if (!done) { p.destroyForcibly(); return Result(false, -2, out.text(), "таймаут") }
        return Result(p.exitValue() == 0, p.exitValue(), out.text(), err.text())
    }

    /** Есть ли root: `id -u` должен вернуть 0. Первый вызов может показать запрос менеджера root. */
    fun available(): Boolean = run("id -u", 8000).let { it.ok && it.out.trim() == "0" }

    /** Проверка root по кнопке в настройках; результат кладётся в AppState.rootInfo. */
    fun checkAsync() {
        if (AppState.rootChecking) return
        AppState.rootChecking = true
        Thread { try { AppState.rootInfo = available() } finally { AppState.rootChecking = false } }.start()
    }
}
