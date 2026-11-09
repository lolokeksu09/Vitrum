@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package app.rayclient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.JournalDialog() {
    if (!AppState.showJournal) return
    var routeMenu by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }       // 0 все, 1 VPN, 2 напрямую, 3 заблокировано
    var group by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }  // 0 traffic, 1 redacted VPN events
    var confirmClearPage by remember { mutableIntStateOf(-1) }
    var pick by remember { mutableStateOf<ConnEntry?>(null) }
    val v = ConnLogUi.version
    val all = remember(v, group) { ConnLog.snapshot(group) }
    val list = all.filter { filter == 0 || it.out == filter - 1 }
    val totals = remember(v) { ConnLog.totals() }
    val events = VpnEventHistory.entries
    val eventSummary = remember(events) { VpnEventPolicy.summary(events) }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    val outNames = listOf("VPN", "Напрямую", "Блок"); val outCols = listOf(Pal.Violet, Pal.Green, Pal.Red)
    Dialog({ AppState.showJournal = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                PageHeader("Журнал и события", { AppState.showJournal = false }) {
                    if (page == 0 && all.isNotEmpty()) TextButton({ confirmClearPage = 0 }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Очистить")) }
                    if (page == 1 && events.isNotEmpty()) TextButton({ confirmClearPage = 1 }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Очистить")) }
                }
                Segmented(listOf(I18n.tr("Трафик"), I18n.tr("События VPN")), page) { page = it }
                if (page == 1) {
                    Column {
                        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(I18n.tr("Последние 40 событий только в памяти процесса. IP, домены, ссылки и ключи не записываются. История исчезнет при завершении процесса."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(I18n.tr("Смен серверов: {0}".replace("{0}", eventSummary.switches.toString())), style = MaterialTheme.typography.bodySmall)
                            Text(I18n.tr("Сбоев: {0}".replace("{0}", eventSummary.problems.toString())), style = MaterialTheme.typography.bodySmall)
                            Text(I18n.tr("Потерь сети: {0}".replace("{0}", eventSummary.networkLosses.toString())), style = MaterialTheme.typography.bodySmall)
                            if (events.isNotEmpty()) Text(I18n.tr("Отчёт содержит время событий. Проверьте его перед отправкой."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (events.isNotEmpty()) OutlinedButton(
                                onClick = { copy(VpnEventPolicy.report(events), sensitive = false) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text(I18n.tr("Скопировать отчёт без адресов и ключей")) }
                        }
                    }
                    if (events.isEmpty()) {
                        Panel(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(RayIcons.Info, null, Modifier.padding(top = 2.dp).size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    I18n.tr("Событий пока нет. Подключите VPN или смените сервер."),
                                    Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(top = 10.dp)) {
                            items(events.asReversed()) { event ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(I18n.tr(event.type.title),
                                            color = if (event.type.problem) Pal.Red else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall)
                                        Text(fmt.format(java.util.Date(event.atMs)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (event.count > 1) Text("×${event.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            }
                        }
                    }
                } else {
                    Panel(Modifier.padding(top = 4.dp)) {
                        SwitchRow("Вести журнал", "Пока VPN включён, Xray пишет адреса во временный файл в закрытой папке приложения (не больше 2 МБ); при отключении файл и журнал стираются. Пока журнал выключен, Xray не записывает посещённые адреса нигде. Какое приложение сделало запрос, узнать нельзя.", AppState.connLog) { on ->
                            AppState.connLog = on; AppState.save()
                            if (RayVpnService.connected) RayVpnService.requestReload() else if (!on) ConnLog.clear()
                        }
                    }
                    if (!AppState.connLog) {
                        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(RayIcons.Journal, null, Modifier.padding(top = 2.dp).size(21.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    I18n.tr("Включите журнал: он покажет, какие адреса идут через VPN, какие напрямую, какие заблокированы, и позволит одним нажатием добавить правило."),
                                    Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (!RayVpnService.connected) Text(I18n.tr("Подключитесь к серверу: журнал заполняется во время подключения."), Modifier.padding(top = 10.dp, start = 6.dp), style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        val routeLabels = listOf(I18n.tr("Все {0}".replace("{0}", totals.sum().toString()))) +
                            outNames.mapIndexed { i, name -> "${I18n.tr(name)} ${totals[i]}" }
                        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                TextButton({ routeMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(routeLabels[filter])
                                    Icon(RayIcons.Chevron, null, Modifier.size(16.dp).rotate(90f))
                                }
                                DropdownMenu(routeMenu, { routeMenu = false }) {
                                    routeLabels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { filter = index; routeMenu = false },
                                            trailingIcon = if (filter == index) ({ Icon(RayIcons.Check, null) }) else null
                                        )
                                    }
                                }
                            }
                            FilterChip(group, { group = !group }, { Text(I18n.tr("По доменам")) })
                        }
                        if (list.isEmpty()) {
                            val filtered = filter != 0 && all.isNotEmpty()
                            Text(I18n.tr(if (filtered) "Нет записей для этого маршрута" else "Пока пусто."),
                                Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (filtered) TextButton({ filter = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Сбросить фильтры")) }
                        }
                        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                            items(list, key = { it.key }) { e ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { pick = e }.padding(vertical = 9.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.host, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(I18n.tr("{0}:{1} · {2} раз · {3} · {4}".replace("{0}", e.proto).replace("{1}", e.port.toString()).replace("{2}", e.count.toString()).replace("{3}", fmt.format(java.util.Date(e.last))).replace("{4}", I18n.tr(if (e.viaRule) "по правилу" else "по умолчанию"))),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Tag(outNames[e.out], outCols[e.out])
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }
                    }
                } // traffic tab
            }
        }
    }
    if (confirmClearPage >= 0) {
        val clearingEvents = confirmClearPage == 1
        RayConfirmDialog(
            title = I18n.tr(if (clearingEvents) "Очистить события VPN?" else "Очистить журнал трафика?"),
            message = if (clearingEvents) {
                "Будут удалены последние события VPN, сохранённые в памяти процесса. Список нельзя восстановить."
            } else {
                "Будут удалены записи истории трафика. Это не меняет правила маршрутизации, настройки VPN или выбранный сервер."
            },
            confirmLabel = I18n.tr("Очистить"),
            destructive = true,
            onConfirm = {
                if (clearingEvents) VpnEventHistory.clear() else ConnLog.clear()
                confirmClearPage = -1
            },
            onDismiss = { confirmClearPage = -1 }
        )
    }
    pick?.let { e ->
        val base = if (e.isIp) e.host else ConnLog.baseDomain(e.host)
        var scope by remember(e.host) { mutableIntStateOf(0) }
        var act by remember(e.host) { mutableIntStateOf(if (e.out == 0) 1 else 0) }
        RayDialogFrame(
            title = e.host,
            onDismiss = { pick = null },
            content = {
                Text(I18n.tr("Сейчас: {0}. Добавить правило в профиль «{1}»:".replace("{0}", I18n.tr(outNames[e.out])).replace("{1}", I18n.tr(AppState.profileName(AppState.activeProfile)))), style = MaterialTheme.typography.bodySmall)
                if (!e.isIp && base != e.host) Segmented(listOf(I18n.tr("Весь {0}".replace("{0}", base)), I18n.tr("Только этот адрес")), scope) { scope = it }
                Segmented(listOf(I18n.tr("Через VPN"), I18n.tr("Напрямую"), I18n.tr("Блок")), act) { act = it }
            },
            confirmButton = { TextButton({
                if (AppState.activeProfile == "sub") AppState.message = I18n.tr("Профиль из подписки менять нельзя: сделайте копию в «Маршрутах»")
                else {
                    val value = if (e.isIp) e.host else if (scope == 0 || base == e.host) base else e.host
                    val kind = if (e.isIp) 1 else 0
                    AppState.rules.removeAll { it.kind == kind && it.value == value }
                    AppState.rules += Rule(kind, value, act); AppState.save()
                    if (RayVpnService.connected) RayVpnService.requestReload()
                    AppState.message = I18n.tr("Правило добавлено: {0} · {1}".replace("{0}", value).replace("{1}", I18n.tr(listOf("через VPN", "напрямую", "блок")[act])))
                }
                pick = null
            }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Новое правило")) } },
            dismissButton = { TextButton({ pick = null }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Отмена")) } }
        )
    }
}

