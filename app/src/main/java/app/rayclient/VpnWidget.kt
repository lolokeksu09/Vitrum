package app.rayclient

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * Виджет «Подключить / Отключить». Состояние берётся у службы VPN и обновляется при каждой её смене (`refresh`), по таймеру не обновляется.
 * Касание, когда VPN выключен: открывает приложение с действием «подключить» (то же, что ярлык значка: нужное разрешение VPN система спросит как обычно).
 * Касание, когда VPN включён или подключается: останавливает службу через собственное намерение приложения (PendingIntent, подделать его снаружи нельзя).
 */
class VpnWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { for (id in ids) manager.updateAppWidget(id, views(context)) }

    companion object {
        enum class Kind { OFF, BUSY, ON, BLOCKED }

        fun kind(connected: Boolean, busy: Boolean, blocked: Boolean): Kind = when { blocked -> Kind.BLOCKED; connected -> Kind.ON; busy -> Kind.BUSY; else -> Kind.OFF }

        internal fun label(k: Kind): String = when (k) { Kind.ON -> "Подключено"; Kind.BUSY -> "Подключение…"; Kind.BLOCKED -> "Трафик заблокирован"; Kind.OFF -> "Отключено" }

        private fun views(ctx: Context): RemoteViews {
            AppState.init(ctx)
            val k = kind(RayVpnService.connected, RayVpnService.busy, RayVpnService.blocked)
            val v = RemoteViews(ctx.packageName, R.layout.widget_vpn)
            val color = when (k) { Kind.ON -> 0xFF3DDBB4.toInt(); Kind.BUSY -> 0xFFFFC857.toInt(); Kind.BLOCKED -> 0xFFFF6B6B.toInt(); Kind.OFF -> 0xFFA9A5B8.toInt() }
            v.setTextViewText(R.id.widget_state, I18n.tr(label(k)))
            v.setTextColor(R.id.widget_state, color)
            v.setInt(R.id.widget_icon, "setColorFilter", color)
            val tap = if (k == Kind.OFF)
                PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java).setAction("app.rayclient.action.CONNECT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(ctx, 12, Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.widget_root, tap)
            return v
        }

        /** Перерисовывает все виджеты приложения; вызывает служба VPN при смене состояния. */
        fun refresh(ctx: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, VpnWidget::class.java))
                if (ids.isNotEmpty()) { val v = views(ctx); for (id in ids) m.updateAppWidget(id, v) }
            }
        }
    }
}
