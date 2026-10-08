@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.JournalDialog() {
    if (!AppState.showJournal) return
    var filter by remember { mutableIntStateOf(0) }       // 0 все, 1 VPN, 2 напрямую, 3 заблокировано
    var group by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf<ConnEntry?>(null) }
    val v = ConnLogUi.version
    val all = remember(v, group) { ConnLog.snapshot(group) }
    val list = all.filter { filter == 0 || it.out == filter - 1 }
    val totals = remember(v) { ConnLog.totals() }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    val outNames = listOf("VPN", "Напрямую", "Блок"); val outCols = listOf(Pal.Violet, Pal.Green, Pal.Red)
    Dialog({ AppState.showJournal = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            AuroraBackground(connectionColor(), animate = false)
            Column(Modifier.riseIn().fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                PageHeader("Журнал соединений", { AppState.showJournal = false }) {
                    if (all.isNotEmpty()) TextButton({ ConnLog.clear() }) { Text("Очистить") }   // пока чистить нечего, кнопки нет
                }
                Panel(Modifier.padding(top = 4.dp)) {
                    SwitchRow("Вести журнал", "Пока VPN включён, Xray пишет адреса во временный файл в закрытой папке приложения (не больше 2 МБ); при отключении файл и журнал стираются. Пока журнал выключен, Xray не записывает посещённые адреса нигде. Какое приложение сделало запрос, узнать нельзя.", AppState.connLog) { on ->
                        AppState.connLog = on; AppState.save()
                        if (RayVpnService.connected) RayVpnService.requestReload() else if (!on) ConnLog.clear()
                    }
                }
                if (!AppState.connLog) Text("Включите журнал: он покажет, какие адреса идут через VPN, какие напрямую, какие заблокированы, и позволит одним нажатием добавить правило.", Modifier.padding(top = 16.dp, start = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    if (!RayVpnService.connected) Text("Подключитесь к серверу: журнал заполняется во время подключения.", Modifier.padding(top = 10.dp, start = 6.dp), style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == 0, { filter = 0 }, { Text("Все ${totals.sum()}") })
                        for (i in 0..2) FilterChip(filter == i + 1, { filter = i + 1 }, { Text("${outNames[i]} ${totals[i]}") })
                        FilterChip(group, { group = !group }, { Text("По доменам") })
                    }
                    if (list.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(RayIcons.Journal, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text("Пока пусто.", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        items(list, key = { it.key }) { e ->
                            Row(Modifier.fillMaxWidth().clickable { pick = e }.padding(vertical = 9.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.host, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${e.proto}:${e.port} · ${e.count} раз · ${fmt.format(java.util.Date(e.last))} · " + if (e.viaRule) "по правилу" else "по умолчанию",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Tag(outNames[e.out], outCols[e.out])
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
    pick?.let { e ->
        val base = if (e.isIp) e.host else ConnLog.baseDomain(e.host)
        var scope by remember(e.host) { mutableIntStateOf(0) }
        var act by remember(e.host) { mutableIntStateOf(if (e.out == 0) 1 else 0) }
        AlertDialog(onDismissRequest = { pick = null }, title = { Text(e.host, maxLines = 2) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сейчас: ${outNames[e.out]}. Добавить правило в профиль «${AppState.profileName(AppState.activeProfile)}»:", style = MaterialTheme.typography.bodySmall)
                if (!e.isIp && base != e.host) Segmented(listOf("Весь $base", "Только этот адрес"), scope) { scope = it }
                Segmented(listOf("Через VPN", "Напрямую", "Блок"), act) { act = it }
            } },
            confirmButton = { TextButton({
                if (AppState.activeProfile == "sub") AppState.message = "Профиль из подписки менять нельзя: сделайте копию в «Маршрутах»"
                else {
                    val value = if (e.isIp) e.host else if (scope == 0 || base == e.host) base else e.host
                    val kind = if (e.isIp) 1 else 0
                    AppState.rules.removeAll { it.kind == kind && it.value == value }
                    AppState.rules += Rule(kind, value, act); AppState.save()
                    if (RayVpnService.connected) RayVpnService.requestReload()
                    AppState.message = "Правило добавлено: $value · ${listOf("через VPN", "напрямую", "блок")[act]}"
                }
                pick = null }) { Text("Новое правило") } },
            dismissButton = { TextButton({ pick = null }) { Text("Отмена") } })
    }
}

