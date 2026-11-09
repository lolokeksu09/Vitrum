package app.rayclient

import java.net.InetAddress

/**
 * Проверка содержимого скачанного geoip.dat до того, как он заменит рабочую базу. Контрольная сумма из того же релиза доказывает
 * целостность, но не подлинность: подмена метки `private` на `0.0.0.0/0` пустила бы весь интернет мимо VPN («Локальная сеть» и «Кроме России»).
 * Проверяются только метки, которыми пользуется приложение; остальные страны могут быть любыми (у `us` бывает и /7).
 */
object GeoSanity {
    class Cidr(val ip: ByteArray, val prefix: Int)

    /** Метки с правилом «напрямую»: узкие диапазоны и небольшая доля адресного пространства. */
    private val GUARDED = setOf("ru", "ru-whitelist", "telegram")
    private val WANTED = GUARDED + "private"
    /** Доля IPv4 (в процентах) для метки; у `ru` сейчас около 1,1%, у `ru-whitelist` около 0,8%. */
    private const val MAX_SHARE = 3.0
    private const val MIN_V4_PREFIX = 8
    private const val MIN_V6_PREFIX = 16

    /** Зарезервированные диапазоны, из которых может состоять `private`. Любой другой диапазон в этой метке считается подменой. */
    private val RESERVED: List<Cidr> = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
        "192.88.99.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/3",
        "::/127", "::1/128", "fc00::/7", "fe80::/10", "ff00::/8", "2001:db8::/32", "100::/64",
    ).map { s -> Cidr(InetAddress.getByName(s.substringBefore('/')).address, s.substringAfter('/').toInt()) }

    private class Reader(val b: ByteArray) {
        var p = 0
        fun more() = p < b.size
        fun varint(): Long {
            var r = 0L; var s = 0
            while (true) {
                if (p >= b.size || s > 63) throw IllegalStateException("truncated")
                val c = b[p++].toInt() and 0xff
                r = r or ((c and 0x7f).toLong() shl s); s += 7
                if (c and 0x80 == 0) return r
            }
        }
        fun bytes(): ByteArray { val n = varint(); if (n < 0 || p + n > b.size) throw IllegalStateException("truncated"); return b.copyOfRange(p, p + n.toInt()).also { p += n.toInt() } }
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> p += 8; 2 -> bytes(); 5 -> p += 4; else -> throw IllegalStateException("wire type") } }
    }

    /** Нужные метки файла: код в нижнем регистре -> диапазоны. Бросает исключение, если файл оборван или разобран неверно. */
    fun parse(data: ByteArray, wanted: Set<String> = WANTED): Map<String, List<Cidr>> {
        val out = HashMap<String, MutableList<Cidr>>(); val all = Reader(data); var tags = 0
        while (all.more()) {
            val key = all.varint(); val f = (key shr 3).toInt(); val wire = (key and 7).toInt()
            if (f != 1 || wire != 2) { all.skip(wire); continue }
            val e = Reader(all.bytes()); var code: String? = null; val cidrs = ArrayList<ByteArray>()
            while (e.more()) {
                val k = e.varint(); val g = (k shr 3).toInt(); val w = (k and 7).toInt()
                if (g == 1 && w == 2) code = String(e.bytes(), Charsets.UTF_8).lowercase()
                else if (g == 2 && w == 2) { if (code == null || code in wanted) cidrs += e.bytes() else e.bytes() }
                else e.skip(w)
            }
            tags++
            val c = code ?: continue
            if (c !in wanted) continue
            val list = out.getOrPut(c) { ArrayList() }
            for (raw in cidrs) {
                val r = Reader(raw); var ip: ByteArray? = null; var pre = 0
                while (r.more()) { val k = r.varint(); val g = (k shr 3).toInt(); val w = (k and 7).toInt(); if (g == 1 && w == 2) ip = r.bytes() else if (g == 2 && w == 0) pre = r.varint().toInt() else r.skip(w) }
                if (ip != null && (ip.size == 4 || ip.size == 16) && pre in 0..ip.size * 8) list += Cidr(ip, pre)
            }
        }
        if (tags < 50) throw IllegalStateException("too few tags")
        return out
    }

    /** Лежит ли диапазон a целиком внутри диапазона net. */
    internal fun inside(a: Cidr, net: Cidr): Boolean {
        if (a.ip.size != net.ip.size || a.prefix < net.prefix) return false
        for (bit in 0 until net.prefix) { val m = 0x80 shr (bit % 8); if ((a.ip[bit / 8].toInt() and m) != (net.ip[bit / 8].toInt() and m)) return false }
        return true
    }

    /** null — база годится; иначе причина отказа по-русски. */
    fun check(data: ByteArray): String? {
        val m = try { parse(data) } catch (_: Exception) { return "файл не разобрался" }
        val priv = m["private"]; if (priv.isNullOrEmpty() || m["ru"].isNullOrEmpty()) return "нет меток private или ru"
        if (priv.any { c -> RESERVED.none { inside(c, it) } }) return "метка private выходит за зарезервированные диапазоны"
        for (t in GUARDED) {
            val list = m[t] ?: continue
            if (list.any { (it.ip.size == 4 && it.prefix < MIN_V4_PREFIX) || (it.ip.size == 16 && it.prefix < MIN_V6_PREFIX) }) return "метка $t содержит слишком широкий диапазон"
            val share = list.filter { it.ip.size == 4 }.sumOf { 2.0.pow(32 - it.prefix) } / 4294967296.0 * 100
            if (share > MAX_SHARE) return "метка $t занимает слишком большую долю адресов"
        }
        return null
    }

    private fun Double.pow(e: Int): Double = Math.pow(this, e.toDouble())
}
