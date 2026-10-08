package app.rayclient

import java.io.File

/**
 * Метки `geoip:xx` и `geosite:xx` из баз Xray (geoip.dat, geosite.dat). Если правило ссылается на метку, которой в базе нет,
 * Xray не запускается; поэтому такие метки проверяются при добавлении правила и пропускаются при сборке конфигурации.
 * В файле подряд идут записи; у каждой записи первое поле (номер 1) — название метки. Остальное содержимое записей не читается.
 */
object GeoTags {
    class Known(val geoip: Set<String>, val geosite: Set<String>)
    class Sanitized(val rule: Rule?, val dropped: List<String>)

    private fun varint(b: ByteArray, pos: IntArray): Long? {
        var v = 0L
        for (shift in 0 until 64 step 7) {
            if (pos[0] >= b.size) return null
            val x = b[pos[0]++].toInt() and 0xFF
            v = v or ((x and 0x7F).toLong() shl shift)
            if (x < 0x80) return v
        }
        return null
    }

    /** Названия меток файла `.dat` в нижнем регистре. Битый или обрезанный файл даёт то, что успели прочитать. */
    fun parse(data: ByteArray): Set<String> {
        val tags = HashSet<String>()
        val p = intArrayOf(0)
        while (p[0] < data.size) {
            varint(data, p) ?: break
            val len = varint(data, p) ?: break
            val end = minOf(data.size.toLong(), p[0] + len).toInt()
            val q = intArrayOf(p[0])
            if (varint(data, q) != null) {
                val n = varint(data, q)
                if (n != null && n >= 0 && q[0] + n <= end) tags += String(data, q[0], n.toInt(), Charsets.UTF_8).lowercase()
            }
            p[0] = end
        }
        return tags
    }

    private val cache = HashMap<String, Pair<String, Set<String>>>()

    private fun tagsOf(f: File): Set<String>? {
        if (!f.isFile) return null
        val key = "${f.length()}:${f.lastModified()}"
        synchronized(cache) { cache[f.absolutePath]?.let { if (it.first == key) return it.second } }
        val tags = runCatching { parse(f.readBytes()) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        synchronized(cache) { cache[f.absolutePath] = key to tags }
        return tags
    }

    /** Метки баз, которыми пользуется Xray (каталог из [GeoData.assetDir]). null — базы не удалось прочитать: тогда ничего не отбрасывается. */
    fun load(dir: String): Known? {
        val ip = tagsOf(File(dir, "geoip.dat")) ?: return null
        val site = tagsOf(File(dir, "geosite.dat")) ?: return null
        return Known(ip, site)
    }

    private fun tokens(s: String) = s.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** Есть ли у записи вид `geoip:…`/`geosite:…` метка в базе. Остальные записи (домены, адреса, `ext:`) не проверяются. */
    fun known(token: String, k: Known): Boolean {
        val (set, rest) = when {
            token.startsWith("geoip:") -> k.geoip to token.removePrefix("geoip:")
            token.startsWith("geosite:") -> k.geosite to token.removePrefix("geosite:")
            else -> return true
        }
        return rest.trimStart('!').substringBefore('@').lowercase() in set
    }

    /** Правило без неизвестных меток; если ничего не осталось, rule = null. */
    fun sanitize(r: Rule, k: Known?): Sanitized {
        if (k == null || r.kind == 2) return Sanitized(r, emptyList())
        val all = tokens(r.value)
        val bad = all.filter { !known(it, k) }
        if (bad.isEmpty()) return Sanitized(r, emptyList())
        val keep = all - bad.toSet()
        return Sanitized(if (keep.isEmpty()) null else Rule(r.kind, keep.joinToString(", "), r.action, r.enabled), bad)
    }
}
