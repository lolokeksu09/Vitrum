package app.rayclient

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

class AppRow(val label: String, val wifi: Long, val mobile: Long) { val total: Long get() = wifi + mobile }

/**
 * «Трафик по приложениям»: сколько данных каждое приложение получило и отправило по Wi-Fi и по мобильной сети. Читаются счётчики системы
 * (NetworkStatsManager), для этого пользователь выдаёт «Доступ к истории использования» (особое разрешение). Данные остаются на телефоне
 * и никуда не отправляются. Какое приложение стоит за счётчиком, система сопоставляет сама, в том числе для трафика через VPN.
 */
object AppTraffic {
    enum class Period { TODAY, WEEK, MONTH }

    /** Начало периода, миллисекунды: сегодня с полуночи, 7 дней (включая сегодня), календарный месяц. */
    fun periodStart(p: Period, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val d = when (p) { Period.TODAY -> today; Period.WEEK -> today.minusDays(6); Period.MONTH -> today.withDayOfMonth(1) }
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Байты по uid → строки списка: от больших к меньшим, без нулевых. */
    fun merge(wifi: Map<Int, Long>, mobile: Map<Int, Long>, label: (Int) -> String): List<AppRow> =
        (wifi.keys + mobile.keys).groupBy { label(it) }.map { (name, uids) ->
            AppRow(name, uids.sumOf { wifi[it] ?: 0L }, uids.sumOf { mobile[it] ?: 0L })
        }.filter { it.total > 0 }.sortedByDescending { it.total }

    /** Подпись для счётчика: служебные uid системы по-русски, иначе первое имя приложения (и «+N», если uid общий у нескольких). */
    fun labelFor(uid: Int, names: List<String>): String = when {
        uid == -4 -> "Удалённые приложения"
        uid == -5 -> "Раздача интернета (точка доступа)"
        uid == 0 -> "Система (root)"
        uid == 1000 -> "Система Android"
        names.isEmpty() -> "Неизвестное приложение (uid $uid)"
        names.size == 1 -> names[0]
        else -> names[0] + " +" + (names.size - 1)
    }

    /** Выдан ли «Доступ к истории использования» этому приложению. */
    fun hasAccess(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Открывает системный экран «Доступ к истории использования»: там пользователь сам включает Vitrum. */
    fun openAccessSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private fun sum(nsm: NetworkStatsManager, type: Int, start: Long, end: Long): Map<Int, Long>? = try {
        val out = HashMap<Int, Long>()
        nsm.querySummary(type, null, start, end).use { st ->
            val b = NetworkStats.Bucket()
            while (st.hasNextBucket()) { st.getNextBucket(b); out[b.uid] = (out[b.uid] ?: 0L) + b.rxBytes + b.txBytes }
        }
        out
    } catch (_: Exception) { null }

    class Result(val rows: List<AppRow>, val mobileKnown: Boolean, val period: Period? = null)

    /** Список по приложениям за период; null — доступа нет или система отказала. На счётчики мобильной сети система может не дать ответа без подписчика: тогда mobileKnown = false. */
    fun query(ctx: Context, p: Period): Result? {
        if (!hasAccess(ctx)) return null
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java) ?: return null
        val start = periodStart(p); val end = System.currentTimeMillis()
        val wifi = sum(nsm, ConnectivityManager.TYPE_WIFI, start, end) ?: return null
        val mobile = sum(nsm, ConnectivityManager.TYPE_MOBILE, start, end)
        val pm = ctx.packageManager
        fun names(uid: Int): List<String> = runCatching { pm.getPackagesForUid(uid)?.map { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) } }.getOrNull().orEmpty()
        val cache = HashMap<Int, String>()
        return Result(merge(wifi, mobile ?: emptyMap()) { uid -> cache.getOrPut(uid) { labelFor(uid, names(uid)) } }, mobile != null, p)
    }
}
