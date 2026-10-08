package app.rayclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Страница «О приложении»: версия, лицензии компонентов (с полным текстом), внешние службы, благодарности. */
@Composable
internal fun MainActivity.SettingsAbout() {
    var open by remember { mutableStateOf<Component?>(null) }
    var fullText by remember { mutableStateOf<Component?>(null) }
    var report by remember { mutableStateOf(false) }
    val ver = remember { appVersion(this) ?: "" }
    GroupTitle("Приложение")
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$BRAND $ver", style = MaterialTheme.typography.titleMedium)
            Text(Licenses.APP_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Licenses.AI_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    GroupTitle("Подпись приложения")
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sig = remember { Integrity.signer(this@SettingsAbout) }
            val src = remember { Integrity.installer(this@SettingsAbout) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SHA-256 сертификата", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Tag(if (Integrity.matches(sig)) "ключ Vitrum" else "другая подпись", if (Integrity.matches(sig)) Pal.Green else Pal.Amber)
            }
            Text(sig?.let { Integrity.fmt(it).split(":").chunked(8).joinToString("\n") { row -> row.joinToString(":") } } ?: "не удалось получить", fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)   // по 8 байт в строке: перенос не рвёт байт; копируется сплошная строка
            Text("Источник установки: " + (src ?: "не определён"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Сверьте отпечаток с тем, что указан у автора. Копия с другой подписью не является вашей сборкой. Обновление с другим ключом Android всё равно не установит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sig != null) TextButton({ copy(Integrity.fmt(sig), sensitive = false) }, contentPadding = PaddingValues(0.dp)) { Text("Скопировать отпечаток") }
        }
    }
    GroupTitle("Составить отчёт о проблеме", "Приложение составит отчёт: версия, система, состояние, настройки и конец журнала Xray. Ссылки, адреса, ключи и UUID заменяются. Отчёт никуда не отправляется сам: вы увидите его целиком и сами решите, куда его передать.")
    Panel { FilledTonalButton({ report = true }, Modifier.padding(16.dp)) { Text("Составить отчёт") } }
    GroupTitle("Компоненты и лицензии")
    Panel {
        Licenses.components.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().clickable { open = c }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text(c.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(c.license, if (c.text == null) Pal.Amber else Pal.Teal)
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < Licenses.components.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
    GroupTitle("Какие службы использует приложение")
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Licenses.services.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("Аналитики и рекламы в приложении нет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    GroupTitle("Благодарности")
    Panel { Text(Licenses.thanks, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }

    if (report) {
        var note by remember { mutableStateOf("") }
        // отчёт собирается в фоне и с задержкой после ввода: чтение журнала и очистка текста не должны тормозить интерфейс
        var text by remember { mutableStateOf("") }
        val self = this@SettingsAbout
        LaunchedEffect(note) { if (text.isNotEmpty()) kotlinx.coroutines.delay(300); text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProblemReport.build(self, note) } }
        AlertDialog(onDismissRequest = { report = false }, title = { Text("Отчёт о проблеме") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(note, { note = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Что случилось?") })
                // отчёт показывается ровно в том виде, в каком его передадут (по-русски), без перевода интерфейса
                androidx.compose.material3.Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            } },
            confirmButton = { TextButton({ shareReport(text) }, enabled = text.isNotEmpty()) { Text("Поделиться") } },
            dismissButton = { Row {
                TextButton({ copy(text, sensitive = false) }) { Text("Скопировать") }
                TextButton({ report = false }) { Text("Закрыть") }
            } })
    }
    open?.let { c ->
        AlertDialog(onDismissRequest = { open = null }, title = { Text(c.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Лицензия: ${c.license}", style = MaterialTheme.typography.titleSmall)
                Text(c.note, style = MaterialTheme.typography.bodyMedium)
                Text(c.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } },
            confirmButton = { if (c.text != null) TextButton({ fullText = c; open = null }) { Text("Полный текст") } else TextButton({ open = null }) { Text("Закрыть") } },
            dismissButton = { Row {
                TextButton({ copy(c.url, sensitive = false) }) { Text("Копировать адрес") }
                if (c.text != null) TextButton({ open = null }) { Text("Закрыть") }
            } })
    }
    fullText?.let { c ->
        AlertDialog(onDismissRequest = { fullText = null }, title = { Text(c.license) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(Licenses.text(this@SettingsAbout, c.text ?: ""), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp) } },
            confirmButton = { TextButton({ fullText = null }) { Text("Закрыть") } })
    }
}

/** Передаёт готовый отчёт через системное меню «Поделиться»: приложение само его никуда не отправляет, адресата выбирает пользователь. */
private fun MainActivity.shareReport(text: String) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text); putExtra(android.content.Intent.EXTRA_SUBJECT, ProblemReport.SUBJECT)
    }
    runCatching { startActivity(android.content.Intent.createChooser(i, null)) }
}
