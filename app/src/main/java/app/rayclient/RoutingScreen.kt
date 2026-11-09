@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.RoutingTab(embedded: Boolean = false) {
    var pick by remember { mutableStateOf(false) }
    var routePreview by remember { mutableStateOf(false) }
    var routeQuery by remember { mutableStateOf("") }
    var routeCandidate by remember { mutableStateOf<String?>(null) }
    var routeTarget by remember { mutableStateOf("") }
    var routePort by remember { mutableStateOf("") }
    var routeProtocol by remember { mutableStateOf("tcp") }
    var routeResult by remember { mutableStateOf<RouteExplanation?>(null) }
    var routeApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var addRule by remember { mutableStateOf(false) }
    var editIdx by remember { mutableIntStateOf(-1) }            // номер правила, которое правится (-1: никакое)
    var askDefault by remember { mutableIntStateOf(-1) }         // «остальной трафик»: выбранное значение ждёт подтверждения
    var profDialog by remember { mutableStateOf<String?>(null) }   // "new" | "copy" | "rename"
    var confirmProfDel by remember { mutableStateOf(false) }
    var deleteRuleAt by remember { mutableIntStateOf(-1) }
    var bankPlan by remember { mutableStateOf<BankApps.Plan?>(null) }   // пресет «Банки и госуслуги мимо VPN», ждёт подтверждения
    val editable = AppState.activeProfile != "sub"
    val kinds = listOf("Домены", "IP", "Порты"); val acts = listOf("Через VPN", "Напрямую", "Блокировать")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (!embedded) Text("Маршрутизация", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineLarge)
        // итог одной панелью: что происходит с трафиком и сколько правил ведёт его через VPN, напрямую и в блок
        val enabledRules = AppState.rules.filter { it.enabled }
    val direct = enabledRules.count { it.action == 1 } + AppState.presets.size + (if (AppState.exceptRu) 1 else 0) + (if (AppState.useWhitelist) 1 else 0)
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (AppState.appMode) {
                    1 -> "В VPN попадают только выбранные приложения, остальные идут мимо него."
                    2 -> "Выбранные приложения идут напрямую; остальные подчиняются правилам и настройке прочего трафика."
                    else -> when (AppState.defaultAction) {
                        1 -> "Трафик по правилам; прочий трафик идёт напрямую, мимо VPN."
                        2 -> "Трафик по правилам; прочий трафик блокируется."
                        else -> "Трафик по правилам; прочий трафик идёт через VPN."
                    }
                }, style = MaterialTheme.typography.titleSmall)
                Text("Настроенные правила", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Через VPN" to enabledRules.count { it.action == 0 },
                    "Напрямую" to direct,
                    "Блокировать" to enabledRules.count { it.action == 2 }).forEach { (label, count) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(count.toString(), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
        if (direct > 0 || AppState.appMode != 0 || AppState.defaultAction == 1) Panel(Modifier.padding(top = 10.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Часть соединений может идти мимо VPN", color = Pal.Amber,
                    style = MaterialTheme.typography.titleSmall)
                Text("Так работают выбранные исключения, профили и правила напрямую. Это настройка маршрута, не результат проверки трафика.",
                    Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GroupTitle("Профиль")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var profileMenu by remember { mutableStateOf(false) }
                val subscriptionProfile = AppState.subProfile()
                val profileTitle = if (AppState.activeProfile == "sub") "Из подписки" else
                    AppState.profiles.firstOrNull { it.id == AppState.activeProfile }?.name ?: "Профиль"
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton({ profileMenu = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(profileTitle, Modifier.weight(1f))
                        Icon(RayIcons.Chevron, null, Modifier.size(16.dp).rotate(90f))
                    }
                    DropdownMenu(profileMenu, { profileMenu = false }) {
                        AppState.profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = { AppState.activateProfile(profile.id); profileMenu = false },
                                trailingIcon = if (AppState.activeProfile == profile.id) ({ Icon(RayIcons.Check, null) }) else null
                            )
                        }
                        if (subscriptionProfile != null) DropdownMenuItem(
                            text = { Text("Из подписки") },
                            onClick = { AppState.activateProfile("sub"); profileMenu = false },
                            trailingIcon = if (AppState.activeProfile == "sub") ({ Icon(RayIcons.Check, null) }) else null
                        )
                    }
                }
                TextButton({ profDialog = "new" }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(RayIcons.Plus, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Новый профиль")
                }
                if (editable) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ profDialog = "rename" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Переименовать") }
                    TextButton({ profDialog = "copy" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Копия") }
                    TextButton({ confirmProfDel = true }, enabled = AppState.profiles.size > 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("Удалить", color = if (AppState.profiles.size > 1) Pal.Red else MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text("Правила (${AppState.rules.size}) взяты из подписки и обновляются вместе с ней. Редактировать их нельзя: сделайте копию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ profDialog = "copy" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Сделать копию для редактирования") }
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
            SwitchRow("Не использовать IPv6", "Xray запрашивает IPv4 вместо IPv6 и блокирует прямые IPv6-адреса внутри своей маршрутизации. Android VPN по-прежнему содержит маршрут ::/0. Отсутствие утечек требует отдельной проверки устройства.", AppState.blockV6) { AppState.blockV6 = it; reload() }
        }
        GroupTitle("Программы")
        Panel {
            Column(Modifier.padding(16.dp)) {
                Segmented(listOf("Все", "Только выбранные", "Кроме выбранных"), AppState.appMode) { AppState.appMode = it; AppState.save() }
                Text(listOf("Весь трафик телефона идёт через VPN.", "В VPN попадают только выбранные приложения, остальные идут мимо него.", "Выбранные программы идут мимо VPN, всё остальное через него.")[AppState.appMode],
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (AppState.appMode != 0) FilledTonalButton({ pick = true }, Modifier.padding(top = 10.dp).heightIn(min = 48.dp)) { Text("Выбрать программы (${AppState.apps.size})") }
                OutlinedButton({
                    routeQuery = ""
                    routeCandidate = null
                    routeTarget = ""
                    routePort = ""
                    routeProtocol = "tcp"
                    routeResult = null
                    routeApps = packageManager.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        PackageManager.MATCH_ALL
                    ).map { item ->
                        val pkg = item.activityInfo.packageName
                        (item.loadLabel(packageManager)?.toString() ?: pkg) to pkg
                    }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
                    routePreview = true
                }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Text("Проверить маршрут приложения")
                }
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
                }, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) { Text("Банки и госуслуги мимо VPN") }
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
                    }, enabled = !AppState.asnBusy, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (AppState.asnBusy) "Скачиваю…" else "Обновить подсети") }
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
                    TextButton({ GeoData.update(ctx) }, enabled = !AppState.ruGeoBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Обновить сейчас") }
                    TextButton({ AppState.ruGeo = false; GeoData.delete(ctx); AppState.save() }, enabled = !AppState.ruGeoBusy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Удалить файл") }
                }
            }
            if (askGeo) RayConfirmDialog(
                title = "Скачать базу IP для России?",
                message = "Будет скачано около 18 МБ с github.com. Если вы на мобильном интернете, это расходует трафик. Применится при следующем подключении.",
                confirmLabel = "Скачать",
                onConfirm = { askGeo = false; GeoData.update(ctx, enableAfter = true) },
                onDismiss = { askGeo = false }
            )
        }
        GroupTitle("Правила", "Проверяются сверху вниз: первое подходящее включённое правило определяет маршрут. Перемещайте важные правила выше.")
        Panel {
            if (AppState.rules.isEmpty()) Text("Правил нет. Пример: Домены · netflix.com · Через VPN", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppState.rules.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(r.enabled, { AppState.rules[i] = Rule(r.kind, r.value, r.action, it); AppState.save() }, Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f).clickable(role = Role.Button) { editIdx = i }.alpha(if (r.enabled) 1f else 0.5f)) {
                        Text(r.value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tag(kinds[r.kind], Pal.Gray); Tag(acts[r.action], when (r.action) { 0 -> Pal.Violet; 1 -> Pal.Green; else -> Pal.Red })
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val canMoveUp = i > 0
                        val canMoveDown = i < AppState.rules.lastIndex
                        IconButton(
                            onClick = {
                                RuleOrder.move(AppState.rules.toList(), i, -1)?.let {
                                    AppState.rules.clear(); AppState.rules.addAll(it); AppState.save()
                                }
                            },
                            enabled = canMoveUp
                        ) {
                            Icon(
                                RayIcons.Chevron,
                                I18n.tr("Переместить правило выше"),
                                Modifier.rotate(270f),
                                tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = {
                                RuleOrder.move(AppState.rules.toList(), i, 1)?.let {
                                    AppState.rules.clear(); AppState.rules.addAll(it); AppState.save()
                                }
                            },
                            enabled = canMoveDown
                        ) {
                            Icon(
                                RayIcons.Chevron,
                                I18n.tr("Переместить правило ниже"),
                                Modifier.rotate(90f),
                                tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton({ deleteRuleAt = i }) {
                        Icon(RayIcons.Trash, I18n.tr("Удалить правило:") + " " + r.value)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
            TextButton({ addRule = true }, Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) { Icon(RayIcons.Plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Новое правило") }
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
        RayDialogFrame(
            title = when (mode) { "rename" -> "Переименовать профиль"; "copy" -> "Копия профиля"; else -> "Новый профиль" },
            onDismiss = { profDialog = null },
            content = {
                OutlinedTextField(nm, { nm = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Название") })
            },
            confirmButton = { TextButton({
                val n = nm.trim().ifBlank { "Профиль" }
                when (mode) { "rename" -> AppState.renameProfile(AppState.activeProfile, n); "copy" -> AppState.newProfile(n, true); else -> AppState.newProfile(n, false) }
                profDialog = null
            }, Modifier.heightIn(min = 48.dp)) { Text("Готово") } },
            dismissButton = { TextButton({ profDialog = null }, Modifier.heightIn(min = 48.dp)) { Text("Отмена") } }
        )
    }
    AppState.rules.getOrNull(deleteRuleAt)?.let { pendingRule ->
        RayConfirmDialog(
            title = I18n.tr("Удалить правило?"),
            message = I18n.tr("Правило:") + " " + pendingRule.value + ". " + I18n.tr("Это действие нельзя отменить."),
            confirmLabel = I18n.tr("Удалить"),
            destructive = true,
            onConfirm = {
                val current = AppState.rules.getOrNull(deleteRuleAt)
                if (current == pendingRule) {
                    AppState.rules.removeAt(deleteRuleAt)
                    AppState.save()
                }
                deleteRuleAt = -1
            },
            onDismiss = { deleteRuleAt = -1 }
        )
    }
    if (confirmProfDel) RayConfirmDialog(
        title = "Удалить профиль?",
        message = "«${AppState.profileName(AppState.activeProfile)}» будет удалён вместе с правилами.",
        confirmLabel = "Удалить",
        destructive = true,
        onConfirm = { AppState.deleteProfile(AppState.activeProfile); confirmProfDel = false },
        onDismiss = { confirmProfDel = false }
    )
    if (routePreview) RayDialogFrame(
        title = I18n.tr("Маршрут приложения · по настройкам"),
        onDismiss = { routePreview = false },
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Прогноз по настройкам: Android TUN + порядок правил Xray. Не измерение реального трафика.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(routeQuery, { routeQuery = it },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text(I18n.tr("Найти приложение")) })
                val filtered = routeApps.filter { (label, pkg) ->
                    routeQuery.isBlank() || label.contains(routeQuery, true) || pkg.contains(routeQuery, true)
                }
                if (filtered.isEmpty()) Text(I18n.tr("Подходящие приложения не найдены."),
                    style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 130.dp)) {
                    items(filtered.take(80)) { (label, pkg) ->
                        TextButton({ routeCandidate = pkg; routeResult = null }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (filtered.size > 80) Text(I18n.tr("Показаны первые 80. Уточните поиск."),
                    style = MaterialTheme.typography.bodySmall)
                routeCandidate?.let { pkg ->
                    val label = routeApps.firstOrNull { it.second == pkg }?.first ?: pkg
                    Text(I18n.tr("Приложение: {0}".replace("{0}", label)), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(routeTarget, { routeTarget = it; routeResult = null },
                        Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Домен или IP-адрес") },
                        placeholder = { Text("example.org или 203.0.113.5") })
                    OutlinedTextField(routePort, { routePort = it.filter(Char::isDigit).take(5); routeResult = null },
                        Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Порт (необязательно)") })
                    Segmented(listOf("TCP", "UDP"), if (routeProtocol == "tcp") 0 else 1) {
                        routeProtocol = if (it == 0) "tcp" else "udp"
                        routeResult = null
                    }
                    FilledTonalButton({
                        routeResult = RouteExplain.evaluate(
                            AppState.routingOptions(), AppState.appMode, AppState.apps.toSet(),
                            pkg, packageName, routeTarget, routePort.toIntOrNull(), routeProtocol
                        )
                    }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Проверить маршрут")
                    }
                    routeResult?.let { result ->
                        val name = when (result.verdict) {
                            RouteVerdict.VPN -> "Через VPN"
                            RouteVerdict.DIRECT -> "Напрямую"
                            RouteVerdict.BLOCK -> "Блокировать"
                            RouteVerdict.EXCLUDED -> "Вне Android VPN"
                            RouteVerdict.UNKNOWN -> "UNKNOWN · маршрут не определён"
                        }
                        Text(I18n.tr("Предполагаемый маршрут: {0}".replace("{0}", name)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (result.verdict == RouteVerdict.DIRECT || result.verdict == RouteVerdict.EXCLUDED ||
                                result.verdict == RouteVerdict.UNKNOWN) Pal.Amber else MaterialTheme.colorScheme.onSurface)
                        Text(result.reason, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("DNS IPv4: UNKNOWN · DNS IPv6: UNKNOWN · реальный трафик: UNKNOWN",
                        style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                    Text("Геобазы, DNS-разрешение, sniffing, правила на сервере и фактическая сеть не проверены. Результат только по текущим сохранённым настройкам; при активном VPN изменения могут ещё не примениться.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton({ routePreview = false }, Modifier.heightIn(min = 48.dp)) { Text("Закрыть") }
        }
    )
    if (pick) AppPicker { pick = false }
    bankPlan?.let { plan ->
        RayDialogFrame(
            title = "Банки и госуслуги мимо VPN?",
            onDismiss = { bankPlan = null },
            content = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Напрямую, без VPN пойдут: ${BankApps.names(plan.found).joinToString(", ") { I18n.tr(it) }}. Банк или сервис увидит ваш настоящий IP.", style = MaterialTheme.typography.bodyMedium)
                    Text("Признаки VPN на самом телефоне (интерфейс tun, сеть VPN в списке сетей) могут остаться видны этим приложениям. «Щит → Что видят другие приложения» покажет, что именно видно.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (plan.flipsMeaning) Text("Сейчас режим «Только выбранные»: ваши программы (${AppState.apps.size}) идут через VPN. После переключения на «Кроме выбранных» они тоже пойдут мимо VPN.",
                        style = MaterialTheme.typography.bodyMedium, color = Pal.Amber)
                    Text("Список составлен вручную и может устареть. Изменения применятся при следующем подключении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({
                plan.added.forEach { if (it !in AppState.apps) AppState.apps += it }
                AppState.appMode = plan.mode; AppState.save(); bankPlan = null
            }, Modifier.heightIn(min = 48.dp)) { Text("Добавить") } },
            dismissButton = { TextButton({ bankPlan = null }, Modifier.heightIn(min = 48.dp)) { Text("Отмена") } }
        )
    }
    if (addRule || editIdx >= 0) {
        val cur = AppState.rules.getOrNull(editIdx)
        val close = { addRule = false; editIdx = -1 }
        var kind by remember(editIdx) { mutableIntStateOf(cur?.kind ?: 0) }; var act by remember(editIdx) { mutableIntStateOf(cur?.action ?: 1) }; var v by remember(editIdx) { mutableStateOf(cur?.value ?: "") }
        var err by remember(editIdx) { mutableStateOf("") }; var checking by remember(editIdx) { mutableStateOf(false) }
        RayDialogFrame(
            title = "Правило",
            onDismiss = close,
            content = {
                Segmented(kinds, kind) { kind = it; err = "" }
                Segmented(acts, act) { act = it }
                OutlinedTextField(v, { v = it; err = "" }, Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text(kinds[kind]) }, isError = err.isNotEmpty(),
                    supportingText = if (err.isNotEmpty()) ({ Text(err) }) else null)
            },
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
            }, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (cur != null) "Сохранить" else "Добавить")
            } },
            dismissButton = { TextButton(close, Modifier.heightIn(min = 48.dp)) { Text("Отмена") } }
        )
    }
    if (askDefault > 0) {
        val pick = askDefault
        RayConfirmDialog(
            title = if (pick == 1) "Остальной трафик напрямую?" else "Блокировать остальной трафик?",
            message = if (pick == 1) "Всё, что не подошло ни под одно правило, пойдёт без VPN, с вашим настоящим IP и через вашего провайдера. Проверьте, что нужные сайты и приложения есть в правилах с действием «Через VPN». Применится при следующем подключении."
                else "Всё, что не подошло ни под одно правило, перестанет работать. Проверьте, что нужное есть в правилах. Если интернет пропал, верните «Через VPN». Применится при следующем подключении.",
            confirmLabel = "Включить",
            onConfirm = { AppState.defaultAction = pick; AppState.save(); askDefault = -1 },
            onDismiss = { askDefault = -1 }
        )
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
    RayDialogFrame(
        title = "Программы",
        onDismiss = { AppState.save(); onClose() },
        content = {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Поиск") })
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(list.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }, key = { it.second }) { (name, pkg) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { if (pkg in AppState.apps) AppState.apps -= pkg else AppState.apps += pkg },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in AppState.apps, onCheckedChange = null)
                        Column { Text(name, maxLines = 1); Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }
            }
        },
        confirmButton = { TextButton({ AppState.save(); onClose() }, Modifier.heightIn(min = 48.dp)) { Text("Готово") } }
    )
}

// ================================================================ Настройки

