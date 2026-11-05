package app.rayclient

import androidx.compose.ui.graphics.Color

fun connectionColor(): Color {
    val err = RayVpnService.state.startsWith("Ошибка")
    return when {
        RayVpnService.blocked || err -> Pal.Red
        RayVpnService.connected -> Pal.Green
        RayVpnService.busy || AppState.quickBusy -> Pal.Amber
        else -> Pal.Violet
    }
}
