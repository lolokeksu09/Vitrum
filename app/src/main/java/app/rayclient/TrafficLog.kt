package app.rayclient

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate

/**
 * Трафик по дням: сколько байт получило и отправило приложение (счётчик системы для uid приложения: сюда входит и зашифрованный обмен с сервером,
 * и прямой трафик, и служебные запросы). Считается только пока VPN подключён. Для контроля лимита тарифа; это не счёт оператора.
 * Хранится в настройках приложения (ключ traffic), не более KEEP_DAYS дней.
 */
object TrafficLog {
    const val KEEP_DAYS = 62
    private const val KEY = "traffic"
    private val days = java.util.TreeMap<String, LongArray>()   // "2026-10-07" → [получено, отправлено, из них по мобильной сети (всего байт)]
    var tick by mutableLongStateOf(0L)   // меняется при каждой записи: страница статистики перерисовывается

    /** mobile: в этот момент интернет шёл по мобильной сети (тогда эти байты ещё и считаются в «мобильных»). */
    @Synchronized fun add(day: String, rx: Long, tx: Long, mobile: Boolean = false) {
        if (rx <= 0 && tx <= 0) return
        val a = days.getOrPut(day) { LongArray(3) }; a[0] += rx.coerceAtLeast(0); a[1] += tx.coerceAtLeast(0)
        if (mobile) a[2] += rx.coerceAtLeast(0) + tx.coerceAtLeast(0)
        tick++
    }

    /** Идёт ли интернет сейчас по мобильной сети: есть мобильная сеть и нет Wi-Fi или Ethernet (VPN не считается). Для приблизительного учёта раз в несколько секунд. */
    fun mobileFrom(wifiOrEthernet: Boolean, cellular: Boolean) = cellular && !wifiOrEthernet

    fun mobileNow(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
            .filter { it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) }
        mobileFrom(caps.any { it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) },
            caps.any { it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) })
    }.getOrDefault(false)

    /** Сумма «мобильных» байт за последние n дней и за месяц даты. */
    @Synchronized fun mobileLastDays(n: Int, today: LocalDate = LocalDate.now()): Long {
        var m = 0L
        for (k in 0 until n) days[today.minusDays(k.toLong()).toString()]?.let { m += it[2] }
        return m
    }

    @Synchronized fun mobileMonth(today: LocalDate = LocalDate.now()): Long {
        val p = "%04d-%02d-".format(today.year, today.monthValue); var m = 0L
        for ((d, a) in days) if (d.startsWith(p)) m += a[2]
        return m
    }

    fun today(): String = LocalDate.now().toString()

    /** Сумма (получено, отправлено) за последние n дней, считая сегодняшний. */
    @Synchronized fun lastDays(n: Int, today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        var r = 0L; var t = 0L
        for (k in 0 until n) days[today.minusDays(k.toLong()).toString()]?.let { r += it[0]; t += it[1] }
        return r to t
    }

    /** Сумма за календарный месяц даты. */
    @Synchronized fun month(today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val p = "%04d-%02d-".format(today.year, today.monthValue); var r = 0L; var t = 0L
        for ((d, a) in days) if (d.startsWith(p)) { r += a[0]; t += a[1] }
        return r to t
    }

    /** Последние n дней от новых к старым: (дата, получено, отправлено). */
    @Synchronized fun byDay(n: Int, today: LocalDate = LocalDate.now()): List<Triple<String, Long, Long>> =
        (0 until n).map { k -> today.minusDays(k.toLong()).toString().let { d -> Triple(d, days[d]?.get(0) ?: 0L, days[d]?.get(1) ?: 0L) } }

    @Synchronized fun toJson(today: LocalDate = LocalDate.now()): String {
        val oldest = today.minusDays(KEEP_DAYS.toLong() - 1).toString()
        val o = JSONObject(); for ((d, a) in days) if (d >= oldest) o.put(d, org.json.JSONArray().put(a[0]).put(a[1]).put(a[2]))
        return o.toString()
    }

    @Synchronized fun fromJson(s: String) {
        days.clear()
        runCatching {
            val o = JSONObject(s)
            for (d in o.keys()) { val a = o.getJSONArray(d); runCatching { LocalDate.parse(d) }.onSuccess { days[d] = longArrayOf(a.getLong(0).coerceAtLeast(0), a.getLong(1).coerceAtLeast(0), a.optLong(2, 0).coerceAtLeast(0)) } }
        }
        tick++
    }

    @Synchronized fun clear() { days.clear(); tick++ }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ray", Context.MODE_PRIVATE)
    fun load(ctx: Context) = fromJson(prefs(ctx).getString(KEY, "{}") ?: "{}")
    fun save(ctx: Context) { if (!AppState.frozen) prefs(ctx).edit().putString(KEY, toJson()).apply() }
}

/** Предупреждение о месячном лимите трафика: уровни 80 % и 100 % от заданного в ГБ, каждый раз в месяц один раз. */
object TrafficLimit {
    private const val WARNED_KEY = "trafficWarned"

    /** 0 — ниже 80 %, 80 — от 80 % до лимита, 100 — лимит достигнут. limitGb == 0: предупреждения выключены. */
    fun level(usedBytes: Long, limitGb: Int): Int {
        if (limitGb <= 0) return 0
        val limit = limitGb.toLong() * 1024 * 1024 * 1024
        return when { usedBytes >= limit -> 100; usedBytes * 10 >= limit * 8 -> 80; else -> 0 }
    }

    /** Нужно ли сообщить сейчас: уровень выше уже показанного в этом месяце. warned хранится как "2026-10:80". */
    fun shouldWarn(level: Int, monthKey: String, warned: String?): Boolean {
        if (level == 0) return false
        val (m, l) = (warned ?: "").split(':').let { it.getOrElse(0) { "" } to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        return m != monthKey || level > l
    }

    /** Что сравнивается с лимитом: весь трафик или только мобильный. */
    fun used(all: Long, mobile: Long, mobileOnly: Boolean) = if (mobileOnly) mobile else all

    fun monthKey(today: LocalDate = LocalDate.now()) = "%04d-%02d".format(today.year, today.monthValue)

    /** Вызывается службой раз в минуту. Уведомление без названия сервера. */
    fun check(ctx: Context) {
        val level = level(used(TrafficLog.month().let { it.first + it.second }, TrafficLog.mobileMonth(), AppState.trafficLimitMobileOnly), AppState.trafficLimitGb)
        val prefs = ctx.getSharedPreferences("ray", Context.MODE_PRIVATE)
        val key = monthKey()
        if (!shouldWarn(level, key, prefs.getString(WARNED_KEY, null))) return
        prefs.edit().putString(WARNED_KEY, "$key:$level").apply()
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("traffic", I18n.tr("Лимит трафика"), android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val used = used(TrafficLog.month().let { it.first + it.second }, TrafficLog.mobileMonth(), AppState.trafficLimitMobileOnly)
            val text = if (level >= 100) "Месячный лимит трафика достигнут: ${size(used)} из ${AppState.trafficLimitGb} ГБ." else "Использовано 80 % месячного лимита: ${size(used)} из ${AppState.trafficLimitGb} ГБ."
            val open = android.app.PendingIntent.getActivity(ctx, 5, android.content.Intent(ctx, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
            nm.notify(8, android.app.Notification.Builder(ctx, "traffic").setSmallIcon(R.drawable.ic_tile).setContentTitle(BRAND).setContentText(I18n.tr(text)).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}

/** Мягкий предел памяти для Xray (переменная Go GOMEMLIMIT): при приближении к нему сборщик мусора работает чаще. */
object MemLimit {
    val CHOICES = listOf(0, 128, 256)   // 0 — выключено, иначе МиБ
    fun env(choice: Int): String? = CHOICES.getOrElse(choice) { 0 }.takeIf { it > 0 }?.let { "${it}MiB" }
}
