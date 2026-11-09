@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun GameMetric(modifier: Modifier, label: String, value: String, valueColor: Color) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(I18n.tr(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(I18n.tr(value), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun MainActivity.GameDialog() {
    if (!AppState.showGame) return
    var pickApps by remember { mutableStateOf(false) }
    var selectionBlocked by remember { mutableStateOf(false) }
    fun choose(result: GameRow) {
        when (chooseServerFromUi(result.id)) {
            ServerChoiceDecision.REQUIRE_DISCONNECT -> selectionBlocked = true
            ServerChoiceDecision.SELECT -> AppState.message = I18n.tr("Выбран: ${result.name}")
            else -> Unit
        }
    }
    val live = AppState.gameLive
    val res = AppState.gameResults
    fun col(ms: Int) = if (ms < 0) Pal.Red else if (ms < 80) Pal.Green else if (ms < 160) Pal.Amber else Pal.Red
    Dialog({ AppState.showGame = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                PageHeader(I18n.tr("Игровой режим"), { AppState.showGame = false })
                Panel(Modifier.padding(top = 4.dp)) {
                    SwitchRow(I18n.tr("Включить"), I18n.tr("Живой пинг, джиттер и потери через туннель (идут и в уведомление). UDP разрешён даже при «Только TCP», на соединении с сервером включён TCP NoDelay (небольшой выигрыш)."), AppState.gameMode) { on ->
                        AppState.gameMode = on; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
                    if (AppState.gameMode) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!RayVpnService.connected) Text(I18n.tr("Подключитесь к серверу: пинг считается по рабочему туннелю."), style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                            else if (live == null) Text(I18n.tr("Замеряю…"), style = MaterialTheme.typography.bodySmall)
                            else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GameMetric(
                                        Modifier.weight(1f),
                                        "Пинг",
                                        if (live.last >= 0) "${live.last} мс" else "—",
                                        col(live.last)
                                    )
                                    GameMetric(
                                        Modifier.weight(1f),
                                        "Джиттер",
                                        "${live.jitter} мс",
                                        MaterialTheme.colorScheme.onSurface
                                    )
                                    GameMetric(
                                        Modifier.weight(1f),
                                        "Потери",
                                        "${live.loss}%",
                                        if (live.loss == 0) Pal.Green else Pal.Amber
                                    )
                                }
                                Text(
                                    I18n.tr(if (live.avg >= 0) "Среднее: ${live.avg} мс" else "Нет ответов"),
                                    Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val sm = live.samples; val ok = sm.filter { it >= 0 }; val top = maxOf(100, (ok.maxOrNull() ?: 100)).toFloat()
                                val lineCol = col(live.avg)
                                val gridColor = MaterialTheme.colorScheme.outlineVariant
                                Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                                    for (k in 1..3) drawLine(gridColor, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1f)
                                    if (sm.size >= 2) {
                                        val dx = size.width / (sm.size - 1)
                                        var prev: Offset? = null
                                        sm.forEachIndexed { i, v ->
                                            if (v < 0) { drawCircle(Pal.Red, 3.dp.toPx(), Offset(i * dx, size.height - 3.dp.toPx())); prev = null }
                                            else {
                                                val p = Offset(i * dx, size.height - 6.dp.toPx() - (v / top) * (size.height - 12.dp.toPx()))
                                                prev?.let { drawLine(lineCol, it, p, 2.dp.toPx(), StrokeCap.Round) }
                                                prev = p
                                            }
                                        }
                                        prev?.let { drawCircle(lineCol, 3.dp.toPx(), it) }
                                    }
                                }
                            }
                            OutlinedTextField(AppState.gameTarget, { AppState.gameTarget = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.tr("Цель замера")) },
                                supportingText = { Text(I18n.tr("http://адрес или хост[:порт] веб-сервера: замеряется задержка HTTP-запросов по открытому туннелю. Игровые UDP-порты так измерить нельзя.")) })
                        }
                    }
                }
                GroupTitle(I18n.tr("Тест серверов для игр"))
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(I18n.tr("Для каждого из 10 лучших серверов (сначала избранные) делает 12 замеров по уже открытому туннелю: задержка, джиттер, потери. Подключаться к VPN не нужно."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button({ Pinger.gameTest() }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.gameBusy && AppState.servers.isNotEmpty(), shape = RoundedCornerShape(14.dp)) {
                                if (AppState.gameBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                                Text(I18n.tr(if (AppState.gameBusy) "Идёт тест…" else "Запустить тест")) }
                        res.forEachIndexed { i, r ->
                            val isSelected = AppState.selectedId == r.id
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .semantics { selected = isSelected }
                                    .clickable(role = Role.Button) { choose(r) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (i == 0 && r.avg >= 0) { Icon(RayIcons.StarFilled, null, Modifier.size(14.dp), tint = Pal.Amber); Spacer(Modifier.width(5.dp)) }
                                        Text(splitFlag(r.name).second, style = MaterialTheme.typography.titleSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(if (r.avg >= 0) I18n.tr("джиттер ${r.jitter} мс · потери ${r.loss}% · мин ${r.min}/макс ${r.max}") else I18n.tr("нет ответа") + if (r.note.isNotBlank()) " (${I18n.tr(r.note)})" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSelected) {
                                    Icon(RayIcons.Check, null, Modifier.padding(end = 6.dp).size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(if (r.avg >= 0) I18n.tr("${r.avg} мс") else "—", color = col(r.avg), fontWeight = FontWeight.Bold)
                            }
                        }
                        res.firstOrNull()?.takeIf { it.avg >= 0 && it.id != AppState.selectedId }?.let { best ->
                            TextButton({ choose(best) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(I18n.tr(if (RayVpnService.connected) "Переключиться на лучший сервер" else "Выбрать лучший сервер"))
                            }
                        }
                    }
                }
                GroupTitle(I18n.tr("Игры на телефоне"))
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(I18n.tr("Выберите игры и пустите через VPN только их или, наоборот, мимо VPN. Выбрано программ: ${AppState.apps.size}.")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton({ val g = installedGames(); g.forEach { if (it !in AppState.apps) AppState.apps += it }; AppState.save(); AppState.message = I18n.tr(if (g.isEmpty()) "Игры с категорией «Игры» не найдены, выберите вручную" else "Добавлено игр: ${g.size}") }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(I18n.tr("Найти игры")) }
                            FilledTonalButton({ pickApps = true }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(I18n.tr("Выбрать вручную")) }
                        }
                        Segmented(listOf("Все", "Только выбранные", "Кроме выбранных").map(I18n::tr), AppState.appMode) { AppState.appMode = it; AppState.save() }
                        Text(I18n.tr("«Только выбранные» значит, что через VPN идут лишь отмеченные программы, остальное напрямую. Применяется при следующем подключении."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (selectionBlocked) ServerChoiceBlockedDialog { selectionBlocked = false }
    if (pickApps) AppPicker { pickApps = false }
}

