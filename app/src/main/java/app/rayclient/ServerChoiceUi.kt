package app.rayclient

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Use the same selection policy for the server list and diagnostic results. */
internal fun MainActivity.chooseServerFromUi(targetId: String): ServerChoiceDecision? {
    if (AppState.servers.none { it.id == targetId }) {
        AppState.message = "Сервер больше не доступен"
        return null
    }
    val decision = ServerChoicePolicy.decide(
        targetId, AppState.selectedId, RayVpnService.connected,
        RayVpnService.busy || RayVpnService.switching || AppState.quickBusy,
        RayVpnService.blocked
    )
    if (decision == ServerChoiceDecision.SELECT) {
        NetworkServerAutomation.noteManualSelection(this)
    }
    when (decision) {
        ServerChoiceDecision.REQUEST_SWITCH -> startService(Intent(this, RayVpnService::class.java)
            .setAction(RayVpnService.ACTION_SWITCH)
            .putExtra(RayVpnService.EXTRA_SWITCH_SERVER_ID, targetId))
        ServerChoiceDecision.SELECT -> {
            AppState.selectedId = targetId
            AppState.save()
        }
        ServerChoiceDecision.NO_CHANGE, ServerChoiceDecision.REQUIRE_DISCONNECT -> Unit
    }
    return decision
}

@Composable
internal fun ServerChoiceBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена сервера") },
        text = { Text("Смена сервера недоступна, пока VPN запускается, переключается или удерживает заблокированный туннель. Дождитесь завершения действия.") },
        confirmButton = { TextButton(onDismiss) { Text("Понятно") } }
    )
}
