package app.rayclient

import android.content.Context
import androidx.work.*
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Расписание VPN: в заданное окно времени (например 23:00–07:00) VPN выключен, в остальное время включён. На границах окна фоновая задача
 * выключает или включает VPN (включает, только если выбран сервер и выдано разрешение VPN). Срабатывает через WorkManager без точных
 * будильников, поэтому система может сдвинуть запуск на несколько минут (режим экономии заряда).
 */
object VpnSchedule {
    const val WORK = "vpn-schedule"
    private const val DAY = 86_400

    /** «7:05» или «07:05» → минуты от полуночи; иное → null. */
    fun parse(s: String): Int? {
        val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    }

    fun format(min: Int) = "%02d:%02d".format(min / 60 % 24, min % 60)

    /** Попадает ли время в окно «VPN выключен». Окно может переходить через полночь; одинаковые границы — окна нет. */
    fun inWindow(nowMin: Int, from: Int, to: Int): Boolean = when {
        from == to -> false
        from < to -> nowMin in from until to
        else -> nowMin >= from || nowMin < to
    }

    /** Что должно быть сейчас: 2 — выключить VPN, 1 — включить (в формате сценариев). */
    fun desired(nowMin: Int, from: Int, to: Int): Int = if (inWindow(nowMin, from, to)) 2 else 1

    /** Через сколько секунд ближайшая граница окна; null, если окна нет. Ровно на границе берётся следующая. */
    fun secondsToNext(nowSec: Int, from: Int, to: Int): Int? {
        if (from == to) return null
        return listOf(from * 60, to * 60).map { b -> ((b - nowSec) % DAY + DAY) % DAY }.map { if (it == 0) DAY else it }.min()
    }

    fun enqueue(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val sec = secondsToNext(LocalTime.now().toSecondOfDay(), AppState.schedFrom, AppState.schedTo)
        if (!AppState.schedOn || sec == null) { wm.cancelUniqueWork(WORK); return }
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ScheduleWorker>().setInitialDelay(sec + 5L, TimeUnit.SECONDS).build())
    }
}

class ScheduleWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        AppState.init(applicationContext)
        if (AppState.schedOn) {
            Scenarios.runVpn(applicationContext, VpnSchedule.desired(LocalTime.now().let { it.hour * 60 + it.minute }, AppState.schedFrom, AppState.schedTo), "Расписание: нужно разрешение VPN, откройте приложение")
        }
        VpnSchedule.enqueue(applicationContext)
        return Result.success()
    }
}
