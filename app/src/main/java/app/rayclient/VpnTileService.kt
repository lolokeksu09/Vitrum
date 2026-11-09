package app.rayclient

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/** Плитка в шторке: включить или выключить VPN одним нажатием. */
class VpnTileService : TileService() {
    override fun onStartListening() { refresh() }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (RayVpnService.connected || RayVpnService.blocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = BRAND
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = I18n.tr(when { RayVpnService.blocked -> "Заблокировано"; RayVpnService.connected -> "Подключено"; RayVpnService.busy -> "Подключение…"; else -> "Выключено" })
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        AppState.init(this)
        if (RayVpnService.connected || RayVpnService.busy || RayVpnService.blocked) {
            startService(Intent(this, RayVpnService::class.java).setAction(RayVpnService.ACTION_STOP))
        } else if (AppState.selectedId != null && VpnService.prepare(this) == null) {
            ContextCompat.startForegroundService(this, Intent(this, RayVpnService::class.java).setAction(RayVpnService.ACTION_START))
        } else {
            // нет сервера или не выдано разрешение VPN: открываем приложение
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 2, i, PendingIntent.FLAG_IMMUTABLE)) else startActivityAndCollapse(i)
        }
        refresh()
    }
}
