@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.ShieldDialog() {
    if (!AppState.showShield) return
    val checks = Shield.evaluate(this)
    val sc = Shield.score(checks)
    val col = if (sc.value >= 8) Pal.Green else if (sc.value >= 5) Pal.Amber else Pal.Red
    val track = MaterialTheme.colorScheme.surfaceVariant
    var confirm by remember { mutableStateOf<Act?>(null) }
    var strengthen by remember { mutableStateOf<List<Act>?>(null) }   // «Усилить защиту»: ждёт подтверждения
    Dialog({ AppState.showShield = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            AuroraBackground(connectionColor(), animate = false)
            Column(Modifier.riseIn().fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                PageHeader("Щит приватности", { AppState.showShield = false })
                // шкала из 10 сегментов считается только из готовой оценки (логика Shield не меняется): набранные баллы, потерянные и непроверенные
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val lit = sc.value.coerceIn(0, 10)
                val fill by animateFloatAsState(if (shown) lit.toFloat() else 0f, tween(1100, easing = FastOutSlowInEasing), label = "gauge")
                val totalW = checks.filter { it.weight > 0 }.sumOf { it.weight }.coerceAtLeast(1)
                val unkW = checks.filter { it.st == St.UNKNOWN && it.weight > 0 }.sumOf { it.weight }
                val unk = minOf(10 - lit, (10.0 * unkW / totalW).roundToInt())
                val lostCol = if (checks.any { it.st == St.BAD }) Pal.Red else Pal.Amber
                val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                val gaugeDesc = I18n.tr("Оценка защиты: $lit из 10")
                Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp).clearAndSetSemantics { contentDescription = gaugeDesc }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(220.dp)) {
                        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.26f), Color.Transparent)), size.minDimension / 2)
                        val sw = 14.dp.toPx(); val pad = sw / 2 + 18.dp.toPx()
                        val tl = Offset(pad, pad); val sz = Size(size.width - 2 * pad, size.height - 2 * pad)
                        val gap = 4f; val seg = (270f - 9 * gap) / 10f
                        for (i in 0 until 10) {
                            val c = when {
                                i < lit -> androidx.compose.ui.graphics.lerp(neutral, col, (fill - i).coerceIn(0f, 1f))   // набрано
                                i < 10 - unk -> lostCol.copy(alpha = 0.55f)                                                // потеряно
                                else -> neutral                                                                           // не проверено
                            }
                            drawArc(c, 135f + i * (seg + gap), seg, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${fill.roundToInt()}", style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = col)
                        Text("/10", Modifier.padding(bottom = 8.dp, start = 2.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Проверено ${sc.verified} из ${sc.scored} пунктов", Modifier.fillMaxWidth().padding(bottom = 12.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sc.preliminary) Notice("Предварительная оценка: не все проверки выполнены. Запустите проверку сети.", Pal.Amber)
                sc.capReason?.let { Notice(it, Pal.Red) }
                Button({ Shield.runNet() }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !AppState.shieldBusy, shape = RoundedCornerShape(16.dp)) {
                    if (AppState.shieldBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (AppState.shieldBusy) "Проверяю…" else if (AppState.shieldNet == null) "Запустить проверку сети" else "Проверить снова")
                }
                Text("Сеть: быстрый просмотр локальных портов и запрос вашего IP на api.ipify.org (с VPN и без него).", Modifier.padding(top = 6.dp, bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val fixes = Shield.fixable(checks)
                if (fixes.isNotEmpty()) FilledTonalButton({ strengthen = fixes }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Усилить защиту") }
                val groups = listOf(
                    "Требует внимания" to checks.filter { it.st == St.BAD || it.st == St.WARN },
                    "Не проверено" to checks.filter { it.st == St.UNKNOWN },
                    "В порядке" to checks.filter { it.st == St.OK },
                    "К сведению" to checks.filter { it.st == St.INFO })
                groups.forEach { (name, list) ->
                    if (list.isNotEmpty()) {
                        GroupTitle(name)
                        list.forEachIndexed { k, ch ->
                            Panel(Modifier.riseIn(150 + 45 * k.coerceAtMost(8)).padding(bottom = 8.dp)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                    val (sym, c) = when (ch.st) { St.OK -> RayIcons.Check to Pal.Green; St.WARN -> RayIcons.Alert to Pal.Amber; St.BAD -> RayIcons.Close to Pal.Red; St.UNKNOWN -> RayIcons.Help to Pal.Gray; St.INFO -> RayIcons.Info to Pal.Gray }
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.copy(alpha = 0.40f), c.copy(alpha = 0.12f))))
                                        .border(1.dp, c.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Icon(sym, null, Modifier.size(17.dp), tint = c) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(ch.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            if (ch.weight > 0) Text("${ch.points}/${ch.weight}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = if (ch.st == St.OK) Pal.Green else c)
                                        }
                                        Text(ch.detail, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (ch.act != Act.NONE) TextButton({ if (ch.act == Act.DOH) confirm = ch.act else doAct(ch.act) }, contentPadding = PaddingValues(0.dp)) { Text(ch.actLabel) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (AppState.shieldNet?.ports?.isNotEmpty() == true) {
                    GroupTitle("Чьи это порты (root)")
                    Text(if (AppState.rootOn) "Нужен root: без него Android не даёт приложению узнать, кому принадлежит порт. Кнопка один раз запросит root-доступ, прочитает список сокетов и пакетов и ничего не изменит. Порты берутся из последней проверки сети."
                        else "Нужен root. Функция выключена: включите «Root-функции» в «Настройки → Сервис → Root».",
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (AppState.rootOn) FilledTonalButton({ PortOwners.run(this@ShieldDialog) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.ownersBusy, shape = RoundedCornerShape(16.dp)) {
                        if (AppState.ownersBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(if (AppState.ownersBusy) "Проверяю…" else "Узнать, чьи порты (root)")
                    }
                    if (AppState.rootOn) AppState.portOwners?.items?.forEach { o ->
                        Panel(Modifier.padding(top = 8.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Порт ${o.port}", style = MaterialTheme.typography.titleSmall)
                                Text(o.label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = if (o.self) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (o.self) Text("Это порт самого ${BRAND}: так быть не должно, сообщите об этом.", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Pal.Red)
                            }
                        }
                    }
                }
                GroupTitle("Что видят другие приложения")
                Text("Проверка без root: что о VPN может узнать любое приложение. Измеряется из процесса ${BRAND}, который сам идёт мимо туннеля, поэтому это вид приложения вне VPN, например банка, исключённого из туннеля. У конкретного приложения результат может отличаться. В оценку щита не входит.",
                    Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ VpnTraces.run(this@ShieldDialog) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.traceBusy, shape = RoundedCornerShape(16.dp)) {
                    if (AppState.traceBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (AppState.traceBusy) "Проверяю…" else if (AppState.traceResult == null) "Проверить, что видят другие приложения" else "Проверить снова")
                }
                AppState.traceResult?.let { tr ->
                    val (seen, measured) = VpnTraces.summary(tr.items)
                    Text("Признак VPN виден в $seen из $measured измеренных проверок.", Modifier.padding(top = 10.dp, bottom = 6.dp), style = MaterialTheme.typography.titleSmall,
                        color = if (seen > 0) Pal.Amber else Pal.Green)
                    if (tr.session != RayVpnService.since || !RayVpnService.connected) Notice("Результат относится к прошлому подключению. Проверьте снова.", Pal.Amber)
                    if (seen > 0) Text("Это не ошибка: система показывает приложениям, что VPN есть. ${BRAND} эти признаки не скрывает. Скрывают их внешние модули на телефоне с root. Исключение приложения из туннеля (например банка) направляет его трафик мимо VPN, но признаки VPN остаются.",
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    tr.items.forEach { t ->
                        TraceRow(t.title, t.detail, t.state, when (t.state) { TraceState.SEEN -> "Виден"; TraceState.CLEAN -> "Не виден"; TraceState.UNKNOWN -> "Не удалось измерить" })
                    }
                    GroupTitle("Сравнение с эталоном (root)")
                    Text(if (AppState.rootOn) "Root покажет, что в системе на самом деле: какие VPN-интерфейсы есть, и входит ли ${BRAND} в правила VPN. Так видно разницу между «признака нет» и «признак есть, но приложению закрыт». Только чтение."
                        else "Нужен root. Функция выключена: включите «Root-функции» в «Настройки → Сервис → Root».",
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (AppState.rootOn) {
                        FilledTonalButton({ VpnTruth.run() }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.truthBusy, shape = RoundedCornerShape(16.dp)) {
                            if (AppState.truthBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (AppState.truthBusy) "Проверяю…" else "Сравнить с эталоном (root)")
                        }
                        AppState.truthResult?.let { tt ->
                            if (tt.traceAt != tr.at) Notice("Сравнение относится к прошлой проверке. Запустите его снова.", Pal.Amber)
                            Spacer(Modifier.height(8.dp))
                            tt.notes.forEach { n -> TraceRow(n.title, n.detail, n.state, n.word) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    strengthen?.let { acts ->
        val names = acts.map { when (it) { Act.KILL -> "Kill switch"; Act.RECONNECT -> "Переподключение при смене сети"; else -> "Шифрованный DNS (DoH)" } }
        AlertDialog(onDismissRequest = { strengthen = null }, title = { Text("Усилить защиту?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Будут включены: ${names.joinToString(", ") { I18n.tr(it) }}.", style = MaterialTheme.typography.bodyMedium)
                if (Act.DOH in acts) Text("Функция новая. Запросы приложений будут разбираться внутри Xray по HTTPS. Если после включения пропадёт интернет, выключите её в «Настройки → Сеть и DNS».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton({ acts.forEach { doAct(it) }; AppState.message = "Защита усилена. Часть настроек применится при следующем подключении."; strengthen = null }) { Text("Включить") } },
            dismissButton = { TextButton({ strengthen = null }) { Text("Отмена") } })
    }
    confirm?.let { a ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Включить шифрованный DNS?") },
            text = { Text("Функция новая. Запросы приложений будут разбираться внутри Xray по HTTPS. Если после включения пропадёт интернет, выключите её в «Настройки → Сеть и DNS».") },
            confirmButton = { TextButton({ doAct(a); confirm = null }) { Text("Включить") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Отмена") } })
    }
}

/** Строка результата проверки: значок и цвет по состоянию, справа короткая пометка. */
@Composable
private fun TraceRow(title: String, detail: String, state: TraceState, word: String) = Panel(Modifier.padding(bottom = 8.dp)) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
        val (sym, c) = when (state) { TraceState.SEEN -> RayIcons.Alert to Pal.Amber; TraceState.CLEAN -> RayIcons.Check to Pal.Green; TraceState.UNKNOWN -> RayIcons.Help to Pal.Gray }
        Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.copy(alpha = 0.40f), c.copy(alpha = 0.12f))))
            .border(1.dp, c.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Icon(sym, null, Modifier.size(17.dp), tint = c) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(word, style = MaterialTheme.typography.labelMedium, color = c)
            }
            Text(detail, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Плашка с предупреждением в окне щита: значок и текст слева, мягкая заливка цвета предупреждения. */
@Composable
private fun Notice(text: String, color: Color) = Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp),
    verticalAlignment = Alignment.Top) {
    Icon(RayIcons.Alert, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(10.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
}
