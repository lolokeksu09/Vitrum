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
        if (!V6.matches(a) || a.count { it == ':' } < 2 || ":::" in a ||
            a.indexOf("::") != a.lastIndexOf("::")) return false
        val compressed = "::" in a
        if (a.startsWith(":") && !a.startsWith("::")) return false
        if (a.endsWith(":") && !a.endsWith("::")) return false
        val groups = a.split(':').filter { it.isNotEmpty() }
        if (groups.count { '.' in it } > 1) return false
        var units = 0
        for ((index, group) in groups.withIndex()) {
            if ('.' in group) {
                if (index != groups.lastIndex || !a.endsWith(group) || !v4(group)) return false
                units += 2
            } else {
                if (group.length !in 1..4 || group.toIntOrNull(16) == null) return false
                units++
            }
        }
        return if (compressed) units < 8 else units == 8
    }

    /**
     * Parse an already-validated IPv6 literal into exactly 16 bytes.
     * This avoids hostname resolution and preserves IPv4-mapped IPv6 as IPv6.
     */
    internal fun ipv6Bytes(address: String): ByteArray? {
        if (!v6(address)) return null
        val marker = address.indexOf("::")
        val leftText = if (marker >= 0) address.substring(0, marker) else address
        val rightText = if (marker >= 0) address.substring(marker + 2) else ""
        val left = ipv6Words(leftText, address) ?: return null
        val right = if (marker >= 0) ipv6Words(rightText, address) ?: return null else emptyList()
        val explicit = left.size + right.size
        val zeroGroups = if (marker >= 0) 8 - explicit else 0
        if ((marker >= 0 && zeroGroups < 1) || (marker < 0 && explicit != 8)) return null
        val words = left + List(zeroGroups) { 0 } + right
        if (words.size != 8) return null
        return ByteArray(16).also { bytes ->
            words.forEachIndexed { index, word ->
                bytes[index * 2] = (word shr 8).toByte()
                bytes[index * 2 + 1] = word.toByte()
            }
        }
    }

    private fun ipv6Words(part: String, whole: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val chunks = part.split(':')
        val words = mutableListOf<Int>()
        for ((index, chunk) in chunks.withIndex()) {
            if ('.' in chunk) {
                if (index != chunks.lastIndex || !whole.endsWith(chunk) || !v4(chunk)) return null
                val octets = chunk.split('.').map { it.toIntOrNull() ?: return null }
                words += (octets[0] shl 8) or octets[1]
                words += (octets[2] shl 8) or octets[3]
            } else {
                if (chunk.length !in 1..4) return null
                words += chunk.toIntOrNull(16) ?: return null
            }
        }
        return words
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
