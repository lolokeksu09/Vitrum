package app.rayclient

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class Sample(val t: Long, val ok: Boolean, val ms: Int)
class Stat(val n: Int, val uptime: Double, val medianMs: Int, val dots: List<Boolean>)

/** Счётчик для перерисовки списков при изменении истории. */
object HealthUi { var version by mutableIntStateOf(0) }

/** История проверок серверов (по ссылке: id серверов меняются при обновлении подписки) и рейтинг стабильности. */
object Health {
    private const val MAX = 200
    private const val KEEP_MS = 7L * 24 * 3600 * 1000
    private const val DAY_MS = 24L * 3600 * 1000
    private val map = HashMap<String, ArrayDeque<Sample>>()

    /** В истории вместо ссылки хранится её хэш: ключи серверов не лежат на диске в открытом виде. */
    private fun k(link: String) = MessageDigest.getInstance("SHA-256").digest(link.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    @Volatile var onChange: (() -> Unit)? = null

    @Synchronized fun add(link: String, ok: Boolean, ms: Int, now: Long = System.currentTimeMillis()) {
        val q = map.getOrPut(k(link)) { ArrayDeque() }
        q.addLast(Sample(now, ok, if (ok) ms else -1))
        while (q.size > MAX) q.removeFirst()
        while (q.isNotEmpty() && now - q.first().t > KEEP_MS) q.removeFirst()
        onChange?.invoke()
    }

    /** Статистика за последние сутки или null, если замеров за сутки нет. */
    @Synchronized fun stats(link: String, now: Long = System.currentTimeMillis()): Stat? {
        val day = (map[k(link)] ?: return null).filter { now - it.t <= DAY_MS }
        if (day.isEmpty()) return null
        val oks = day.filter { it.ok }
        val med = if (oks.isEmpty()) 0 else oks.map { it.ms }.sorted()[oks.size / 2]
        return Stat(day.size, oks.size.toDouble() / day.size, med, day.takeLast(12).map { it.ok })
    }

    /** Рейтинг 0..1: 70% доля успешных замеров за сутки (нужно хотя бы 3), 30% задержка. Нет данных — нейтральные 0.5. */
    fun rank(link: String, pingNow: Int? = null, now: Long = System.currentTimeMillis()): Double {
        val st = stats(link, now)
        val up = st?.takeIf { it.n >= 3 }?.uptime ?: 0.5
        val ms = pingNow?.takeIf { it > 0 } ?: st?.medianMs?.takeIf { it > 0 }
        val lat = if (ms == null) 0.5 else (1.0 - (ms - 50) / 450.0).coerceIn(0.0, 1.0)
        return 0.7 * up + 0.3 * lat
    }

    @Synchronized fun count(): Int = map.values.sumOf { it.size }

    @Synchronized fun clear() { map.clear(); onChange?.invoke() }

    @Synchronized fun toJson(): String {
        val o = JSONObject()
        map.forEach { (k, q) -> o.put(k, JSONArray().also { a -> q.forEach { s -> a.put(JSONArray().put(s.t).put(if (s.ok) 1 else 0).put(s.ms)) } }) }
        return o.toString()
    }

    @Synchronized fun fromJson(s: String) {
        map.clear()
        val o = JSONObject(s)
        o.keys().forEach { k ->
            val a = o.getJSONArray(k); val q = ArrayDeque<Sample>()
            for (i in 0 until a.length()) a.getJSONArray(i).let { q.addLast(Sample(it.getLong(0), it.getInt(1) == 1, it.getInt(2))) }
            map[if ("://" in k) k(k) else k] = q   // старые записи с ссылкой в ключе превращаем в хэш
        }
    }

    fun load(ctx: Context) { runCatching { File(ctx.filesDir, "health.json").takeIf { it.exists() }?.readText()?.let { fromJson(it) } }; onChange?.invoke() }
    fun save(ctx: Context) { runCatching { File(ctx.filesDir, "health.json").writeText(toJson()) } }
}
