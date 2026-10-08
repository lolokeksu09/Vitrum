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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.GameDialog() {
    if (!AppState.showGame) return
    var pickApps by remember { mutableStateOf(false) }
    val live = AppState.gameLive
    val res = AppState.gameResults
    fun col(ms: Int) = if (ms < 0) Pal.Red else if (ms < 80) Pal.Green else if (ms < 160) Pal.Amber else Pal.Red
    Dialog({ AppState.showGame = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            AuroraBackground(connectionColor(), animate = false)
            Column(Modifier.riseIn().fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                PageHeader("Игровой режим", { AppState.showGame = false })
                Panel(Modifier.padding(top = 4.dp)) {
                    SwitchRow("Игровой режим", "Живой пинг, джиттер и потери через туннель (идут и в уведомление). UDP разрешён даже при «Только TCP», на соединении с сервером включён TCP NoDelay (небольшой выигрыш).", AppState.gameMode) { on ->
                        AppState.gameMode = on; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
                    if (AppState.gameMode) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!RayVpnService.connected) Text("Подключитесь к серверу: пинг считается по рабочему туннелю.", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                            else if (live == null) Text("Замеряю…", style = MaterialTheme.typography.bodySmall)
                            else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (live.last >= 0) "${live.last} мс" else "нет", style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = col(live.last))
                                    Spacer(Modifier.width(16.dp))
                                    Column { Text(if (live.avg >= 0) "среднее ${live.avg} мс" else "нет ответов", style = MaterialTheme.typography.bodyMedium); Text("джиттер ${live.jitter} мс · потери ${live.loss}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                val sm = live.samples; val ok = sm.filter { it >= 0 }; val top = maxOf(100, (ok.maxOrNull() ?: 100)).toFloat()
                                val lineCol = col(live.avg)
                                Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                                    for (k in 1..3) drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1f)
                                    if (sm.size >= 2) {
                                        val dx = size.width / (sm.size - 1)
                                        var prev: Offset? = null
                                        sm.forEachIndexed { i, v ->
                                            if (v < 0) { drawCircle(Pal.Red, 3.dp.toPx(), Offset(i * dx, size.height - 3.dp.toPx())); prev = null }
                                            else {
                                                val p = Offset(i * dx, size.height - 6.dp.toPx() - (v / top) * (size.height - 12.dp.toPx()))
                                                // мягкое свечение под линией, потом сама линия
                                                prev?.let { drawLine(lineCol.copy(alpha = 0.22f), it, p, 7.dp.toPx(), StrokeCap.Round); drawLine(lineCol, it, p, 2.5.dp.toPx(), StrokeCap.Round) }
                                                prev = p
                                            }
                                        }
                                        prev?.let { drawCircle(lineCol.copy(alpha = 0.3f), 7.dp.toPx(), it); drawCircle(lineCol, 3.5.dp.toPx(), it) }
                                    }
                                }
                            }
                            OutlinedTextField(AppState.gameTarget, { AppState.gameTarget = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Цель замера") },
                                supportingText = { Text("http://адрес или хост[:порт] веб-сервера: замеряется задержка HTTP-запросов по открытому туннелю. Игровые UDP-порты так измерить нельзя.") })
                        }
                    }
                }
                GroupTitle("Тест серверов для игр")
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Для каждого из 10 лучших серверов (сначала избранные) делает 12 замеров по уже открытому туннелю: задержка, джиттер, потери. Подключаться к VPN не нужно.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button({ Pinger.gameTest() }, Modifier.fillMaxWidth(), enabled = !AppState.gameBusy && AppState.servers.isNotEmpty(), shape = RoundedCornerShape(14.dp)) {
                                if (AppState.gameBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                                Text(if (AppState.gameBusy) "Идёт тест…" else "Запустить тест") }
                        res.forEachIndexed { i, r ->
                            Row(Modifier.fillMaxWidth().clickable { AppState.selectedId = r.id; AppState.save(); AppState.message = "Выбран: ${r.name}" }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (i == 0 && r.avg >= 0) { Icon(RayIcons.StarFilled, null, Modifier.size(14.dp), tint = Pal.Amber); Spacer(Modifier.width(5.dp)) }
                                        Text(r.name.let { splitFlag(it).second }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(if (r.avg >= 0) "джиттер ${r.jitter} мс · потери ${r.loss}% · мин ${r.min}/макс ${r.max}" else "нет ответа" + if (r.note.isNotBlank()) " (${r.note})" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (r.avg >= 0) "${r.avg} мс" else "—", color = col(r.avg), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (res.isNotEmpty() && res.first().avg >= 0) TextButton({ AppState.selectedId = res.first().id; AppState.save(); AppState.message = "Выбран лучший: ${res.first().name}" }) { Text("Выбрать лучший сервер") }
                    }
                }
                GroupTitle("Игры на телефоне")
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Выберите игры и пустите через VPN только их или, наоборот, мимо VPN. Выбрано программ: ${AppState.apps.size}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton({ val g = installedGames(); g.forEach { if (it !in AppState.apps) AppState.apps += it }; AppState.save(); AppState.message = if (g.isEmpty()) "Игры с категорией «Игры» не найдены, выберите вручную" else "Добавлено игр: ${g.size}" }, Modifier.weight(1f)) { Text("Найти игры") }
                            FilledTonalButton({ pickApps = true }, Modifier.weight(1f)) { Text("Выбрать вручную") }
                        }
                        Segmented(listOf("Все", "Только выбранные", "Кроме выбранных"), AppState.appMode) { AppState.appMode = it; AppState.save() }
                        Text("«Только выбранные» значит, что через VPN идут лишь отмеченные программы, остальное напрямую. Применяется при следующем подключении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (pickApps) AppPicker { pickApps = false }
}

