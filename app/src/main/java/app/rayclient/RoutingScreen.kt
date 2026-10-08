@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.RoutingTab(embedded: Boolean = false) {
    var pick by remember { mutableStateOf(false) }
    var addRule by remember { mutableStateOf(false) }
    var editIdx by remember { mutableIntStateOf(-1) }            // номер правила, которое правится (-1: никакое)
    var askDefault by remember { mutableIntStateOf(-1) }         // «остальной трафик»: выбранное значение ждёт подтверждения
    var profDialog by remember { mutableStateOf<String?>(null) }   // "new" | "copy" | "rename"
    var confirmProfDel by remember { mutableStateOf(false) }
    var bankPlan by remember { mutableStateOf<BankApps.Plan?>(null) }   // пресет «Банки и госуслуги мимо VPN», ждёт подтверждения
    val editable = AppState.activeProfile != "sub"
    val kinds = listOf("Домены", "IP", "Порты"); val acts = listOf("Через VPN", "Напрямую", "Блокировать")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (!embedded) Text("Маршрутизация", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineLarge)
        // итог одной панелью: что происходит с трафиком и сколько правил ведёт его через VPN, напрямую и в блок
        val direct = AppState.rules.count { it.action == 1 } + AppState.presets.size + (if (AppState.exceptRu) 1 else 0) + (if (AppState.useWhitelist) 1 else 0)
        Panel(Modifier.padding(top = 14.dp).riseIn()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (AppState.appMode == 1) "В VPN попадают только выбранные приложения, остальные идут мимо него." else "Всё, что не попало в правила, идёт через VPN.",
                    style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf(Triple("Через VPN", AppState.rules.count { it.action == 0 }, Pal.Violet), Triple("Напрямую", direct, Pal.Teal),
                        Triple("Блокировать", AppState.rules.count { it.action == 2 }, Pal.Red)).forEach { (label, n, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(7.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text("  $n", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                        }
                    }
                }
            }
        }
        GroupTitle("Профиль")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppState.profiles.forEach { p -> FilterChip(AppState.activeProfile == p.id, { AppState.activateProfile(p.id) }, { Text(p.name, maxLines = 1) }) }
                    if (AppState.subProfile() != null) FilterChip(AppState.activeProfile == "sub", { AppState.activateProfile("sub") }, { Text("Из подписки") })
                    AssistChip({ profDialog = "new" }, { Text("＋ Новый") })
                }
                if (editable) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ profDialog = "rename" }) { Text("Переименовать") }
                    TextButton({ profDialog = "copy" }) { Text("Копия") }
                    TextButton({ confirmProfDel = true }, enabled = AppState.profiles.size > 1) { Text("Удалить", color = if (AppState.profiles.size > 1) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text("Правила (${AppState.rules.size}) взяты из подписки и обновляются вместе с ней. Редактировать их нельзя: сделайте копию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ profDialog = "copy" }) { Text("Сделать копию для редактирования") }
                }
            }
        }
        if (editable) {
        GroupTitle("Режим для РФ", "Российские сайты и сервисы открываются напрямую, без VPN: так они работают быстрее и не видят IP сервера. Всё остальное идёт через VPN. Правило работает по адресам и доменам (.ru, .su, .рф и известные российские сервисы).")
        Panel { SwitchRow("Все, кроме РФ", "Российские сайты и сервисы (.ru, Яндекс, VK, банки, госуслуги, российские IP) идут напрямую, всё остальное через VPN.", AppState.exceptRu) { AppState.exceptRu = it; AppState.save() } }
        }
        GroupTitle("Фильтры и сеть", "Это общие настройки для всех профилей маршрутизации.")
        Panel {
            val reload = { AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
            SwitchRow("Блокировать рекламу", "Домены рекламы и трекеров из списка geosite:category-ads-all блокируются. Работает по доменам: рекламу в самом приложении, которое ходит на тот же домен, что и нужный сервис, не отличить. Если что-то сломалось, выключите. Свои правила выше этого фильтра сильнее.", AppState.blockAds) { AppState.blockAds = it; reload() }
            Divider()
            SwitchRow("Обход локальной сети", "Адреса домашней сети (192.168.x.x, 10.x.x.x и подобные) идут напрямую: принтер, телевизор, роутер доступны. Если выключить, такие адреса уйдут на сервер и локальные устройства перестанут открываться.", AppState.bypassLan) { AppState.bypassLan = it; reload() }
            Divider()
            SwitchRow("Определять адрес назначения", "По первым байтам соединения (http, tls, quic) определяется сайт, чтобы работали правила по доменам, например «Все, кроме РФ». Если выключить, правила по доменам будут срабатывать только для запросов, которые приложение прислало по имени, и часть сайтов пойдёт не так, как задано.", AppState.sniff) { AppState.sniff = it; reload() }
            Divider()
            SwitchRow("Не использовать IPv6", "Запросы IPv6-адресов получают пустой ответ, прямые IPv6-соединения блокируются: приложения переходят на IPv4. В туннель IPv6 по-прежнему заходит, поэтому мимо VPN он не утекает. Помогает, когда у сервера нет IPv6 и сайты зависают.", AppState.blockV6) { AppState.blockV6 = it; reload() }
        }
        GroupTitle("Программы")
        Panel {
            Column(Modifier.padding(16.dp)) {
                Segmented(listOf("Все", "Только выбранные", "Кроме выбранных"), AppState.appMode) { AppState.appMode = it; AppState.save() }
                Text(listOf("Весь трафик телефона идёт через VPN.", "В VPN попадают только выбранные приложения, остальные идут мимо него.", "Выбранные программы идут мимо VPN, всё остальное через него.")[AppState.appMode],
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (AppState.appMode != 0) FilledTonalButton({ pick = true }, Modifier.padding(top = 10.dp)) { Text("Выбрать программы (${AppState.apps.size})") }
                FilledTonalButton({
                    // установленные программы определяем так же, как в списке «Выбрать программы»: по значкам запуска
                    val installed = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                        .map { it.activityInfo.packageName }.toSet()
                    val plan = BankApps.plan(AppState.appMode, AppState.apps, installed)
                    when {
                        plan.found.isEmpty() -> AppState.message = "Установленных банков и госуслуг из списка не нашлось"
                        plan.added.isEmpty() && AppState.appMode == 2 -> AppState.message = "Уже в списке: эти программы и так идут мимо VPN"
                        else -> bankPlan = plan
                    }
                }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Банки и госуслуги мимо VPN") }
            }
        }
        if (editable) Column {
        GroupTitle("Игры мимо VPN")
        Panel {
            PRESETS.forEachIndexed { i, p ->
                SwitchRow(p.title, p.desc, p.id in AppState.presets) { on -> if (on) AppState.presets += p.id else AppState.presets -= p.id; AppState.save()
                    if (on && p.asns.isNotEmpty()) Thread { val e = AsnPrefixes.refresh(this@RoutingTab, AppState.presets.toSet(), force = false); if (e != null) AppState.message = "Не удалось скачать подсети: $e" }.start() }
                if (i < PRESETS.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
        Panel(Modifier.padding(top = 8.dp)) {
            val ctx = this@RoutingTab
            val v = AsnUi.version
            val withAsn = PRESETS.filter { it.asns.isNotEmpty() }
            val enabled = withAsn.filter { it.id in AppState.presets }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Подсети игровых серверов", style = MaterialTheme.typography.titleSmall)
                Text("У матчей нет доменов: они идут по UDP на IP-адреса. Для Steam, Riot и Battle.net приложение скачивает подсети компаний (RIPEstat, без вшитых данных) и пускает их напрямую. Обновляется раз в сутки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (enabled.isEmpty()) Text("Включите Steam, Riot или Battle.net выше.", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                else {
                    val parts = remember(v, enabled.map { it.id }) { enabled.joinToString(" · ") { p -> "${p.title}: ${AsnPrefixes.count(ctx, p.id)}" } }
                    val at = remember(v) { AsnPrefixes.newest(ctx) }
                    Text(parts, style = MaterialTheme.typography.bodySmall)
                    Text(if (at == 0L) "Подсети ещё не скачаны" else "Обновлено " + java.text.SimpleDateFormat("d MMM, HH:mm", I18n.locale).format(java.util.Date(at)), style = MaterialTheme.typography.labelSmall, color = if (at == 0L) Pal.Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({
                        AppState.asnBusy = true
                        Thread { val e = AsnPrefixes.refresh(ctx, AppState.presets.toSet(), force = true); AppState.asnBusy = false
                            AppState.message = if (e == null) "Подсети обновлены. Применятся при следующем подключении" else "Не удалось скачать подсети: $e" }.start()
                    }, enabled = !AppState.asnBusy, contentPadding = PaddingValues(0.dp)) { Text(if (AppState.asnBusy) "Скачиваю…" else "Обновить подсети") }
                }
            }
        }
        GroupTitle("Белый список", "Список адресов, которые на мобильном интернете в России открываются даже при ограничениях. Включённый список пускает эти адреса напрямую, а не через VPN. Список подтягивается с GitHub раз в сутки.")
        Panel { SwitchRow("Белый список мобильного интернета", "Эти домены идут напрямую. Список обновляется сам раз в сутки.", AppState.useWhitelist) { AppState.useWhitelist = it; AppState.save() } }
        GroupTitle("Базы для России", "Свежий geoip.dat проекта runetfreedom (≈18 МБ). В нём есть метка «белых» IP мобильных сетей и более точные российские адреса. Файл проверяется по контрольной сумме из релиза и пробным запуском Xray; если он не подойдёт, останется встроенная база.")
        Panel {
            val ctx = this@RoutingTab
            var askGeo by remember { mutableStateOf(false) }
            SwitchRow("Свежая база IP для России", if (AppState.ruGeoBusy) "Скачиваю и проверяю…" else if (AppState.ruGeo && AppState.ruGeoAt > 0) "Включена. Обновлено " + java.text.SimpleDateFormat("d MMM, HH:mm", I18n.locale).format(java.util.Date(AppState.ruGeoAt)) + ". Обновляется раз в сутки по Wi-Fi." else "Выключена: используется встроенная база Xray.", AppState.ruGeo) { on ->
                if (on) askGeo = true else { AppState.ruGeo = false; AppState.save() } }
            if (AppState.ruGeo) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton({ GeoData.update(ctx) }, enabled = !AppState.ruGeoBusy) { Text("Обновить сейчас") }
                    TextButton({ AppState.ruGeo = false; GeoData.delete(ctx); AppState.save() }, enabled = !AppState.ruGeoBusy) { Text("Удалить файл") }
                }
            }
            if (askGeo) AlertDialog(onDismissRequest = { askGeo = false }, title = { Text("Скачать базу IP для России?") },
                text = { Text("Будет скачано около 18 МБ с github.com. Если вы на мобильном интернете, это расходует трафик. Применится при следующем подключении.") },
                confirmButton = { TextButton({ askGeo = false; GeoData.update(ctx, enableAfter = true) }) { Text("Скачать") } },
                dismissButton = { TextButton({ askGeo = false }) { Text("Отмена") } })
        }
        GroupTitle("Правила")
        Panel {
            if (AppState.rules.isEmpty()) Text("Правил нет. Пример: Домены · netflix.com · Через VPN", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppState.rules.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(r.enabled, { AppState.rules[i] = Rule(r.kind, r.value, r.action, it); AppState.save() }, Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f).clickable { editIdx = i }.alpha(if (r.enabled) 1f else 0.5f)) {
                        Text(r.value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tag(kinds[r.kind], Pal.Gray); Tag(acts[r.action], when (r.action) { 0 -> Pal.Violet; 1 -> Pal.Green; else -> Pal.Red })
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf("▲" to -1, "▼" to 1).forEach { (mark, dir) ->
                            val can = i + dir in AppState.rules.indices
                            Text(mark, Modifier.clickable(enabled = can) {
                                RuleOrder.move(AppState.rules.toList(), i, dir)?.let { AppState.rules.clear(); AppState.rules.addAll(it); AppState.save() }
                            }.padding(horizontal = 10.dp, vertical = 5.dp), color = if (can) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                    IconButton({ AppState.rules.removeAt(i); AppState.save() }) { Icon(RayIcons.Trash, "Удалить") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
            TextButton({ addRule = true }, Modifier.padding(horizontal = 8.dp)) { Icon(RayIcons.Plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Новое правило") }
        }
        Text("Правило открывается по нажатию. Выключенное правило не применяется, но остаётся в списке. Проверка идёт по порядку, сверху вниз, и действует первое подходящее; порядок меняют стрелки ▲ ▼. Примеры. Домены: example.com, geosite:xx. IP: адрес, подсеть, geoip:ru, geoip:private. Порты: 443, 6881-6889. Несколько значений разделяйте запятыми.",
            Modifier.padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GroupTitle("Прочий трафик", "Что делать с трафиком, который не подошёл ни под одно правило. По умолчанию он идёт через VPN. «Напрямую»: через VPN идёт только то, что вы выбрали правилами, остальное с вашим обычным IP. «Блокировать»: остальное не пройдёт совсем.")
        Panel { Column(Modifier.padding(16.dp)) { Segmented(acts, AppState.defaultAction) { if (it == 0) { AppState.defaultAction = 0; AppState.save() } else if (it != AppState.defaultAction) askDefault = it } } }
        if (AppState.subs.any { it.json }) {
            GroupTitle("Стратегия балансировщика", "Только для авто-серверов (JSON-подписки): как Xray выбирает сервер из группы. «Как в панели» оставляет настройку панели. «Быстрый» выбирает по задержке и работает, только если панель передала проверку серверов; иначе остаётся настройка панели. Применится при следующем подключении.")
            Panel { Column(Modifier.padding(16.dp)) { Segmented(listOf("Как в панели", "Случайный", "По очереди", "Быстрый"), AppState.balancerStrategy) { AppState.balancerStrategy = it; AppState.save() } } }
        }
        GroupTitle("Стратегия доменов", "Как Xray выбирает маршрут, когда адрес записан доменом. AsIs: по имени, без запроса IP (быстрее). IPIfNonMatch: если по имени правило не нашлось, узнаёт IP и сверяет с правилами по IP (рекомендуется). IPOnDemand: всегда узнаёт IP.")
        Panel { Column(Modifier.padding(16.dp)) { Segmented(listOf("AsIs", "IPIfNonMatch", "IPOnDemand"), AppState.domainStrategy) { AppState.domainStrategy = it; AppState.save() } } }
        }
        Text("Изменения применяются при следующем подключении.", Modifier.padding(start = 6.dp, top = 10.dp, bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(LocalTabBottom.current))
    }
    profDialog?.let { mode ->
        var nm by remember { mutableStateOf(if (mode == "rename") AppState.profileName(AppState.activeProfile) else if (mode == "copy") AppState.profileName(AppState.activeProfile) + " (копия)" else "") }
        AlertDialog(onDismissRequest = { profDialog = null }, title = { Text(when (mode) { "rename" -> "Переименовать профиль"; "copy" -> "Копия профиля"; else -> "Новый профиль" }) },
            text = { OutlinedTextField(nm, { nm = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Название") }) },
            confirmButton = { TextButton({
                val n = nm.trim().ifBlank { "Профиль" }
                when (mode) { "rename" -> AppState.renameProfile(AppState.activeProfile, n); "copy" -> AppState.newProfile(n, true); else -> AppState.newProfile(n, false) }
                profDialog = null }) { Text("Готово") } },
            dismissButton = { TextButton({ profDialog = null }) { Text("Отмена") } })
    }
    if (confirmProfDel) AlertDialog(onDismissRequest = { confirmProfDel = false }, title = { Text("Удалить профиль?") },
        text = { Text("«${AppState.profileName(AppState.activeProfile)}» будет удалён вместе с правилами.") },
        confirmButton = { TextButton({ AppState.deleteProfile(AppState.activeProfile); confirmProfDel = false }) { Text("Удалить", color = Pal.Red) } },
        dismissButton = { TextButton({ confirmProfDel = false }) { Text("Отмена") } })
    if (pick) AppPicker { pick = false }
    bankPlan?.let { plan ->
        AlertDialog(onDismissRequest = { bankPlan = null }, title = { Text("Банки и госуслуги мимо VPN?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Напрямую, без VPN пойдут: ${BankApps.names(plan.found).joinToString(", ") { I18n.tr(it) }}. Банк или сервис увидит ваш настоящий IP.", style = MaterialTheme.typography.bodyMedium)
                Text("Признаки VPN на самом телефоне (интерфейс tun, сеть VPN в списке сетей) могут остаться видны этим приложениям. «Щит → Что видят другие приложения» покажет, что именно видно.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.flipsMeaning) Text("Сейчас режим «Только выбранные»: ваши программы (${AppState.apps.size}) идут через VPN. После переключения на «Кроме выбранных» они тоже пойдут мимо VPN.",
                    style = MaterialTheme.typography.bodyMedium, color = Pal.Amber)
                Text("Список составлен вручную и может устареть. Изменения применятся при следующем подключении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton({
                plan.added.forEach { if (it !in AppState.apps) AppState.apps += it }
                AppState.appMode = plan.mode; AppState.save(); bankPlan = null }) { Text("Добавить") } },
            dismissButton = { TextButton({ bankPlan = null }) { Text("Отмена") } })
    }
    if (addRule || editIdx >= 0) {
        val cur = AppState.rules.getOrNull(editIdx)
        val close = { addRule = false; editIdx = -1 }
        var kind by remember(editIdx) { mutableIntStateOf(cur?.kind ?: 0) }; var act by remember(editIdx) { mutableIntStateOf(cur?.action ?: 1) }; var v by remember(editIdx) { mutableStateOf(cur?.value ?: "") }
        var err by remember(editIdx) { mutableStateOf("") }; var checking by remember(editIdx) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = close, title = { Text("Правило") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(kinds, kind) { kind = it; err = "" }
                Segmented(acts, act) { act = it }
                OutlinedTextField(v, { v = it; err = "" }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(kinds[kind]) }, isError = err.isNotEmpty(),
                    supportingText = if (err.isNotEmpty()) ({ Text(err) }) else null)
            } },
            confirmButton = { TextButton({
                val value = v.trim(); val idx = editIdx
                val badFormat = RuleCheck.invalid(kind, value)
                if (badFormat.isNotEmpty()) err = "Не разобрал запись: ${badFormat.joinToString(", ")}. Проверьте формат."
                else if (value.isNotEmpty() && !checking) {
                    checking = true
                    // метки geoip/geosite читаются из баз: с несуществующей меткой Xray не запустится
                    Thread {
                        val bad = GeoTags.sanitize(Rule(kind, value, act), GeoTags.load(GeoData.assetDir(AppState.context()))).dropped
                        if (bad.isNotEmpty()) { err = "Нет такой метки в базах: ${bad.joinToString(", ")}. Проверьте название."; checking = false }
                        else {
                            if (idx >= 0 && idx < AppState.rules.size) AppState.rules[idx] = Rule(kind, value, act, AppState.rules[idx].enabled) else AppState.rules += Rule(kind, value, act)
                            AppState.save(); close()
                        }
                    }.start()
                }
            }, enabled = !checking) { Text(if (cur != null) "Сохранить" else "Добавить") } },
            dismissButton = { TextButton(close) { Text("Отмена") } })
    }
    if (askDefault > 0) {
        val pick = askDefault
        AlertDialog(onDismissRequest = { askDefault = -1 }, title = { Text(if (pick == 1) "Остальной трафик напрямую?" else "Блокировать остальной трафик?") },
            text = { Text(if (pick == 1) "Всё, что не подошло ни под одно правило, пойдёт без VPN, с вашим настоящим IP и через вашего провайдера. Проверьте, что нужные сайты и приложения есть в правилах с действием «Через VPN». Применится при следующем подключении."
                else "Всё, что не подошло ни под одно правило, перестанет работать. Проверьте, что нужное есть в правилах. Если интернет пропал, верните «Через VPN». Применится при следующем подключении.") },
            confirmButton = { TextButton({ AppState.defaultAction = pick; AppState.save(); askDefault = -1 }) { Text("Включить") } },
            dismissButton = { TextButton({ askDefault = -1 }) { Text("Отмена") } })
    }
}

@Composable
internal fun MainActivity.AppPicker(onClose: () -> Unit) {
    val pm = packageManager
    val list = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { AppState.save(); onClose() }, title = { Text("Программы") },
        text = { Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Поиск") })
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(list.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }, key = { it.second }) { (name, pkg) ->
                    Row(Modifier.fillMaxWidth().clickable { if (pkg in AppState.apps) AppState.apps -= pkg else AppState.apps += pkg },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in AppState.apps, { if (it) AppState.apps += pkg else AppState.apps -= pkg })
                        Column { Text(name, maxLines = 1); Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }
            }
        } },
        confirmButton = { TextButton({ AppState.save(); onClose() }) { Text("Готово") } })
}

// ================================================================ Настройки

