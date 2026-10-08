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
import androidx.compose.ui.graphics.Brush
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

/**
 * Кнопка подключения. В покое это тёмная «линза» с цветным значком; при подключении (busy) по кольцу бежит дуга; когда VPN включён,
 * дуга замыкается в кольцо, а линза заливается цветом состояния. Всё рисуется на Canvas (без теней и шейдеров, которым нужен новый Android).
 */
@Composable
internal fun MainActivity.PowerButton(color: Color, busy: Boolean, on: Boolean, desc: String, still: Boolean = false, onClick: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "orb")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val breathe by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val wave by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "wave")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val lit by animateFloatAsState(if (on) 1f else 0f, tween(520), label = "lit")
    val sweep by animateFloatAsState(if (on) 360f else if (busy) 100f else 0f, tween(450, easing = FastOutSlowInEasing), label = "sweep")
    val haptic = LocalHapticFeedback.current
    val reduced = remember { Access.reducedMotion(this) }
    val bg = MaterialTheme.colorScheme.background
    // значок на залитой сфере: тёмный на светлой заливке и белый на тёмной (в светлой теме цвета состояний темнее)
    val lensA = if (Pal.dark) 0.62f else 0.34f; val lensB = if (Pal.dark) 0.86f else 0.58f   // в светлой теме линза плотнее, иначе она сливается с фоном
    val ink = if (color.luminance() > 0.25f) Color(0xFF0B1A16) else Color.White
    val glyph = lerp(lerp(color, Color.White, 0.25f), ink, lit)
    // сфера сообщает родителю меньшую высоту (ореол рисуется за границей), чтобы подпись стояла вплотную к кнопке
    Box(Modifier.size(300.dp).layout { m, c -> val p = m.measure(c); val cut = 56.dp.roundToPx(); layout(p.width, p.height - 2 * cut) { p.place(0, -cut) } },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2; val c = center
            // пока поверх открыта шторка серверов, анимации стоят: их значения не читаются, перерисовки нет
            val calm = still || reduced   // «Убрать анимации» в системе: дыхание и волны не рисуем (вращение при подключении остаётся: оно показывает, что идёт работа)
            val spin = if (still) 0f else spin; val breathe = if (calm) 0f else breathe; val wave = if (calm) 0f else wave
            val glow = (0.20f + 0.06f * breathe) * (1f - lit) + 0.42f * lit
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = glow), color.copy(alpha = glow * 0.25f), Color.Transparent), c, r), r, c)
            if (lit > 0.01f) listOf(wave, (wave + 0.5f) % 1f).forEach { p ->
                drawCircle(color.copy(alpha = (1f - p) * 0.40f * lit), r * (0.56f + 0.44f * p), c, style = Stroke(1.5.dp.toPx()))
            }
            val ringR = r * 0.66f
            drawCircle(color.copy(alpha = 0.22f + 0.10f * breathe * (1f - lit)), ringR, c, style = Stroke(1.5.dp.toPx()))
            if (sweep > 1f) rotate(if (busy && !on) spin else -90f, c) {
                val tl = Offset(c.x - ringR, c.y - ringR); val sz = Size(ringR * 2, ringR * 2)
                drawArc(color.copy(alpha = 0.28f), 0f, sweep, false, tl, sz, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color, 0f, sweep, false, tl, sz, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Box(Modifier.size(156.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.04f))), CircleShape)
            .semantics { contentDescription = I18n.tr(desc); role = Role.Button }
            .clickable(src, null, onClickLabel = desc, role = Role.Button) { haptic.tap(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val end = Offset(size.width, size.height)
                drawCircle(Brush.linearGradient(listOf(lerp(color, bg, lensA), lerp(color, bg, lensB)), Offset.Zero, end))
                drawCircle(Brush.linearGradient(listOf(lerp(color, Color.White, 0.40f), color, lerp(color, Color.Black, 0.40f)), Offset.Zero, end), alpha = lit)
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f + 0.26f * lit), Color.Transparent), startY = size.height * 0.05f, endY = size.height * 0.5f),
                    Offset(size.width * 0.2f, size.height * 0.06f), Size(size.width * 0.6f, size.height * 0.4f))
            }
            Canvas(Modifier.size(54.dp)) {
                val sw = 6.dp.toPx()
                drawArc(glyph, -55f, 290f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                drawLine(glyph, Offset(size.width / 2, sw / 2), Offset(size.width / 2, size.height * 0.48f), sw, StrokeCap.Round)
            }
        }
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
            val line = Path(); val fill = Path()
            history.forEachIndexed { k, v ->
                val x = k * dx; val y = size.height - (v / mx) * size.height
                if (k == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Кнопка-«капсула» под карточкой сервера: полупрозрачная заливка без рамки. */
@Composable
private fun GlassAction(mod: Modifier, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(mod.height(44.dp).clip(shape).background(if (enabled) Tone.fillStrong else Tone.fill)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
internal fun MainActivity.HomeTab(toggle: () -> Unit, quick: () -> Unit, still: Boolean = false, openServers: () -> Unit) {
    val act = this
    val connected = RayVpnService.connected; val busy = RayVpnService.busy || AppState.quickBusy
    val blocked = RayVpnService.blocked; val err = RayVpnService.state.startsWith("Ошибка")
    val color = connectionColor()
    val title = when { AppState.quickBusy -> "Подбираю сервер…"; blocked -> "Трафик заблокирован"; err -> "Ошибка"; connected -> "Подключено"; busy -> "Подключение…"; else -> "Не подключено" }
    // скорость и график считает служба (Speed): при смене вкладки они не обнуляются; здесь только часы для таймера
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected) { while (connected) { tick = System.currentTimeMillis(); delay(1000) } }
    val downHist = Speed.down; val upHist = Speed.up
    val down = downHist.lastOrNull() ?: 0L; val up = upHist.lastOrNull() ?: 0L; val total = Speed.total
    val sel = AppState.servers.firstOrNull { it.id == AppState.selectedId }
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = LocalTabBottom.current)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.padding(end = 10.dp).size(34.dp))
            Text(BRAND, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            val shield by remember { derivedStateOf { Shield.score(Shield.evaluate(act)) } }   // пересчёт только при смене настроек, а не каждую секунду
            val shieldScore = shield.value
            // красным плашка бывает только при критичной проблеме (утечка IP, чужой открытый порт); низкая оценка без неё — янтарная
            val sc = if (shield.capReason != null) Pal.Red else if (shieldScore >= 8) Pal.Green else Pal.Amber
            Pill({ AppState.showShield = true }, Tone.fill, Modifier.padding(end = 4.dp)) {
                Icon(painterResource(R.drawable.ic_shield), "Щит приватности", Modifier.size(15.dp), tint = sc); Spacer(Modifier.width(5.dp))
                Text("$shieldScore/10", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            val ks = AppState.killSwitchEffective()
            Pill({ AppState.killSwitch = !AppState.killSwitchEffective(); AppState.scenarioKs = 0; AppState.save() },
                if (ks) Pal.Green.copy(alpha = 0.16f) else Tone.fill) {
                Icon(RayIcons.Lock, "Kill switch", Modifier.size(15.dp), tint = if (ks) Pal.Green else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerButton(color, busy = busy, on = connected || blocked, desc = if (connected || blocked) "Отключиться" else "Подключиться", still = still) { if (sel != null || connected || blocked) toggle() else openServers() }
                AnimatedContent(title, transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 2 }) togetherWith (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 })
                }, label = "title") { t ->
                    // при подключении главный — таймер, а статус становится небольшой меткой с точкой цвета состояния
                    if (t == "Подключено") Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
                        Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
                    } else Text(t, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                if (connected && RayVpnService.since > 0 && tick >= RayVpnService.since) {
                    val sec = (tick - RayVpnService.since) / 1000
                    CappedFontScale(1.15f) { Text("%02d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60), Modifier.padding(top = 2.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), fontSize = 44.sp, lineHeight = 62.sp, fontWeight = FontWeight.W300,   // строка выше кегля, иначе нижняя часть цифр обрезается
                        overflow = TextOverflow.Visible, color = MaterialTheme.colorScheme.onBackground) }
                } else {
                    val sub = when {
                        err || blocked -> RayVpnService.state.removePrefix("Ошибка: ")
                        !busy && !connected -> "Нажмите, чтобы подключиться"
                        else -> ""
                    }
                    if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium, color = if (err || blocked) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (RayVpnService.hint.isNotBlank()) Text(RayVpnService.hint, Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp), color = Pal.Amber, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
        AnimatedVisibility(connected, enter = fadeIn(tween(400)) + expandVertically(tween(400)), exit = fadeOut(tween(200)) + shrinkVertically(tween(200))) {
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
        if (AppState.servers.isEmpty()) WelcomePanel() else Panel(Modifier.riseIn(150).padding(bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { openServers() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                val (flag, nm) = splitFlag(sel?.name ?: "")
                Box(Modifier.size(52.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.08f))))
                    .border(1.5.dp, Brush.sweepGradient(listOf(color, color.copy(alpha = 0.2f), color)), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (sel == null) "＋" else flag, fontSize = 26.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (sel == null) "Нужно выбрать сервер" else nm, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (sel == null) Text("Откройте список серверов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else chipsOf(sel.link).forEach { Tag(it.first, it.second) }
                    }
                    if (sel != null) Health.stats(sel.link)?.takeIf { it.n >= 3 && HealthUi.version >= 0 }?.let { st ->
                        Text("Стабильность за сутки: ${(st.uptime * 100).roundToInt()}%", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp).rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // две кнопки вместо трёх: «Мой IP» нужен только при подключении, «Авто-выбор» только без него
                if (connected) GlassAction(Modifier.weight(1f), if (AppState.myIp.isBlank()) "Мой IP" else AppState.myIp) {
                    Thread { val r = HeadProbe.get("http://api.ipify.org", Pinger.connector(Pinger.probePath))
                        AppState.myIp = if (r.ms >= 0) Regex("""\d{1,3}(\.\d{1,3}){3}|[0-9a-fA-F:]{6,}""").find(r.body)?.value ?: "не разобрал ответ" else "ошибка: ${r.note}" }.start()
                } else GlassAction(Modifier.weight(1f), "Авто-выбор", enabled = !busy && AppState.servers.isNotEmpty(), onClick = quick)
                GlassAction(Modifier.weight(1f), "Журнал") { AppState.showJournal = true }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Row(Modifier.fillMaxWidth().clickable { AppState.showGame = true }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_gamepad), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Text("Игровой режим", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                val gl = AppState.gameLive
                Text(if (!AppState.gameMode) "выкл" else if (gl != null && gl.avg >= 0) "${gl.avg} мс" else "вкл", style = MaterialTheme.typography.bodySmall, color = if (AppState.gameMode) Pal.Green else MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MainActivity.WelcomePanel() = Panel(Modifier.padding(bottom = 6.dp)) {
    Column(Modifier.padding(18.dp)) {
        Text("Добро пожаловать в $BRAND", style = MaterialTheme.typography.titleLarge)
        Text("VPN, который проверяет сам себя.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

