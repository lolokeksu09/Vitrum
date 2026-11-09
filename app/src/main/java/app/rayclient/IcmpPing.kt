package app.rayclient

import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Режим «ICMP» в замере задержки. Раньше был `InetAddress.isReachable`: без root он может не слать настоящий ICMP.
 * Теперь сначала системная команда `ping -c 1 -W 3 адрес` от имени приложения (root не нужен), время берётся из её ответа.
 * Если команду запустить нельзя (прошивка не даёт приложению ping или ICMP-сокет), при включённых «Root-функциях» та же команда идёт через su;
 * иначе остаётся прежняя проверка `isReachable`. Команда и флаги `ping` на устройствах не проверялись.
 */
object IcmpPing {
    sealed class Result {
        class Ok(val ms: Int) : Result()
        object NoReply : Result()
        object Unavailable : Result()
    }

    private val TIME = Regex("time\\s*[=<]\\s*([0-9]+(?:[.,][0-9]+)?)\\s*ms", RegexOption.IGNORE_CASE)
    private val CANNOT = Regex("permission|not permitted|denied|not found|no such|cannot|can't|inaccessible|unknown option|usage", RegexOption.IGNORE_CASE)

    /** Команда для su: адрес проходит ту же проверку символов, что и любая root-команда. Иначе null. */
    fun command(ip: String): String? = "ping -c 1 -W 3 $ip".takeIf { ip.isNotEmpty() && RootShell.isSafe(it) && ip.none { c -> c.isWhitespace() } }

    /** Время ответа в мс из вывода `ping`, не меньше 1; null, если ответа нет. */
    fun parseTime(out: String): Int? {
        val v = TIME.find(out)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return Math.ceil(v).toInt().coerceAtLeast(1)
    }

    /** Разбор готового вывода: ответ есть / ответа нет / команду выполнить не удалось. */
    fun classify(out: String, exited: Boolean): Result {
        parseTime(out)?.let { return Result.Ok(it) }
        return if (!exited || CANNOT.containsMatchIn(out)) Result.Unavailable else Result.NoReply
    }

    private fun viaApp(ip: String): Result = try {
        val p = ProcessBuilder("ping", "-c", "1", "-W", "3", ip).redirectErrorStream(true).start()
        val text = StringBuilder()
        val reader = Thread { runCatching { p.inputStream.bufferedReader().useLines { ls -> ls.take(50).forEach { text.append(it).append('\n') } } } }.apply { isDaemon = true; start() }
        val done = p.waitFor(6, TimeUnit.SECONDS)
        if (!done) p.destroyForcibly()
        reader.join(500)
        classify(text.toString(), done)
    } catch (_: Exception) { Result.Unavailable }

    private fun viaRoot(ip: String): Result {
        val cmd = command(ip) ?: return Result.Unavailable
        val r = RootShell.run(cmd, 6000)
        return classify(r.out + "\n" + r.err, r.code != -2)
    }

    /** Задержка в мс или -1. [rootReady] вызывается только когда обычный ping не запустился: он проверяет «Root-функции» и сам root (один раз на замер). */
    fun ping(addr: InetAddress, rootReady: () -> Boolean): Int {
        val ip = addr.hostAddress ?: return -1
        var r = viaApp(ip)
        if (r is Result.Unavailable && rootReady()) r = viaRoot(ip)
        return when (r) {
            is Result.Ok -> r.ms
            Result.NoReply -> -1
            Result.Unavailable -> {
                val t0 = System.nanoTime()
                if (addr.isReachable(3000)) ((System.nanoTime() - t0) / 1_000_000).toInt().coerceAtLeast(1) else -1
            }
        }
    }
}
