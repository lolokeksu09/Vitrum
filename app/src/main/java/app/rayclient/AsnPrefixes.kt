package app.rayclient

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Подсети игровых серверов. Матчи идут по UDP на IP-адреса без доменов, поэтому списков доменов мало:
 * подсети компаний берутся у RIPEstat по номеру автономной системы (Riot AS6507, Blizzard AS57976, Valve AS32590),
 * кэшируются на телефоне и обновляются раз в сутки. Сами данные в приложение не вшиты.
 */
object AsnPrefixes {
    private val cidr4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})$""")
    private val cidr6 = Regex("""^[0-9a-fA-F:]+/(\d{1,3})$""")
    private const val DAY = 24 * 3600 * 1000L
    private val cache = HashMap<String, Pair<Long, List<String>>>()   // AS -> (время, подсети)
    @Volatile private var loaded = false

    /** Префикс принимается, только если он разумного размера: ошибка в данных не должна пустить мимо VPN полинтернета (/0, /8). */
    fun valid(p: String): Boolean {
        cidr4.matchEntire(p)?.let { m ->
            val o = m.groupValues.subList(1, 5).map { it.toInt() }
            return o.all { it in 0..255 } && m.groupValues[5].toInt() in 12..32
        }
        if (':' in p && cidr6.matches(p) && RuleCheck.isIpv6(p.substringBefore('/'))) return p.substringAfter('/').toInt() in 24..128   // выделения у реальных компаний от /29 (Riot), а целые регистры вроде 2a00::/12 отбрасываем
        return false
    }

    /** Разбор ответа RIPEstat announced-prefixes. */
    fun parse(json: String): List<String> {
        val arr = JSONObject(json).getJSONObject("data").getJSONArray("prefixes")
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) { val p = arr.getJSONObject(i).optString("prefix"); if (valid(p)) out += p }
        return out.toList()
    }

    private fun fetch(asn: String): List<String> {
        val c = URL("https://stat.ripe.net/data/announced-prefixes/data.json?resource=$asn").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 25000
        val body = c.inputStream.use { ins ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
            while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > 2_000_000) error("ответ слишком большой"); out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
        return parse(body).also { require(it.isNotEmpty()) { "пустой список" } }
    }

    private fun file(ctx: Context) = File(ctx.filesDir, "asn-cache.json")

    @Synchronized private fun load(ctx: Context) {
        if (loaded) return
        runCatching {
            val j = JSONObject(file(ctx).readText())
            j.keys().forEach { k -> val o = j.getJSONObject(k); val a = o.getJSONArray("p"); cache[k] = o.getLong("at") to (0 until a.length()).map { a.getString(it) }.filter { valid(it) } }
        }
        loaded = true
    }

    @Synchronized private fun save(ctx: Context) {
        val j = JSONObject(); cache.forEach { (k, v) -> j.put(k, JSONObject().put("at", v.first).put("p", org.json.JSONArray(v.second))) }
        runCatching { file(ctx).writeText(j.toString()) }
    }

    /** Подсети включённых наборов (из кэша; пусто, пока не скачаны). */
    @Synchronized fun prefixes(ctx: Context, presetIds: Collection<String>): List<String> {
        load(ctx)
        return PRESETS.filter { it.id in presetIds }.flatMap { it.asns }.distinct().flatMap { cache[it]?.second ?: emptyList() }.distinct()
    }

    @Synchronized fun count(ctx: Context, presetId: String): Int { load(ctx); return PRESETS.firstOrNull { it.id == presetId }?.asns?.sumOf { cache[it]?.second?.size ?: 0 } ?: 0 }
    @Synchronized fun newest(ctx: Context): Long { load(ctx); return cache.values.maxOfOrNull { it.first } ?: 0L }

    /** Скачивает подсети для включённых наборов, у которых они устарели (или всегда при force). Возвращает текст ошибки или null. */
    fun refresh(ctx: Context, presetIds: Collection<String>, force: Boolean): String? {
        load(ctx)
        val need = PRESETS.filter { it.id in presetIds }.flatMap { it.asns }.distinct()
        var err: String? = null
        for (asn in need) {
            val old = synchronized(this) { cache[asn] }
            if (!force && old != null && System.currentTimeMillis() - old.first < DAY) continue
            try { val p = fetch(asn); synchronized(this) { cache[asn] = System.currentTimeMillis() to p } }
            catch (e: Exception) { if (old == null) err = e.message ?: e.javaClass.simpleName }   // есть старые данные: остаёмся на них
        }
        save(ctx); AsnUi.version++
        return err
    }
}

/** Счётчик для перерисовки состояния подсетей. */
object AsnUi { var version by mutableIntStateOf(0) }
