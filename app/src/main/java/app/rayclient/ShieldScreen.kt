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
    var confirmHistoryClear by remember { mutableStateOf(false) }
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
                PageHeader(I18n.tr("Щит приватности"), { AppState.showShield = false })
                val lit = sc.value.coerceIn(0, 10)
                val unknownCount = checks.count { it.st == St.UNKNOWN }
                Panel(Modifier.padding(top = 8.dp, bottom = 10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(I18n.tr("Оценка защиты"), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(I18n.tr("$lit / 10"), Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.headlineMedium, color = col,
                                    fontWeight = FontWeight.Bold)
                            }
                            Text(I18n.tr("{0} из {1} учтено".replace("{0}", sc.verified.toString()).replace("{1}", sc.scored.toString())),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = lit / 10f,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = col,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (unknownCount > 0) Text(
                            I18n.tr("Не проверено: {0}. Эти пункты не прибавляют баллы.".replace("{0}", unknownCount.toString())),
                            style = MaterialTheme.typography.bodySmall, color = Pal.Amber
                        )
                        Text(
                            "Оценка настроек и ограниченных локальных проверок. Она не подтверждает безопасность DNS, IPv4/IPv6 других приложений или отсутствие утечек.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (sc.preliminary) Notice(I18n.tr("Оценка предварительная: часть пунктов не проверена или устарела."), Pal.Amber)
                sc.capReason?.let { Notice(I18n.tr(it), Pal.Red) }
                Button({ Shield.runFull() }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !AppState.shieldBusy && familyProof?.running != true, shape = RoundedCornerShape(16.dp)) {
                    if (AppState.shieldBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(I18n.tr(if (AppState.shieldBusy || familyProof?.running == true) "Проверяю…" else "Проверить порты и IPv4/IPv6"))
                }
                Text(I18n.tr("Проверка: типичные TCP-порты телефона, затем IPv4/IPv6 через Xray. Прямой IP без вашего отдельного согласия не запрашивается."),
                    Modifier.padding(top = 6.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (RayVpnService.connected && !RayVpnService.busy && !RayVpnService.blocked) {
                    TextButton({ confirmDirect = true }, enabled = !AppState.shieldBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(I18n.tr("Отдельно сравнить IP вне VPN"))
                    }
                }
                Panel(Modifier.padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(I18n.tr("Результаты IPv4 / IPv6 через Xray"), style = MaterialTheme.typography.titleSmall)
                        Text(I18n.tr("Только по нажатию: два запроса к api4.ipify.org и api6.ipify.org через приватный Unix-сокет Xray. HTTP без TLS после выхода с сервера. Публичный IP не сохраняется и не показывается."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val ready = RayVpnService.connected && !RayVpnService.busy && !RayVpnService.blocked &&
                            RayVpnService.since > 0L && Pinger.probePath.isNotBlank()
                        FilledTonalButton({ FamilyHttpProbe.check() }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = ready && familyProof?.running != true) {
                            Text(I18n.tr(if (familyProof?.running == true) "Проверяю IPv4 и IPv6…"
                                else if (familyProof != null) "Повторить IPv4 / IPv6" else "Проверить IPv4 / IPv6"))
                        }
                        if (!ready) Text(I18n.tr("Подключите VPN для проверки через Xray."),
                            style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        val v4 = familyProof?.ipv4
                        val v6 = familyProof?.ipv6
                        Text(I18n.tr("IPv4 HTTP: " + when {
                            v4?.status == FamilyHttpStatus.REACHABLE -> I18n.tr("ответ через Xray · {0} мс".replace("{0}", v4.ms.toString()))
                            familyProof?.running == true -> "проверяется"
                            v4?.issue != null -> "UNKNOWN · " + I18n.tr(v4.issue.label)
                            else -> "UNKNOWN · не проверено или результат устарел"
                        }, style = MaterialTheme.typography.bodyMedium)
                        Text(I18n.tr("IPv6 HTTP: " + when {
                            v6?.status == FamilyHttpStatus.REACHABLE -> I18n.tr("ответ через Xray · {0} мс".replace("{0}", v6.ms.toString()))
                            familyProof?.running == true -> "проверяется"
                            v6?.issue != null -> "UNKNOWN · " + I18n.tr(v6.issue.label)
                            else -> "UNKNOWN · не проверено или результат устарел"
                        }, style = MaterialTheme.typography.bodyMedium)
                        if (AppState.blockV6) Text(
                            "Настройка «Не использовать IPv6» включена. Тест через служебный Xray-сокет не проверяет IPv6-трафик других приложений.",
                            style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        Text(I18n.tr("Политика маршрутов DNS 53 (по настройкам)"), style = MaterialTheme.typography.titleSmall)
                        Text(I18n.tr(DnsRoutePolicy.describe(RoutingOptions(
                            rules = AppState.rules.toList(), doh = AppState.dnsDoh,
                            blockV6 = AppState.blockV6, fakeDns = AppState.fakeDns,
                            defaultAction = AppState.defaultAction
                        ))), style = MaterialTheme.typography.bodySmall,
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
                            Text(I18n.tr("Проверить DNS 53/853 и Private DNS"))
                        }
                        dnsRouteSnapshot?.let { dns ->
                            Text(I18n.tr("DNS UDP/53: {0}".replace("{0}", I18n.tr(dns.udp.label))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dns.udp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text(I18n.tr("DNS TCP/53: {0}".replace("{0}", I18n.tr(dns.tcp.label))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dns.tcp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text(I18n.tr("Адресов DNS в расчёте: {0}. Возможные ограничения — пользовательские правила direct, геобазы и приложения вне VPN.".replace("{0}", dns.resolvers.toString())),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(I18n.tr("Это снимок текущих настроек, а не маршрутов уже запущенного VPN. Фактическая передача DNS и отсутствие утечек не проверяются."),
                                style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        }
                        encryptedDnsSnapshot?.let { secure ->
                            Text(I18n.tr("DNS-over-TLS TCP/853: {0}".replace("{0}", I18n.tr(secure.tcp.label))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (secure.tcp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text(I18n.tr("DNS-over-QUIC UDP/853: {0}".replace("{0}", I18n.tr(secure.udp.label))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (secure.udp == DnsAuditKind.DIRECT) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                            Text(I18n.tr("Оценка только для {0} выбранных IP DNS. Системный Private DNS и приложения могут использовать другие адреса.".replace("{0}", secure.resolvers.toString())),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        privateDnsSnapshot?.let { privateDns ->
                            Text(I18n.tr("Android Private DNS: {0}".replace("{0}", I18n.tr(privateDns.label))),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(I18n.tr("Показана активная сеть самого VITRUM — приложение может быть исключено из VPN. Домен Private DNS не считывается в отчёт. Это не проверка маршрута других приложений."),
                                style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        }
                        Text(I18n.tr("DNS A/AAAA и утечки: UNKNOWN"),
                            style = MaterialTheme.typography.bodyMedium, color = Pal.Amber)
                        Text(I18n.tr("Успех HTTP подтверждает ответ внешнего узла через служебный Xray-сокет, но не маршрут других приложений, DNS-запросов или отсутствие утечек. Ошибка или таймаут остаются UNKNOWN. Для проверки DNS запустите отдельный тест в браузере, который включён в Android VPN."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val auditHistory = ShieldAuditHistory.entries
                if (auditHistory.isNotEmpty()) {
                    Panel(Modifier.padding(top = 10.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(I18n.tr("История диагностик"), style = MaterialTheme.typography.titleSmall)
                            Text(I18n.tr("До 12 проверок только в памяти процесса. Не сохраняются IP, порты, серверы и ключи. Время проверок остаётся в отчёте."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (auditHistory.size >= 2) {
                                val changed = ShieldAuditPolicy.diff(auditHistory[auditHistory.lastIndex - 1], auditHistory.last())
                                Text(I18n.tr(if (changed.isEmpty()) "С прошлой проверки категории результатов не изменились."
                                    else "Изменения с прошлой проверки: {0}".replace("{0}", changed.joinToString("; "))),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            val fmtAudit = remember { java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.getDefault()) }
                            auditHistory.asReversed().take(6).forEach { entry ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
                                Text(fmtAudit.format(java.util.Date(entry.atMs)), style = MaterialTheme.typography.labelMedium)
                                Text(I18n.tr("Порты: {0} · IPv4: {1} · IPv6: {2}".replace("{0}", I18n.tr(entry.ports.label)).replace("{1}", I18n.tr(entry.ipv4.label)).replace("{2}", I18n.tr(entry.ipv6.label))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ copy(ShieldAuditPolicy.report(auditHistory), sensitive = false) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(I18n.tr("Скопировать отчёт")) }
                                TextButton({ confirmHistoryClear = true },
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Очистить")) }
                            }
                        }
                    }
                }
                val fixes = Shield.fixable(checks)
                if (fixes.isNotEmpty()) FilledTonalButton({ strengthen = fixes }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) { Text(I18n.tr("Усилить защиту")) }
                val groups = listOf(
                    "Требует внимания" to checks.filter { it.st == St.BAD || it.st == St.WARN },
                    "Не проверено" to checks.filter { it.st == St.UNKNOWN },
                    "В порядке" to checks.filter { it.st == St.OK },
                    "К сведению" to checks.filter { it.st == St.INFO })
                groups.forEach { (name, list) ->
                    if (list.isNotEmpty()) {
                        GroupTitle(I18n.tr(name))
                        list.forEach { ch ->
                            Column {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                    val (sym, c) = when (ch.st) { St.OK -> RayIcons.Check to Pal.Green; St.WARN -> RayIcons.Alert to Pal.Amber; St.BAD -> RayIcons.Close to Pal.Red; St.UNKNOWN -> RayIcons.Help to Pal.Gray; St.INFO -> RayIcons.Info to Pal.Gray }
                                    Icon(sym, null, Modifier.size(20.dp), tint = c)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(I18n.tr(ch.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            if (ch.weight > 0) Text(I18n.tr("${ch.points}/${ch.weight}"), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = if (ch.st == St.OK) Pal.Green else c)
                                        }
                                        Text(I18n.tr(ch.detail), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (ch.act != Act.NONE) TextButton({ if (ch.act == Act.DOH) confirm = ch.act else doAct(ch.act) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr(ch.actLabel)) }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                if (AppState.shieldNet?.ports?.isNotEmpty() == true) {
                    GroupTitle(I18n.tr("Чьи это порты (root)"))
                    Text(I18n.tr(if (AppState.rootOn) "Нужен root: без него Android не даёт приложению узнать, кому принадлежит порт. Кнопка один раз запросит root-доступ, прочитает список сокетов и пакетов и ничего не изменит. Порты берутся из последней проверки сети."
                        else "Нужен root. Функция выключена: включите «Root-функции» в «Настройки → Сервис → Root»."),
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (AppState.rootOn) FilledTonalButton({ PortOwners.run(this@ShieldDialog) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.ownersBusy, shape = RoundedCornerShape(16.dp)) {
                        if (AppState.ownersBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(I18n.tr(if (AppState.ownersBusy) "Проверяю…" else "Узнать, чьи порты (root)"))
                    }
                    if (AppState.rootOn) AppState.portOwners?.items?.forEach { o ->
                        Panel(Modifier.padding(top = 8.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(I18n.tr("Порт {0}".replace("{0}", o.port.toString())), style = MaterialTheme.typography.titleSmall)
                                Text(I18n.tr(o.label), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = if (o.self) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (o.self) Text(I18n.tr("Это порт самого {0}: так быть не должно, сообщите об этом.".replace("{0}", BRAND)), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Pal.Red)
                            }
                        }
                    }
                }
                GroupTitle(I18n.tr("Что видят другие приложения"))
                Text(I18n.tr("Проверка без root: что о VPN может узнать любое приложение. Измеряется из процесса {0}, который сам идёт мимо туннеля, поэтому это вид приложения вне VPN, например банка, исключённого из туннеля. У конкретного приложения результат может отличаться. В оценку щита не входит.".replace("{0}", BRAND)),
                    Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ VpnTraces.run(this@ShieldDialog) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.traceBusy, shape = RoundedCornerShape(16.dp)) {
                    if (AppState.traceBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(I18n.tr(if (AppState.traceBusy) "Проверяю…" else if (AppState.traceResult == null) "Проверить, что видят другие приложения" else "Проверить снова"))
                }
                AppState.traceResult?.let { tr ->
                    val (seen, measured) = VpnTraces.summary(tr.items)
                    Text(I18n.tr("Признак VPN виден в {0} из {1} измеренных проверок.".replace("{0}", seen.toString()).replace("{1}", measured.toString())), Modifier.padding(top = 10.dp, bottom = 6.dp), style = MaterialTheme.typography.titleSmall,
                        color = if (seen > 0) Pal.Amber else Pal.Green)
                    if (tr.session != RayVpnService.since || !RayVpnService.connected) Notice(I18n.tr("Результат относится к прошлому подключению. Проверьте снова."), Pal.Amber)
                    if (seen > 0) Text(I18n.tr("Это не ошибка: система показывает приложениям, что VPN есть. {0} эти признаки не скрывает. Скрывают их внешние модули на телефоне с root. Исключение приложения из туннеля (например банка) направляет его трафик мимо VPN, но признаки VPN остаются.".replace("{0}", BRAND)),
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    tr.items.forEach { t ->
                        TraceRow(t.title, t.detail, t.state, when (t.state) { TraceState.SEEN -> "Виден"; TraceState.CLEAN -> "Не виден"; TraceState.UNKNOWN -> "Не удалось измерить" })
                    }
                    GroupTitle(I18n.tr("Сравнение с эталоном (root)"))
                    Text(I18n.tr(if (AppState.rootOn) "Root покажет, что в системе на самом деле: какие VPN-интерфейсы есть, и входит ли {0} в правила VPN. Так видно разницу между «признака нет» и «признак есть, но приложению закрыт». Только чтение.".replace("{0}", BRAND)
                        else "Нужен root. Функция выключена: включите «Root-функции» в «Настройки → Сервис → Root»."),
                        Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (AppState.rootOn) {
                        FilledTonalButton({ VpnTruth.run() }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !AppState.truthBusy, shape = RoundedCornerShape(16.dp)) {
                            if (AppState.truthBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(I18n.tr(if (AppState.truthBusy) "Проверяю…" else "Сравнить с эталоном (root)"))
                        }
                        AppState.truthResult?.let { tt ->
                            if (tt.traceAt != tr.at) Notice(I18n.tr("Сравнение относится к прошлой проверке. Запустите его снова."), Pal.Amber)
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
        RayDialogFrame(
            title = I18n.tr("Усилить защиту?"),
            onDismiss = { strengthen = null },
            content = {
                Text(I18n.tr("Будут включены: {0}.".replace("{0}", names.joinToString(", ") { I18n.tr(it) })), style = MaterialTheme.typography.bodyMedium)
                if (Act.DOH in acts) Text(I18n.tr("Функция новая. Запросы приложений будут разбираться внутри Xray по HTTPS. Если после включения пропадёт интернет, выключите её в «Настройки → Сеть и DNS»."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = { TextButton({ acts.forEach { doAct(it) }; AppState.message = "Защита усилена. Часть настроек применится при следующем подключении."; strengthen = null }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Включить")) } },
            dismissButton = { TextButton({ strengthen = null }, Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Отмена")) } }
        )
    }
    if (confirmHistoryClear) {
        RayConfirmDialog(
            title = I18n.tr("Очистить историю диагностик?"),
            message = I18n.tr("Будут удалены последние результаты проверок, хранящиеся только в памяти процесса. Настройки защиты и VPN не изменятся."),
            confirmLabel = I18n.tr("Очистить"),
            destructive = true,
            onConfirm = {
                ShieldAuditHistory.clear()
                confirmHistoryClear = false
            },
            onDismiss = { confirmHistoryClear = false }
        )
    }
    if (confirmDirect) {
        RayConfirmDialog(
            title = I18n.tr("Сравнить IP вне VPN?"),
            message = I18n.tr("VITRUM выполнит HTTPS-запрос к внешней службе из собственного процесса, исключённого из Android VPN. Служба увидит ваш прямой IP. Затем приложение сравнит его с IP через Xray. Равенство или различие адресов не доказывает отсутствие утечек других приложений."),
            confirmLabel = I18n.tr("Запросить прямой IP"),
            onConfirm = { Shield.runNet(compareDirectIp = true); confirmDirect = false },
            onDismiss = { confirmDirect = false }
        )
    }
    confirm?.let { a ->
        RayConfirmDialog(
            title = I18n.tr("Включить шифрованный DNS?"),
            message = I18n.tr("Функция новая. Запросы приложений будут разбираться внутри Xray по HTTPS. Если после включения пропадёт интернет, выключите её в «Настройки → Сеть и DNS»."),
            confirmLabel = I18n.tr("Включить"),
            onConfirm = { doAct(a); confirm = null },
            onDismiss = { confirm = null }
        )
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
                Text(I18n.tr(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(I18n.tr(word), style = MaterialTheme.typography.labelMedium, color = c)
            }
            Text(I18n.tr(detail), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Плашка с предупреждением в окне щита: значок и текст слева, мягкая заливка цвета предупреждения. */
@Composable
private fun Notice(text: String, color: Color) = Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp),
    verticalAlignment = Alignment.Top) {
    Icon(RayIcons.Alert, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(10.dp))
    Text(I18n.tr(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
}
