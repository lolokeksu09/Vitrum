package app.rayclient

import androidx.compose.runtime.*

// ---------- обновления приложения ----------
internal fun AppState.checkUpdate(silent: Boolean) {
    val u = updateUrl.trim()
    Thread {
        (if (u.isEmpty()) Updater.checkDefault(ctx!!) else Updater.check(ctx!!, u)).onSuccess { info ->
            lastUpd = System.currentTimeMillis(); save()
            if (info != null) updateInfo = info else if (!silent) message = "Обновлений нет"
        }.onFailure { if (!silent) message = "Не удалось проверить: ${it.message ?: it.javaClass.simpleName}" }
    }.start()
}

internal fun AppState.autoCheckUpdate() { if ((updateUrl.isNotBlank() || updAutoDefault) && System.currentTimeMillis() - lastUpd > 24 * 3600 * 1000L) checkUpdate(true) }

