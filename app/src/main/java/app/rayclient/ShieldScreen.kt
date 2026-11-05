@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.ShieldDialog() {
    if (!AppState.showShield) return
    val checks = Shield.evaluate(this)
    val sc = Shield.score(checks)
    val col = if (sc.value >= 8) Pal.Green else if (sc.value >= 5) Pal.Amber else Pal.Red
    var confirm by remember { mutableStateOf<Act?>(null) }
    var strengthen by remember { mutableStateOf<List<Act>?>(null) }   // «Усилить защиту»: ждёт подтверждения
    var confirmDirect by remember { mutableStateOf(false) }
    var dnsRouteSnapshot by remember { mutableStateOf<DnsAuditResult?>(null) }
    var encryptedDnsSnapshot by remember { mutableStateOf<EncryptedDnsRouteResult?>(null) }
    var privateDnsSnapshot by remember { mutableStateOf<AndroidPrivateDnsMode?>(null) }
    var probeClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            probeClock = System.currentTimeMillis()
        }
    }
    val familyProof = FamilyHttpProbe.current(probeClock)

    Dialog({ AppState.showShield = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EdgeToEdgeDialog()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                PageHeader("Щит приватности", { AppState.showShield = false })
                val lit = sc.value.coerceIn(0, 10)
                Text("Оценка защиты: $lit из 10", Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleLarge, color = col)
                Text("Учтено для оценки: ${sc.verified} из ${sc.scored} пунктов", Modifier.padding(top = 4.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val unknownCount = checks.count { it.st == St.UNKNOWN }
                if (unknownCount > 0) Text(
                    "Не проверено: $unknownCount. Эти пункты не прибавляют баллы.",
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = Pal.Amber
                )
                Text("Оценка настроек и ограниченных локальных проверок. Она не подтверждает безопасность DNS, IPv4/IPv6 других приложений или отсутствие утечек.",
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sc.preliminary) Notice("Оценка предварительная: часть пунктов не проверена или устарела.", Pal.Amber)
                sc.capReason?.let { Notice(it, Pal.Red) }
                Button({ Shield.runFull() }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !AppState.shieldBusy && familyProof?.running != true, shape = RoundedCornerShape(16.dp)) {
                    if (AppState.shieldBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (AppState.shieldBusy || familyProof?.running == true) "Проверяю…" else "Проверить порты и IPv4/IPv6")
                }
                Text("Проверка: типичные TCP-порты телефона, затем IPv4/IPv6 через Xray. Прямой IP без вашего отдельного согласия не запрашивается.",
                    Modifier.padding(top = 6.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (RayVpnService.connected && !RayVpnService.busy && !RayVpnService.blocked) {
                    TextButton({ confirmDirect = true }, enabled = !AppState.shieldBusy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Отдельно сравнить IP вне VPN")
                    }
                }
                Panel(Modifier.padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Результаты IPv4 / IPv6 через Xray", style = MaterialTheme.typography.titleSmall)
                        Text("Только по нажатию: два запроса к api4.ipify.org и api6.ipify.org через приватный Unix-сокет Xray. HTTP без TLS после выхода с сервера. Публичный IP не сохраняется и не показывается.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val ready = RayVpnService.connected && !RayVpnService.busy && !RayVpnService.blocked &&
                            RayVpnService.since > 0L && Pinger.probePath.isNotBlank()
                        FilledTonalButton({ FamilyHttpProbe.check() }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = ready && familyProof?.running != true) {
                            Text(if (familyProof?.running == true) "Проверяю IPv4 и IPv6…"
                                else if (familyProof != null) "Повторить IPv4 / IPv6" else "Проверить IPv4 / IPv6")
                        }
                        if (!ready) Text("Подключите VPN для проверки через Xray.",
                            style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        val v4 = familyProof?.ipv4
                        val v6 = familyProof?.ipv6
                        Text("IPv4 HTTP: " + when {
                            v4?.status == FamilyHttpStatus.REACHABLE -> "ответ через Xray · ${v4.ms} мс"
                            familyProof?.running == true -> "проверяется"
                            v4?.issue != null -> "UNKNOWN · ${v4.issue.label}"
                            else -> "UNKNOWN · не проверено или результат устарел"
                        }, style = MaterialTheme.typography.bodyMedium)
                        Text("IPv6 HTTP: " + when {
                            v6?.status == FamilyHttpStatus.REACHABLE -> "ответ через Xray · ${v6.ms} мс"
                            familyProof?.running == true -> "проверяется"
                            v6?.issue != null -> "UNKNOWN · ${v6.issue.label}"
                            else -> "UNKNOWN · не проверено или результат устарел"
                        }, style = MaterialTheme.typography.bodyMedium)
                        if (AppState.blockV6) Text(
                            "Настройка «Не использовать IPv6» включена. Тест через служебный Xray-сокет не проверяет IPv6-трафик других приложений.",
                            style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        Text("Политика маршрутов DNS 53 (по настройкам)", style = MaterialTheme.typography.titleSmall)
                        Text(DnsRoutePolicy.describe(RoutingOptions(
                            rules = AppState.rules.toList(), doh = AppState.dnsDoh,
                            blockV6 = AppState.blockV6, fakeDns = AppState.fakeDns,
                            defaultAction = AppState.defaultAction
                        )), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton({
                            // Snapshot is taken only on explicit action; no network requests
                            // for route predictions or hidden Private DNS hostname storage.
                            val options = runCatching { AppState.routingOptions() }.getOrNull()
                            val resolvers = AppState.dnsIps()
                            dnsRouteSnapshot = options?.let { DnsRouteAudit.inspect(it, resolvers) }
                            encryptedDnsSnapshot = options?.let { EncryptedDnsRouteAudit.inspect(it, resolvers) }
                            privateDnsSnapshot = AndroidPrivateDnsObservation.read(this@ShieldDialog)
                        }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Проверить DNS 53/853 и Private DNS")
                        }
                        dnsRouteSnapshot?.let { dns ->
                            Text("DNS UDP/53: ${dns.udp.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dns.udp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text("DNS TCP/53: ${dns.tcp.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dns.tcp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text("Адресов DNS в расчёте: ${dns.resolvers}. Возможные ограничения — пользовательские правила direct, геобазы и приложения вне VPN.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Это снимок текущих настроек, а не маршрутов уже запущенного VPN. Фактическая передача DNS и отсутствие утечек не проверяются.",
                                style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        }
                        encryptedDnsSnapshot?.let { secure ->
                            Text("DNS-over-TLS TCP/853: ${secure.tcp.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (secure.tcp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text("DNS-over-QUIC UDP/853: ${secure.udp.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (secure.udp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text("Оценка только для ${secure.resolvers} выбранных IP DNS. Системный Private DNS и приложения могут использовать другие адреса.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        privateDnsSnapshot?.let { privateDns ->
                            Text("Android Private DNS: ${privateDns.label}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Показана активная сеть самого VITRUM — приложение может быть исключено из VPN. Домен Private DNS не считывается в отчёт. Это не проверка маршрута других приложений.",
                                style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        }
                        Text("DNS A/AAAA и утечки: UNKNOWN",
                            style = MaterialTheme.typography.bodyMedium, color = Pal.Amber)
                        Text("Успех HTTP подтверждает ответ внешнего узла через служебный Xray-сокет, но не маршрут других приложений, DNS-запросов или отсутствие утечек. Ошибка или таймаут остаются UNKNOWN. Для проверки DNS запустите отдельный тест в браузере, который включён в Android VPN.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val auditHistory = ShieldAuditHistory.entries
                if (auditHistory.isNotEmpty()) {
                    Panel(Modifier.padding(top = 10.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("История диагностик", style = MaterialTheme.typography.titleSmall)
                            Text("До 12 проверок только в памяти процесса. Не сохраняются IP, порты, серверы и ключи. Время проверок остаётся в отчёте.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (auditHistory.size >= 2) {
                                val changed = ShieldAuditPolicy.diff(auditHistory[auditHistory.lastIndex - 1], auditHistory.last())
                                Text(if (changed.isEmpty()) "С прошлой проверки категории результатов не изменились."
                                    else "Изменения с прошлой проверки: " + changed.joinToString("; "),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            val fmtAudit = remember { java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.getDefault()) }
                            auditHistory.asReversed().take(6).forEach { entry ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
                                Text(fmtAudit.format(java.util.Date(entry.atMs)), style = MaterialTheme.typography.labelMedium)
                                Text("Порты: ${entry.ports.label} · IPv4: ${entry.ipv4.label} · IPv6: ${entry.ipv6.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ copy(ShieldAuditPolicy.report(auditHistory), sensitive = false) },
                                    modifier = Modifier.weight(1f)) { Text("Скопировать отчёт") }
                                TextButton({ ShieldAuditHistory.clear() }) { Text("Очистить") }
                            }
                        }
                    }
                }
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
                        list.forEach { ch ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                    val (sym, c) = when (ch.st) { St.OK -> RayIcons.Check to Pal.Green; St.WARN -> RayIcons.Alert to Pal.Amber; St.BAD -> RayIcons.Close to Pal.Red; St.UNKNOWN -> RayIcons.Help to Pal.Gray; St.INFO -> RayIcons.Info to Pal.Gray }
                                    Icon(sym, null, Modifier.size(20.dp), tint = c)
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    if (confirmDirect) {
        AlertDialog(onDismissRequest = { confirmDirect = false },
            title = { Text("Сравнить IP вне VPN?") },
            text = { Text("VITRUM выполнит HTTPS-запрос к внешней службе из собственного процесса, исключённого из Android VPN. Служба увидит ваш прямой IP. Затем приложение сравнит его с IP через Xray. Равенство или различие адресов не доказывает отсутствие утечек других приложений.") },
            confirmButton = { TextButton({ Shield.runNet(compareDirectIp = true); confirmDirect = false }) { Text("Запросить прямой IP") } },
            dismissButton = { TextButton({ confirmDirect = false }) { Text("Отмена") } })
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
private fun TraceRow(title: String, detail: String, state: TraceState, word: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val (sym, c) = when (state) { TraceState.SEEN -> RayIcons.Alert to Pal.Amber; TraceState.CLEAN -> RayIcons.Check to Pal.Green; TraceState.UNKNOWN -> RayIcons.Help to Pal.Gray }
        Icon(sym, null, Modifier.size(20.dp), tint = c)
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
