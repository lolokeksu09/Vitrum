package app.rayclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    companion object {
        /** События смены времени и часового пояса: расписание VPN считает задержку по часам, после смены её надо пересчитать. */
        val TIME_CHANGES = setOf("android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED")
    }

    override fun onReceive(c: Context, i: Intent) {
        if (i.action in TIME_CHANGES) { AppState.init(c); VpnSchedule.enqueue(c); return }
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        AppState.init(c)
        if (AppState.scenariosOn) Scenarios.syncService(c, apply = true)
        // Без разрешения VPN (prepare != null) автозапуск невозможен: нужно один раз подтвердить его в приложении.
        if (AppState.autoBoot && AppState.selectedId != null && VpnService.prepare(c) == null)
            ContextCompat.startForegroundService(c, Intent(c, RayVpnService::class.java).setAction(RayVpnService.ACTION_START))
    }
}
