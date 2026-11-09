package app.rayclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.RandomAccessFile

/** Счётчик для перерисовки журнала. */
object ConnLogUi { var version by mutableIntStateOf(0) }

class ParsedConn(val host: String, val port: Int, val proto: String, val out: Int, val viaRule: Boolean)

/** Запись журнала. out: 0 через VPN, 1 напрямую, 2 заблокировано. */
class ConnEntry(val host: String, val isIp: Boolean, val port: Int, val proto: String, val out: Int, var count: Int, var last: Long, var viaRule: Boolean) {
    val key: String get() = "$host|$out"
}

/**
 * Журнал соединений: разбирает access-лог Xray и держит последние адреса в памяти. Сам Xray пишет access-лог во временный файл в filesDir
 * (обрезается на 2 МБ и стирается при отключении). Какое приложение сделало запрос, Xray не сообщает, поэтому журнал по адресам, а не по программам.
 */
object ConnLog {
    private const val MAX = 400
    private val entries = LinkedHashMap<String, ConnEntry>()
    private var reader: Thread? = null
    private val re = Regex("""accepted\s+(\S+)\s+\[([^\]\s]+)\s+(->|>>)\s+([^\]\s]+)\]""")
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** Строка access-лога → соединение. Скрывает служебные входы (замеры, DNS самого Xray). Стрелка «->» — сработало правило, «>>» — выход по умолчанию. */
    fun parse(line: String): ParsedConn? {
        val m = re.find(line) ?: return null
        val inbound = m.groupValues[2]; val out = m.groupValues[4]
        if (inbound == "probe-in" || inbound == "dns-internal" || out == "dns-out") return null
        var tgt = m.groupValues[1]
        var proto = "tcp"
        for (p in listOf("tcp:", "udp:")) if (tgt.startsWith(p)) { proto = p.dropLast(1); tgt = tgt.removePrefix(p) }
        tgt = tgt.removePrefix("https://").removePrefix("http://").removePrefix("//")
        val i = tgt.lastIndexOf(':'); if (i <= 0) return null
        val port = tgt.substring(i + 1).toIntOrNull() ?: return null
        val host = tgt.substring(0, i).removePrefix("[").removeSuffix("]")
        if (host.isEmpty()) return null
        val o = when (out) { "proxy" -> 0; "direct" -> 1; "block" -> 2; else -> return null }
        return ParsedConn(host, port, proto, o, m.groupValues[3] == "->")
    }

    fun isIp(host: String) = host.contains(':') || ipv4.matches(host)

    @Synchronized fun add(p: ParsedConn, now: Long = System.currentTimeMillis()) {
        val key = "${p.host}|${p.out}"
        val e = entries.remove(key)?.also { it.count++; it.last = now; it.viaRule = p.viaRule }
            ?: ConnEntry(p.host, isIp(p.host), p.port, p.proto, p.out, 1, now, p.viaRule)
        entries[key] = e   // последний по времени — в конце
        while (entries.size > MAX) entries.remove(entries.keys.first())
    }

    fun feed(line: String) { parse(line)?.let { add(it) } }

    /** Основной домен («rr1.sn-x.googlevideo.com» → «googlevideo.com»); для двухуровневых зон вроде co.uk берётся три метки. */
    fun baseDomain(host: String): String {
        if (isIp(host)) return host
        val l = host.split('.'); if (l.size <= 2) return host
        val sld = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        return if (l.last().length == 2 && l[l.size - 2] in sld && l.size >= 3) l.takeLast(3).joinToString(".") else l.takeLast(2).joinToString(".")
    }

    /** Свежие записи сверху. group = объединить поддомены по основному домену. */
    @Synchronized fun snapshot(group: Boolean): List<ConnEntry> {
        val src = entries.values.toList()
        if (!group) return src.sortedByDescending { it.last }
        val m = LinkedHashMap<String, ConnEntry>()
        for (e in src) {
            val b = if (e.isIp) e.host else baseDomain(e.host); val k = "$b|${e.out}"
            val x = m[k]
            if (x == null) m[k] = ConnEntry(b, e.isIp, e.port, e.proto, e.out, e.count, e.last, e.viaRule)
            else { x.count += e.count; if (e.last > x.last) { x.last = e.last; x.viaRule = e.viaRule } }
        }
        return m.values.sortedByDescending { it.last }
    }

    @Synchronized fun totals(): IntArray { val t = IntArray(3); entries.values.forEach { t[it.out] += it.count }; return t }
    @Synchronized fun clear() { entries.clear(); ConnLogUi.version++ }

    /** Читает новые строки access-лога (раз в 0,8 с) и обрезает файл, когда он вырастет. */
    fun startReader(file: File) {
        stopReader()
        val t = Thread {
            var pos = 0L
            try {
                while (!Thread.currentThread().isInterrupted) {
                    if (file.exists()) {
                        var got = false
                        RandomAccessFile(file, "r").use { raf ->
                            val len = raf.length()
                            if (len < pos) pos = 0
                            if (len > pos) {
                                val buf = ByteArray((len - pos).coerceAtMost(1_000_000L).toInt())
                                raf.seek(pos); raf.readFully(buf)
                                val text = String(buf, Charsets.ISO_8859_1); val nl = text.lastIndexOf('\n')
                                if (nl >= 0) { text.substring(0, nl).lineSequence().forEach { feed(it) }; pos += nl + 1; got = true }
                            }
                        }
                        if (got) ConnLogUi.version++
                        if (file.length() > 2_000_000L) { runCatching { RandomAccessFile(file, "rw").use { it.setLength(0) } }; pos = 0 }
                    }
                    Thread.sleep(800)
                }
            } catch (_: InterruptedException) {}
        }
        t.isDaemon = true; reader = t; t.start()
    }

    fun stopReader() { reader?.interrupt(); reader = null }
}
