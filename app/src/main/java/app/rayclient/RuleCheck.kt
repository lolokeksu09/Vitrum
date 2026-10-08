package app.rayclient

import java.io.File
import java.io.RandomAccessFile

/**
 * Проверка записи правила до сохранения: с неверным адресом или портом Xray не запустится, а у работающего VPN перезапуск после смены настроек
 * закончился бы ошибкой. Метки geoip/geosite проверяет [GeoTags], здесь только формат остальных записей.
 */
object RuleCheck {
    private val DOMAIN = Regex("""^[\p{L}\p{N}._*\-]+$""")
    private val V4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val V6 = Regex("""^[0-9A-Fa-f:.]+$""")

    fun tokens(s: String) = s.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun isIpv4(a: String) = v4(a)
    fun isIpv6(a: String) = v6(a)

    private fun v4(a: String) = V4.matchEntire(a)?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true

    private fun v6(a: String): Boolean {
        if (!V6.matches(a) || a.count { it == ':' } < 2 || ":::" in a || a.indexOf("::") != a.lastIndexOf("::")) return false
        val groups = a.split(':').filter { it.isNotEmpty() }
        if (groups.size > 8) return false
        return groups.withIndex().all { (i, g) -> if ('.' in g) i == groups.lastIndex && v4(g) else g.length <= 4 }
    }

    private fun ip(t: String): Boolean {
        if (t.startsWith("geoip:") || t.startsWith("ext:")) return t.substringAfter(':').isNotEmpty()
        val addr = t.substringBefore('/'); val mask = if ('/' in t) t.substringAfter('/').toIntOrNull() ?: return false else null
        return when {
            v4(addr) -> mask == null || mask in 0..32
            v6(addr) -> mask == null || mask in 0..128
            else -> false
        }
    }

    private fun port(t: String): Boolean {
        val p = t.split('-')
        if (p.size > 2 || p.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 5 }) return false
        val n = p.map { it.toInt() }
        return n.all { it in 1..65535 } && (n.size == 1 || n[0] <= n[1])
    }

    private fun domain(t: String): Boolean {
        for (p in listOf("geosite:", "ext:", "regexp:")) if (t.startsWith(p)) return t.length > p.length
        val v = listOf("domain:", "full:", "keyword:").firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: t
        return v.isNotEmpty() && DOMAIN.matches(v)
    }

    private val BROAD_RE = setOf(".*", ".+", "^.*$", "^.+$", ".", "^.*", "^.+")

    /** Слишком широкая запись для правила «напрямую» из чужой подписки: весь IPv4 или большая его часть, весь IPv6, любое выражение, домен верхнего уровня. */
    fun tooBroad(kind: Int, token: String): Boolean = when (kind) {
        1 -> if (token.startsWith("geoip:") || token.startsWith("ext:") || '/' !in token) false else {
            val addr = token.substringBefore('/'); val mask = token.substringAfter('/').toIntOrNull()
            mask != null && ((isIpv4(addr) && mask < 8) || (isIpv6(addr) && mask < 16))
        }
        0 -> when {
            token.startsWith("geosite:") || token.startsWith("ext:") -> false
            token.startsWith("regexp:") -> token.removePrefix("regexp:") in BROAD_RE
            token.startsWith("keyword:") -> token.removePrefix("keyword:").length < 3
            else -> '.' !in token.removePrefix("domain:").removePrefix("full:")
        }
        else -> false
    }

    /** Записи, которые не разобрались (kind: 0 домены, 1 IP, 2 порты). Пусто — запись годится. */
    fun invalid(kind: Int, value: String): List<String> = tokens(value).filter { !when (kind) { 1 -> ip(it); 2 -> port(it); else -> domain(it) } }
}

/** Хвост файла журнала без чтения всего файла целиком (журнал на уровне debug бывает очень большим). */
object LogTail {
    fun read(f: File, chars: Int = 3000): String = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val len = r.length(); val start = maxOf(0L, len - chars * 4L)
            r.seek(start); val b = ByteArray((len - start).toInt()); r.readFully(b)
            String(b, Charsets.UTF_8).takeLast(chars)
        }
    }.getOrDefault("")
}
