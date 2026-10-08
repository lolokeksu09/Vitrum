package app.rayclient

import java.io.File

/**
 * Осиротевший Xray: если систему убила приложение, дочерний процесс Xray может остаться жить с копией TUN-дескриптора
 * (родителем у него становится init). Приложение при этом показывает «Отключено», а новое подключение запустило бы второй Xray.
 * Здесь такие процессы находятся в /proc (по командной строке `<путь к libxray.so> run -c ...` и по родителю не-нашему) и завершаются.
 * Работает только с процессами того же пользователя; если система скрывает чужие процессы, просто ничего не находит.
 * Xray, который приложение запустило само (подключение, замер), имеет родителем наш процесс и не трогается.
 */
object XrayOrphans {
    /** Аргументы из /proc/<pid>/cmdline (разделитель NUL). */
    fun parseCmdline(bytes: ByteArray): List<String> = String(bytes, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }

    /** Наш ли это Xray: первый аргумент — путь к нашему libxray.so, дальше `run` и `-c`. */
    fun isOurXray(args: List<String>, exePath: String): Boolean = args.size >= 3 && args[0] == exePath && args[1] == "run" && "-c" in args

    /** PPid из /proc/<pid>/stat: «pid (имя) состояние ppid …»; имя может содержать пробелы и скобки, поэтому разбор после последней «)». */
    fun parsePpid(stat: String): Int? = stat.substringAfterLast(')', "").trim().split(' ').getOrNull(1)?.toIntOrNull()

    // readNBytes есть только с Android 13, поэтому читаем сами
    private fun readHead(f: File, limit: Int = 4096): ByteArray? = runCatching {
        f.inputStream().use { ins ->
            val buf = ByteArray(limit); var n = 0
            while (n < limit) { val k = ins.read(buf, n, limit - n); if (k < 0) break; n += k }
            buf.copyOf(n)
        }
    }.getOrNull()

    /** PID осиротевших Xray: наш libxray.so и родитель не [selfPid]. */
    fun find(exePath: String, selfPid: Int, proc: File = File("/proc")): List<Int> =
        proc.listFiles()?.mapNotNull { d ->
            val pid = d.name.toIntOrNull() ?: return@mapNotNull null
            if (pid == selfPid) return@mapNotNull null
            val args = parseCmdline(readHead(File(d, "cmdline")) ?: return@mapNotNull null)
            if (!isOurXray(args, exePath)) return@mapNotNull null
            val ppid = parsePpid(readHead(File(d, "stat"))?.let { String(it, Charsets.UTF_8) } ?: return@mapNotNull null)
            if (ppid == null || ppid == selfPid) null else pid
        }.orEmpty()

    /** Завершает найденные процессы; возвращает их число. */
    fun kill(exePath: String): Int = find(exePath, android.os.Process.myPid()).onEach { runCatching { android.os.Process.killProcess(it) } }.size
}
