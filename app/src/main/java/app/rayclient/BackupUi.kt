package app.rayclient

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Окно пароля: при сохранении пароль вводится дважды (опечатка сделала бы копию нечитаемой), при восстановлении один раз. */
@Composable
private fun PasswordDialog(title: String, hint: String, twice: Boolean, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val short = a.length < Backup.MIN_PASSWORD
    val same = !twice || a == b
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(a, { a = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = twice && a.isNotEmpty() && short, supportingText = { if (twice) Text("Не короче ${Backup.MIN_PASSWORD} символов.") })
                if (twice) OutlinedTextField(b, { b = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Пароль ещё раз") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = b.isNotEmpty() && !same)
            }
        },
        confirmButton = { TextButton({ onOk(a) }, enabled = a.isNotEmpty() && (!twice || (!short && same))) { Text("Дальше") } },
        dismissButton = { TextButton(onCancel) { Text("Отмена") } })
}

/** «Резервная копия» в «Защите»: сохранить в файл с паролем и восстановить из него. Файл выбирается системным окном, разрешений не нужно. */
@Composable
internal fun MainActivity.BackupPanel() {
    val act = this
    var askExport by remember { mutableStateOf(false) }            // ввод пароля для сохранения
    var exportPw by remember { mutableStateOf<String?>(null) }     // пароль введён, ждём выбора файла
    var importText by remember { mutableStateOf<String?>(null) }   // файл прочитан, ждём пароль
    var opened by remember { mutableStateOf<Map<String, Any>?>(null) }   // расшифровано, ждём подтверждения замены
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPw; exportPw = null
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            AppState.message = try {
                val values = Backup.collect(act) ?: throw IllegalStateException("Не удалось прочитать серверы: копия не создана")
                val file = Backup.seal(values, pw)
                act.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(file.toByteArray(Charsets.UTF_8)) }
                "Копия сохранена"
            } catch (e: IllegalStateException) { e.message ?: "Копия не создана" } catch (e: Exception) { "Не удалось сохранить копию" }
            busy = false
        }.start()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            val text = runCatching {
                act.contentResolver.openInputStream(uri)!!.use { s ->
                    val b = s.readBytes(); if (b.size > 8_000_000) null else String(b, Charsets.UTF_8)
                }
            }.getOrNull()
            if (text == null) AppState.message = "Не удалось прочитать файл" else importText = text
            busy = false
        }.start()
    }

    Panel(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
            Text("Серверы, подписки, правила и настройки в файле, зашифрованном вашим паролем. Пригодится при переустановке или смене телефона. Храните файл и пароль отдельно: в файле ключи ваших серверов. Не входят: история стабильности, журнал, базы.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ askExport = true }, enabled = !busy) { Text("Сохранить копию") }
                FilledTonalButton({ pick.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Восстановить") }
            }
        }
    }

    if (askExport) PasswordDialog("Пароль для копии", "Без этого пароля копию не открыть, восстановить его нельзя.", twice = true,
        onOk = { pw -> askExport = false; exportPw = pw; create.launch("vitrum-backup.vitrumbak") }, onCancel = { askExport = false })

    importText?.let { text ->
        PasswordDialog("Пароль копии", "Введите пароль, которым копия была защищена.", twice = false, onCancel = { importText = null }, onOk = { pw ->
            busy = true
            Thread {
                try {
                    val values = Backup.open(text, pw)
                    if (values == null) AppState.message = "Неверный пароль или файл изменён"
                    else { opened = values; importText = null }
                } catch (e: BackupFormatException) { AppState.message = e.message ?: "Неверный файл"; importText = null }
                busy = false
            }.start()
        })
    }

    opened?.let { values ->
        val (servers, subs) = Backup.counts(values)
        AlertDialog(onDismissRequest = { opened = null }, title = { Text("Заменить данные из копии?") },
            text = { Text("В копии серверов: $servers, подписок: $subs. Текущие серверы, подписки, правила и настройки будут заменены. Восстанавливайте только копию, которую создали сами: чужой файл может подсунуть чужие серверы.") },
            confirmButton = { TextButton({
                opened = null
                if (Backup.restore(act, values)) { AppState.frozen = true; done = true } else AppState.message = "Не удалось восстановить: прежние данные не тронуты"
            }) { Text("Заменить") } },
            dismissButton = { TextButton({ opened = null }) { Text("Отмена") } })
    }

    if (done) AlertDialog(onDismissRequest = {}, title = { Text("Копия восстановлена") },
        text = { Text("Чтобы изменения вступили в силу, приложение нужно закрыть и открыть снова. VPN при этом отключится.") },
        confirmButton = { TextButton({ act.finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("Закрыть приложение") } })
}
