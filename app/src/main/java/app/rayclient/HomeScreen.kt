@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** Large, native single-action control. The label always matches the real VPN action. */
@Composable
internal fun MainActivity.PowerButton(
    busy: Boolean,
    on: Boolean,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val foreground = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = { haptic.tap(HapticFeedbackType.LongPress); onClick() },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics { contentDescription = I18n.tr(desc); role = Role.Button },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.primary,
            contentColor = foreground
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = foreground
            )
        } else {
            Icon(RayIcons.Plug, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(I18n.tr(desc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Колонка панели скорости: крупное число и живой график за последние 40 секунд. */
@Composable
private fun SpeedCol(mod: Modifier, label: String, value: String, color: Color, history: List<Long>) {
    Column(mod.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(top = 6.dp)) {
            if (history.size < 2) return@Canvas
            val mx = (history.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
            val dx = size.width / (history.size - 1)
            val line = Path()
            history.forEachIndexed { k, v ->
                val x = k * dx; val y = size.height - (v / mx) * size.height
                if (k == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            drawPath(line, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Secondary actions retain their callbacks and a 48dp native touch target. */
@Composable
private fun QuickAction(mod: Modifier, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, enabled = enabled, modifier = mod.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun MainActivity.HomeTab(toggle: () -> Unit, quick: () -> Unit, still: Boolean = false, openServers: () -> Unit) {
    val act = this
    val connected = RayVpnService.connected
    val switching = RayVpnService.switching
    val busy = RayVpnService.busy || AppState.quickBusy
    val blocked = RayVpnService.blocked
    val err = RayVpnService.state.startsWith("Ошибка")
    val sel = AppState.servers.firstOrNull { it.id == AppState.selectedId }
    val presentation = HomeStatusPolicy.from(
        connected = connected,
        busy = busy,
        blocked = blocked,
        error = err,
        selecting = AppState.quickBusy,
        switching = switching
    )
    val buttonLabel = when {
        connected || blocked || switching || busy -> presentation.action
        AppState.servers.isEmpty() -> "Добавить сервер"
        sel == null -> "Выбрать сервер"
        else -> presentation.action
    }
    val statusTint = when {
        err || blocked -> Pal.Red
        busy || switching -> Pal.Amber
        connected -> Pal.Green
        else -> MaterialTheme.colorScheme.outline
    }
    // Скорость считает служба Speed; здесь обновляется только таймер активной сессии.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected, switching) {
        while (connected && !switching) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val downHist = Speed.down
    val upHist = Speed.up
    val down = downHist.lastOrNull() ?: 0L
    val up = upHist.lastOrNull() ?: 0L
    val total = Speed.total
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = LocalTabBottom.current)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.padding(end = 10.dp).size(34.dp))
            Text(BRAND, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            val shield by remember { derivedStateOf { Shield.score(Shield.evaluate(act)) } }   // пересчёт только при смене настроек, а не каждую секунду
            val shieldScore = shield.value
            // красным плашка бывает только при критичной проблеме (утечка IP, чужой открытый порт); низкая оценка без неё — янтарная
            val sc = if (shield.capReason != null) Pal.Red else if (shieldScore >= 8) Pal.Green else Pal.Amber
            Pill({ AppState.showShield = true }, Tone.fill, Modifier.padding(end = 4.dp)) {
                Icon(painterResource(R.drawable.ic_shield), "Щит приватности", Modifier.size(15.dp), tint = sc); Spacer(Modifier.width(5.dp))
                Text("Щит", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            val ks = AppState.killSwitchEffective()
            Pill({ AppState.killSwitch = !AppState.killSwitchEffective(); AppState.scenarioKs = 0; AppState.save() },
                if (ks) Pal.Green.copy(alpha = 0.16f) else Tone.fill) {
                Icon(RayIcons.Lock, "Kill switch", Modifier.size(15.dp), tint = if (ks) Pal.Green else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel(Modifier.padding(top = 16.dp, bottom = 10.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(9.dp).clip(CircleShape).background(statusTint)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Состояние соединения",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.3.sp
                    )
                }
                Text(
                    I18n.tr(presentation.title),
                    Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    I18n.tr(if (err) RayVpnService.state.removePrefix("Ошибка: ") else presentation.subtitle),
                    Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (err || blocked) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (connected && !switching && RayVpnService.since > 0 && tick >= RayVpnService.since) {
                    val sec = (tick - RayVpnService.since) / 1000
                    Text(
                        "%02d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60),
                        Modifier.padding(top = 9.dp),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (RayVpnService.hint.isNotBlank()) {
                    Text(
                        RayVpnService.hint,
                        Modifier.padding(top = 10.dp),
                        color = Pal.Amber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(17.dp))
                PowerButton(
                    busy = busy || switching,
                    on = connected || blocked || switching,
                    desc = buttonLabel,
                    enabled = !AppState.quickBusy
                ) {
                    when {
                        AppState.servers.isEmpty() -> AppState.showAdd = true
                        sel == null && !connected && !blocked && !busy && !switching -> openServers()
                        else -> toggle()
                    }
                }
            }
        }

        if (AppState.servers.isEmpty()) {
            WelcomePanel()
        } else {
            Panel(Modifier.padding(bottom = 10.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { openServers() }
                        .padding(horizontal = 16.dp, vertical = 15.dp)
                ) {
                    Text(
                        "Сервер",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (flag, nm) = splitFlag(sel?.name ?: "")
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (sel == null) "+" else flag, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (sel == null) "Нужно выбрать сервер" else nm,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (sel == null) "Откройте список серверов"
                                else chipsOf(sel.link).joinToString(" · ") { I18n.tr(it.first) },
                                Modifier.padding(top = 5.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sel != null) Health.stats(sel.link)
                                ?.takeIf { it.n >= 3 && HealthUi.version >= 0 }?.let { st ->
                                    Text(
                                        "Стабильность за сутки: ${(st.uptime * 100).roundToInt()}%",
                                        Modifier.padding(top = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                        }
                        Icon(
                            RayIcons.Chevron, null,
                            Modifier.padding(start = 8.dp).size(20.dp).rotate(-90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Secondary entry points stay below the connection action and server choice.
        Panel(Modifier.padding(bottom = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (connected) {
                    QuickAction(
                        Modifier.weight(1f),
                        if (AppState.myIp.isBlank()) "Мой IP" else AppState.myIp,
                        enabled = !busy && !switching
                    ) {
                        Thread {
                            val r = HeadProbe.get("http://api.ipify.org", Pinger.connector(Pinger.probePath))
                            AppState.myIp = if (r.ms >= 0) {
                                Regex("""\d{1,3}(\.\d{1,3}){3}|[0-9a-fA-F:]{6,}""")
                                    .find(r.body)?.value ?: "не разобрал ответ"
                            } else "ошибка: ${r.note}"
                        }.start()
                    }
                } else {
                    QuickAction(
                        Modifier.weight(1f),
                        "Авто-выбор",
                        enabled = !busy && !switching && AppState.servers.isNotEmpty(),
                        onClick = quick
                    )
                }
                QuickAction(Modifier.weight(1f), "Журнал") { AppState.showJournal = true }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { AppState.showGame = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_gamepad), null, Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text("Игровой режим", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                val gl = AppState.gameLive
                Text(
                    if (!AppState.gameMode) "выкл"
                    else if (gl != null && gl.avg >= 0) "${gl.avg} мс" else "вкл",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (AppState.gameMode) Pal.Green else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Manual HTTP evidence only; never imply device-wide leak protection.
        AnimatedVisibility(connected && !busy && !blocked && !switching) {
            val proof = ConnectionEvidence.snapshot
            val fresh = EvidencePolicy.isCurrent(
                proof, connected, RayVpnService.since, AppState.selectedId,
                ConnectionEvidence.token, tick
            )
            val checking = fresh && proof?.outcome == ProbeOutcome.CHECKING
            val reachable = fresh && proof?.outcome == ProbeOutcome.REACHABLE
            val headline = when {
                !fresh -> "Доступ не проверен"
                checking -> "Проверка доступа…"
                reachable -> "HTTP-ответ получен"
                else -> "HTTP-ответ не получен"
            }
            val detail = when {
                !fresh -> "Xray запущен · проверка по запросу"
                checking -> "Запрос через Xray"
                reachable -> "${proof!!.elapsedMs} мс · ${(tick - proof.atMs) / 1000} с назад"
                else -> "Не удалось получить HTTP-ответ через Xray"
            }
            Panel(Modifier.padding(bottom = 10.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (reachable) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (reachable) Pal.Green else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(headline, style = MaterialTheme.typography.titleSmall)
                            Text(detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Только HTTP через Xray. DNS, IPv6, другие приложения и утечки не проверены.",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { ConnectionEvidence.check() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                        enabled = !checking
                    ) {
                        Text(if (checking) "Проверяю…" else if (fresh) "Проверить снова" else "Проверить доступ")
                    }
                }
            }
        }
        AnimatedVisibility(connected && !switching, enter = fadeIn(tween(400)) + expandVertically(tween(400)), exit = fadeOut(tween(200)) + shrinkVertically(tween(200))) {
            Panel(Modifier.padding(bottom = 10.dp)) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    SpeedCol(Modifier.weight(1f), "↓ Загрузка", speed(down), Pal.Green, downHist.toList())
                    Box(Modifier.padding(vertical = 14.dp).width(1.dp).fillMaxHeight().background(Tone.divider))
                    SpeedCol(Modifier.weight(1f), "↑ Отдача", speed(up), Pal.Blue, upHist.toList())
                    Box(Modifier.padding(vertical = 14.dp).width(1.dp).fillMaxHeight().background(Tone.divider))
                    Column(Modifier.weight(0.8f).padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                        Text("За сессию", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(size(total), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, maxLines = 1)
                        // за сегодня (все сеансы), из статистики трафика
                        val dayTotal = TrafficLog.tick.let { TrafficLog.lastDays(1).let { p -> p.first + p.second } }
                        Text("Сегодня ${size(dayTotal)}", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }

    }
}

@Composable
internal fun MainActivity.WelcomePanel() = Panel(Modifier.padding(bottom = 6.dp)) {
    Column(Modifier.padding(18.dp)) {
        Text("Добавьте сервер", style = MaterialTheme.typography.titleLarge)
        Text("Добавьте подписку или сервер. Скопируйте ссылку из бота или панели и вставьте её одним нажатием.", Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({ val t = clipboardText(); if (t.isBlank()) AppState.message = "В буфере обмена пусто" else AppState.addInput(t) },
            Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp)) { Text("Вставить из буфера") }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ scanCamera() }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("QR камерой") }
            FilledTonalButton({ scanImage() }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("QR из фото") }
        }
        TextButton({ AppState.showAdd = true }) { Text("Ввести самому") }
    }
}

// ================================================================ Серверы

