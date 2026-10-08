@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.*
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MainActivity.DiagDialog() {
    val d = AppState.diag ?: return
    AlertDialog(onDismissRequest = { AppState.diag = null }, title = { Text("Диагностика") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(d, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton({ copy(d) }) { Text("Скопировать") } },
        dismissButton = { TextButton({ AppState.diag = null }) { Text("Закрыть") } })
}

@Composable
internal fun MainActivity.Divider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

@Composable
internal fun MainActivity.SettingsTab() {
    var page by rememberSaveable { mutableStateOf<String?>(Demo.settingsPage) }
    DiagDialog()
    BackHandler(enabled = page != null) { page = null }
    // разделы въезжают справа и уезжают обратно влево
    AnimatedContent(page, transitionSpec = {
        val dir = if (targetState != null) 1 else -1
        (fadeIn(tween(260, 40)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { w -> dir * w / 5 }) togetherWith
            (fadeOut(tween(140)) + slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { w -> -dir * w / 5 })
    }, label = "settingsPage") { p ->
    if (p == "routes") RoutesPage { page = null } else
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (p == null) {
            Text("Настройки", Modifier.padding(top = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineLarge)
            val hub = listOf(
                Triple("protect", "Защита", "Kill switch, щит приватности, журнал соединений") to RayIcons.Shield,
                Triple("routes", "Маршруты", "Правила, профили, режим для РФ, игры напрямую") to RayIcons.Route,
                Triple("connect", "Подключение", "Запуск, подписки, мониторинг серверов") to RayIcons.Plug,
                Triple("auto", "Автоматизация", "Сценарии по типу сети") to RayIcons.Bolt,
                Triple("network", "Сеть и DNS", "Трафик, замер задержки, DNS") to RayIcons.Globe,
                Triple("look", "Оформление", "Тема") to RayIcons.Palette,
                Triple("service", "Сервис", "Обновления приложения, диагностика, логи") to RayIcons.Terminal,
                Triple("help", "Справка", "Что означают термины: Reality, DoH, kill switch и другие") to RayIcons.Help,
                Triple("about", "О приложении", "Версия, лицензии компонентов, внешние службы") to RayIcons.Info
            )
            // у каждого раздела свой оттенок плитки
            val hues = mapOf("protect" to Pal.Green, "connect" to Pal.Blue, "auto" to Pal.Amber, "network" to Pal.Teal,
                "look" to Pal.Violet, "routes" to Pal.Blue, "service" to Pal.Orange, "help" to Pal.Blue, "about" to Pal.Violet)
            Panel(Modifier.riseIn(90)) {
                hub.forEachIndexed { i, (t, ic) ->
                    Row(Modifier.fillMaxWidth().clickable { page = t.first }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(ic, hues[t.first] ?: Pal.Violet)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(t.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i < hub.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        } else {
            val titles = mapOf("protect" to "Защита", "connect" to "Подключение", "auto" to "Автоматизация", "network" to "Сеть и DNS", "look" to "Оформление", "service" to "Сервис", "help" to "Справка", "about" to "О приложении")
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ page = null }) { Icon(RayIcons.Back, "Назад") }
                Text(titles[p] ?: "", style = MaterialTheme.typography.headlineSmall)
            }
            when (p) {
                "protect" -> SettingsProtection()
                "connect" -> SettingsConnection()
                "auto" -> SettingsAutomation()
                "network" -> SettingsNetwork()
                "look" -> SettingsAppearance()
                "service" -> SettingsService()
                "help" -> SettingsHelp()
                "about" -> SettingsAbout()
            }
        }
        if (p == null) Text("$BRAND ${remember { appVersion(this@SettingsTab) ?: "" }} · by Lolokeksu · ядро Xray $XRAY_VERSION", Modifier.padding(top = 20.dp, bottom = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(LocalTabBottom.current))
    }
    }
}

/** «Маршруты» — страница настроек: у неё своя прокрутка, поэтому она не лежит внутри общей колонки подстраниц. */
@Composable
private fun MainActivity.RoutesPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(RayIcons.Back, "Назад") }
            Text("Маршруты", style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f)) { RoutingTab(embedded = true) }
    }
}

@Composable
internal fun MainActivity.SettingsProtection() {
    val act = this
        Panel(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { AppState.showShield = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Щит приватности", style = MaterialTheme.typography.titleMedium); Text("Оценка защиты и советы, как её усилить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable { AppState.showJournal = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(RayIcons.Journal, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Журнал соединений", style = MaterialTheme.typography.titleMedium); Text("Куда ходит трафик: через VPN, напрямую или заблокирован", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(RayIcons.Chevron, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            SwitchRow("Kill switch", "Если Xray упадёт или связь оборвётся, трафик блокируется, пока вы не нажмёте «Отключиться». Если приложение закроет система, защита пропадёт: для постоянной защиты включите «Постоянный VPN» и блокировку соединений без VPN в настройках Android.", AppState.killSwitch) { AppState.killSwitch = it; AppState.save() }
            Divider()
            SwitchRow("Переподключаться при смене сети", "Wi-Fi ↔ мобильная сеть: Xray перезапускается сам.", AppState.reconnectNet) { AppState.reconnectNet = it; AppState.save() }
            Divider()
            SwitchRow("Перезапускать Xray для Hysteria2", "Помогает, когда соединение через Hysteria2 со временем перестаёт отвечать: Xray перезапускается по таймеру. Туннель остаётся включённым, обрываются только открытые сейчас соединения. Действует только для серверов Hysteria2.", AppState.hyRestart) { AppState.hyRestart = it; AppState.save() }
            if (AppState.hyRestart) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Каждые ${AppState.hyRestartMin} мин", style = MaterialTheme.typography.labelLarge)
                    Slider(AppState.hyRestartMin.toFloat(), { AppState.hyRestartMin = HyRestart.clamp(it.toInt()) }, valueRange = HyRestart.MIN.toFloat()..HyRestart.MAX.toFloat(), onValueChangeFinished = { AppState.save() })
                }
            }
            Divider()
            SwitchRow("Автопереключение серверов", "Если сервер перестал отвечать, приложение само перейдёт на другой: сначала избранные, потом с лучшим пингом.", AppState.autoSwitch) { AppState.autoSwitch = it; AppState.save() }
            Divider()
            SwitchRow("Проверять защиту после подключения", "Через несколько секунд после подключения приложение запускает проверку сети из «Щита»: смотрит открытые порты и сверяет IP с VPN и без него (запрос к api.ipify.org). Работает, пока приложение открыто. Если найдена проблема, покажет сообщение.", AppState.shieldAuto) { AppState.shieldAuto = it; AppState.save() }
            Divider()
            SwitchRow("Блокировка приложения", "При входе в приложение, если вы вышли из него больше чем на ${AppLock.GRACE_SEC} секунд, нужно подтвердить личность: отпечаток, PIN или рисунок телефона. Защищает ссылки, серверы и настройки от того, кто взял разблокированный телефон. VPN при этом работает как обычно. Пока включено, снимки экрана запрещены.", AppState.appLock) { on ->
                if (on && !AppLock.available(act)) AppState.message = "Сначала задайте блокировку экрана в настройках телефона"
                else { AppState.appLock = on; AppState.save() }
            }
            Divider()
            SwitchRow("Приватные уведомления", "На заблокированном экране уведомление VPN и сценариев не показывает название сервера: только «${BRAND}» и короткую фразу. На разблокированном экране всё как раньше. Применится при следующем обновлении уведомления (например, при следующем подключении).", AppState.privateNotif) { AppState.privateNotif = it; AppState.save() }
            Divider()
            SwitchRow("Скрывать экран от скриншотов", "Снимки и запись экрана запрещены, в списке недавних приложений окно пустое: ссылки и QR-коды серверов не попадут на скриншот. Выключите, если хотите сделать скриншот приложения.", AppState.secureScreen) { AppState.secureScreen = it; AppState.save() }
            Divider()
            Column(Modifier.padding(16.dp)) {
                FilledTonalButton({ runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }) { Text("Системный постоянный VPN") }
                Text("Выберите там ${BRAND} и включите «Блокировать соединения без VPN»: защита сработает, даже если приложение закроют.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BackupPanel()
        var wipe by remember { mutableStateOf(false) }
        Panel(Modifier.padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Стереть все данные", style = MaterialTheme.typography.titleMedium)
                Text("Удаляет серверы, подписки, правила, историю, настройки и ключ шифрования. VPN отключится, приложение закроется и при следующем запуске будет как после установки. Отменить нельзя.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                FilledTonalButton({ wipe = true }) { Text("Стереть всё") }
            }
        }
        if (wipe) AlertDialog(onDismissRequest = { wipe = false }, title = { Text("Стереть все данные?") },
            text = { Text("Серверы, подписки, правила, история и настройки будут удалены без возможности восстановления. VPN отключится, приложение закроется.") },
            confirmButton = { TextButton({ wipe = false; if (!DataWipe.run(act)) AppState.message = "Не удалось стереть данные" }) { Text("Стереть") } },
            dismissButton = { TextButton({ wipe = false }) { Text("Отмена") } })
}

@Composable
internal fun MainActivity.SettingsConnection() {
    val act = this
        GroupTitle("Запуск")
        Panel {
            SwitchRow("При открытии приложения", "Подключится к выбранному серверу.", AppState.autoOpen) { AppState.autoOpen = it; AppState.save() }
            Divider()
            SwitchRow("При включении телефона", "Один раз подтвердите разрешение VPN в приложении. На части прошивок нужен автозапуск в системных настройках.", AppState.autoBoot) { AppState.autoBoot = it; AppState.save() }
        }
        GroupTitle("Работа в фоне", "Чтобы VPN не отключался, когда приложение свёрнуто, на многих телефонах нужно исключить его из экономии заряда и разрешить автозапуск. Приложение само этого сделать не может, это настройки системы.")
        Panel {
            var ignoring by remember { mutableStateOf(Background.ignoringBatteryOptimizations(act)) }
            // после возврата из системных настроек состояние обновляется
            DisposableEffect(Unit) {
                val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ignoring = Background.ignoringBatteryOptimizations(act) }
                act.lifecycle.addObserver(ob); onDispose { act.lifecycle.removeObserver(ob) }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ignoring) "Экономия заряда для приложения отключена: система реже закрывает VPN в фоне."
                    else "Для приложения включена экономия заряда: на части телефонов система может закрыть VPN в фоне.",
                    style = MaterialTheme.typography.bodyMedium, color = if (ignoring) Pal.Green else Pal.Amber)
                if (!ignoring) FilledTonalButton({
                    runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.onFailure {
                        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + packageName))) }
                    }
                }) { Text("Открыть настройки экономии заряда") }
                if (!ignoring) Text("В списке найдите приложение и выберите «Не оптимизировать» (или «Без ограничений»).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Background.hint(android.os.Build.MANUFACTURER), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Названия пунктов зависят от версии прошивки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GroupTitle("Работа в фоне (root)", "Нужен root. Вместо ручной настройки команды root исключают приложение из экономии заряда и разрешают работу в фоне. Меняется только это приложение, после подтверждения. «Сбросить к умолчанию» возвращает системные значения, в том числе убирает исключение из экономии заряда, даже если вы включили его вручную раньше. Команды взяты по памяти и на вашей прошивке не проверялись. Автозапуск оболочек производителей так не включается.")
        Panel {
            var confirmBg by remember { mutableStateOf(0) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!AppState.rootOn) Text("Функция выключена: включите «Root-функции» в «Настройки → Сервис → Root».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    AppState.bgRootInfo?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ confirmBg = 1 }, enabled = !AppState.bgRootBusy) { Text(if (AppState.bgRootBusy) "Работаю…" else "Применить (root)") }
                        FilledTonalButton({ BgRoot.run(act, 0) }, enabled = !AppState.bgRootBusy) { Text("Проверить (root)") }
                    }
                    FilledTonalButton({ confirmBg = 2 }, enabled = !AppState.bgRootBusy) { Text("Сбросить к умолчанию (root)") }
                }
            }
            if (confirmBg != 0) AlertDialog(onDismissRequest = { confirmBg = 0 }, title = { Text(if (confirmBg == 1) "Разрешить работу в фоне?" else "Сбросить к умолчанию?") },
                text = { Text(if (confirmBg == 1) "Через root будут выполнены три команды только для ${BRAND}: исключение из экономии заряда (dumpsys deviceidle whitelist) и разрешение работы в фоне (cmd appops RUN_IN_BACKGROUND и RUN_ANY_IN_BACKGROUND). Менеджер root может спросить разрешение."
                    else "Через root будут выполнены три команды только для ${BRAND}: приложение уберут из исключений экономии заряда, а разрешения работы в фоне вернут к значениям по умолчанию.") },
                confirmButton = { TextButton({ BgRoot.run(act, confirmBg); confirmBg = 0 }) { Text("Выполнить") } },
                dismissButton = { TextButton({ confirmBg = 0 }) { Text("Отмена") } })
        }
        GroupTitle("Подписки")
        Panel {
            SwitchRow("Автообновление", "Обновление идёт в фоне, в том числе при закрытом приложении. Идентификатор устройства панели не передаётся.", AppState.autoUpd) { AppState.autoUpd = it; AppState.save(); Scheduler.apply(act) }
            if (AppState.autoUpd) {
                var txt by remember { mutableStateOf(AppState.updMinutes.toString()) }
                OutlinedTextField(txt, { v -> txt = v.filter { it.isDigit() }.take(5); txt.toIntOrNull()?.let { n -> AppState.updMinutes = n.coerceAtLeast(15); AppState.save(); Scheduler.apply(act) } },
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), singleLine = true, label = { Text("Каждые, минут (не меньше 15)") })
            }
        }
        GroupTitle("Мониторинг серверов", "Приложение по расписанию быстро проверяет серверы и копит историю. По ней считается рейтинг стабильности, ею пользуются «Авто-выбор» и автопереключение.")
        Panel {
            SwitchRow("Фоновый мониторинг", "Приложение само проверяет все серверы и копит историю: из неё считается рейтинг стабильности, ею пользуются «Авто-выбор» и автопереключение. Примерно 300 КБ за круг.", AppState.monitor) { AppState.monitor = it; AppState.save(); Scheduler.applyMonitor(act) }
            if (AppState.monitor) {
                Divider()
                SwitchRow("Только по Wi-Fi", "Не тратить мобильный трафик на проверки.", AppState.monitorWifi) { AppState.monitorWifi = it; AppState.save(); Scheduler.applyMonitor(act) }
                var mm by remember { mutableStateOf(AppState.monitorMin.toString()) }
                OutlinedTextField(mm, { v -> mm = v.filter { it.isDigit() }.take(4); mm.toIntOrNull()?.let { n -> AppState.monitorMin = n.coerceAtLeast(15); AppState.save(); Scheduler.applyMonitor(act) } },
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), singleLine = true, label = { Text("Каждые, минут (не меньше 15)") })
            }
            Divider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Записей в истории: ${HealthUi.version.let { Health.count() }}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ Health.clear(); Health.save(AppState.context()) }) { Text("Очистить", color = Pal.Red) }
            }
        }
}

@Composable
internal fun MainActivity.SettingsAutomation() {
    val act = this
        GroupTitle("Расписание VPN", "В заданное время (например, ночью) приложение выключает VPN, а утром включает снова. Включает только если выбран сервер и выдано разрешение VPN. Срабатывает с задержкой до нескольких минут: система экономит заряд. Если включены «Сценарии по типу сети», действует то, что сработало последним.")
        Panel {
            SwitchRow("Выключать VPN по расписанию", "Окно «VPN выключен» задаётся ниже, может переходить через полночь.", AppState.schedOn) { AppState.schedOn = it; AppState.save(); VpnSchedule.enqueue(act) }
            if (AppState.schedOn) {
                var from by remember { mutableStateOf(VpnSchedule.format(AppState.schedFrom)) }; var to by remember { mutableStateOf(VpnSchedule.format(AppState.schedTo)) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { v -> from = v.take(5); VpnSchedule.parse(from)?.let { AppState.schedFrom = it; AppState.save(); VpnSchedule.enqueue(act) } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Выключить в (ЧЧ:ММ)") }, isError = VpnSchedule.parse(from) == null)
                    OutlinedTextField(to, { v -> to = v.take(5); VpnSchedule.parse(to)?.let { AppState.schedTo = it; AppState.save(); VpnSchedule.enqueue(act) } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Включить в (ЧЧ:ММ)") }, isError = VpnSchedule.parse(to) == null)
                    if (AppState.schedFrom == AppState.schedTo) Text("Время начала и конца совпадает: окна нет, расписание ничего не делает.", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                }
            }
        }
        GroupTitle("Сценарии", "Приложение само меняет настройки при смене типа сети: Wi-Fi, мобильная сеть или роуминг. Действует в момент смены сети, а не всё время.")
        Panel {
            SwitchRow("Сценарии по типу сети", "Приложение следит за сетью (небольшое уведомление) и при смене Wi-Fi, мобильной сети или роуминга само включает или выключает VPN, меняет профиль маршрутизации и kill switch. Действует при смене типа сети, а не всё время.", AppState.scenariosOn) {
                AppState.scenariosOn = it; if (!it) AppState.scenarioKs = 0; AppState.save(); Scenarios.syncService(act) }
            if (AppState.scenariosOn) {
                Divider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сейчас: " + (Scenarios.current(act)?.let { Scenarios.names[it] } ?: "не определено"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ Scenarios.template() }) { Text("Шаблон «в дороге»") }
                }
            }
        }
        if (AppState.scenariosOn) {
            GroupTitle("Доверенные Wi-Fi сети", "В этих сетях (дом, работа) приложение само выключает VPN при подключении, остальное из сценария «Wi-Fi» остаётся. Название сети Android считает данными о местоположении, поэтому нужно разрешение «Местоположение». Приложение местоположение не определяет и не сохраняет: оно только читает название сети, к которой вы подключены. Список хранится зашифрованно. Без разрешения (или при выключенной геолокации в телефоне) название недоступно и VPN в таких сетях не выключается: безопасный вариант по умолчанию.")
            Panel {
                var perm by remember { mutableStateOf(Scenarios.hasLocationPermission(act)) }
                val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> perm = granted; if (granted) Scenarios.syncService(act) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!perm) {
                        Text("Чтобы читать название сети, разрешите доступ к местоположению.", style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton({ ask.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Разрешить чтение названия сети") }
                    } else {
                        FilledTonalButton({
                            val s = Scenarios.currentWifiSsid(act)
                            if (s == null) AppState.message = "Название сети недоступно: подключитесь к Wi-Fi и включите геолокацию в настройках телефона"
                            else if (s in AppState.trustedSsids) AppState.message = "Эта сеть уже в списке"
                            else { AppState.trustedSsids += s; AppState.save(); Scenarios.syncService(act, apply = true) }
                        }) { Text("Добавить текущую сеть") }
                    }
                    if (AppState.trustedSsids.isEmpty()) Text("Список пуст.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppState.trustedSsids.toList().forEach { name ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton({ AppState.trustedSsids.remove(name); AppState.save() }) { Icon(RayIcons.Trash, "Удалить", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        if (AppState.scenariosOn) for (i in 0..3) {
            val s = AppState.scen[i]
            if (i == Scenarios.OPEN) GroupTitle("Когда: ${Scenarios.names[i]}", "Wi-Fi без пароля. Тип защиты сети система сообщает на Android 12 и новее; на более старых это правило не сработает. Пока для открытой Wi-Fi настроен сценарий, «Доверенные сети» на неё не действуют: сеть с таким же названием может поднять кто угодно.")
            else GroupTitle("Когда: ${Scenarios.names[i]}")
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VPN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf("Не менять", "Включить", "Выключить"), s.vpn) { AppState.scen[i] = s.copy(vpn = it); AppState.save() }
                    Text("Профиль маршрутизации", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(s.profile.isEmpty(), { AppState.scen[i] = s.copy(profile = ""); AppState.save() }, { Text("Не менять") })
                        AppState.profiles.forEach { p -> FilterChip(s.profile == p.id, { AppState.scen[i] = s.copy(profile = p.id); AppState.save() }, { Text(p.name, maxLines = 1) }) }
                        if (AppState.subProfile() != null) FilterChip(s.profile == "sub", { AppState.scen[i] = s.copy(profile = "sub"); AppState.save() }, { Text("Из подписки") })
                    }
                    Text("Kill switch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf("Как в настройках", "Включить", "Выключить"), s.ks) { AppState.scen[i] = s.copy(ks = it); AppState.save() }
                }
            }
        }
}

@Composable
internal fun MainActivity.SettingsNetwork() {
    val act = this
        GroupTitle("Трафик", "TCP и UDP: пускать оба вида трафика. «Только TCP» блокирует UDP (кроме DNS): иногда помогает при проблемах, но ломает игры и звонки.")
        Panel { Column(Modifier.padding(16.dp)) { Segmented(listOf("TCP + UDP", "Только TCP", "Только UDP"), AppState.transport) { AppState.transport = it; AppState.save() } } }
        GroupTitle("Обход блокировок", "Фрагментация TLS режет начало защищённого соединения с сервером на части: так провайдеру сложнее распознать и заблокировать VPN. Работает только для серверов с TLS или Reality по TCP (не для Hysteria2). Может немного замедлить подключение; если сервер перестал открываться, выключите.")
        Panel { SwitchRow("Фрагментация TLS", "Если VPN уже включён, туннель перезапустится.", AppState.fragment) { on ->
            AppState.fragment = on; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() } }
        if (AppState.fragment) {
            Panel(Modifier.padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // поля хранят введённое как есть; в конфиг идёт только проверенное значение, иначе стандартное
                    OutlinedTextField(AppState.fragLength, { AppState.fragLength = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Длина куска, байт") },
                        isError = !FragmentParams.valid(AppState.fragLength), supportingText = { Text(if (FragmentParams.valid(AppState.fragLength)) "Число или диапазон, например 100-200." else "Неверное значение: действует ${FragmentParams.DEFAULT_LENGTH}.") })
                    OutlinedTextField(AppState.fragInterval, { AppState.fragInterval = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Пауза между кусками, мс") },
                        isError = !FragmentParams.valid(AppState.fragInterval), supportingText = { Text(if (FragmentParams.valid(AppState.fragInterval)) "Число или диапазон, например 10-20." else "Неверное значение: действует ${FragmentParams.DEFAULT_INTERVAL}.") })
                    FilledTonalButton({ AppState.fragLength = FragmentParams.DEFAULT_LENGTH; AppState.fragInterval = FragmentParams.DEFAULT_INTERVAL; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }) { Text("Вернуть стандартные") }
                    Text("Изменения применятся при следующем подключении (или «Применить», если VPN включён).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton({ if (RayVpnService.connected) RayVpnService.requestReload() }, enabled = RayVpnService.connected) { Text("Применить") }
                }
            }
        }
        GroupTitle("Отпечаток TLS", "Каким браузером притворяется клиент при TLS-рукопожатии. «Как в ссылке» оставляет значение из ссылки сервера (обычно chrome). Один отпечаток для всех серверов; для Hysteria2 и других QUIC не применяется.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(AppState.fingerprint.isEmpty(), { AppState.fingerprint = ""; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }, { Text("Как в ссылке") })
                    FragmentParams.FINGERPRINTS.forEach { f -> FilterChip(AppState.fingerprint == f, { AppState.fingerprint = f; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }, { Text(f) }) }
                }
                Text("Если VPN включён, туннель перезапустится. Нестандартный отпечаток может не подойти серверу: тогда вернитесь на «Как в ссылке».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GroupTitle("Туннель (входящие)", "MTU: наибольший размер пакета в туннеле. По умолчанию 1500. Если часть сайтов или видео зависает, а пинг есть, попробуйте 1400 или 1280. Адрес туннеля 172.19.0.1 и режим работы VPN-службы не настраиваются; локальных портов, режима «только прокси» и раздачи VPN по сети в ${BRAND} нет сознательно.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var mtuText by remember { mutableStateOf(AppState.mtu.toString()) }
                val mtuOk = mtuText.toIntOrNull()?.let { TunParams.valid(it) } == true
                OutlinedTextField(mtuText, { mtuText = it.filter { c -> c.isDigit() }.take(4); mtuText.toIntOrNull()?.takeIf { v -> TunParams.valid(v) }?.let { v -> AppState.mtu = v; AppState.save() } },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("MTU") }, isError = !mtuOk,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    supportingText = { Text(if (mtuOk) "От ${TunParams.MIN_MTU} до ${TunParams.DEFAULT_MTU}. Применится при следующем подключении." else "Неверное значение: действует ${AppState.mtu}.") })
                FilledTonalButton({ AppState.mtu = TunParams.DEFAULT_MTU; mtuText = TunParams.DEFAULT_MTU.toString(); AppState.save() }) { Text("Вернуть 1500") }
            }
        }
        GroupTitle("Замер задержки", "TCP: проверяет, что порт сервера открыт. ICMP: обычный ping. HEAD через сервер: отправляет настоящий запрос через каждый сервер и показывает реальную задержку, это самый честный способ.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("TCP", "ICMP", "HEAD сервер", "HEAD VPN"), AppState.pingMode) { AppState.pingMode = it; AppState.save() }
                Text(listOf("Соединение с портом сервера. Быстро, но не доказывает, что прокси работает.", "Классический ICMP-ping: часть серверов на него молчит.",
                    "Реальный запрос через каждый сервер. Подключаться к VPN не нужно.", "Запрос через уже работающий туннель, только для подключённого сервера.")[AppState.pingMode],
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (AppState.pingMode >= 2) OutlinedTextField(AppState.pingUrl, { AppState.pingUrl = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Адрес проверки (только http://)") })
                if (AppState.pingMode == 1) Text("Время берётся из системной команды ping. Если прошивка не даёт приложению её запустить, она выполнится через root (нужны «Root-функции»); иначе останется проверка попроще.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (AppState.pingMode != 3) {
                    val steps = listOf(0, 1, 4, 8, 16)
                    Text("Сколько серверов проверять одновременно", style = MaterialTheme.typography.labelLarge)
                    Segmented(listOf("Авто", "1", "4", "8", "16"), steps.indexOf(AppState.pingThreads).coerceAtLeast(0)) { AppState.pingThreads = steps[it]; AppState.save() }
                    Text("Один поток самый щадящий: провайдер с «белыми списками» может заметить пачку одновременных соединений. Авто: как прежде (16 потоков для TCP и ICMP, 6 для HEAD).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        GroupTitle("DNS", "DNS превращает имена сайтов в адреса. DoH шифрует эти запросы: их разбирает Xray по HTTPS через ваш сервер, поэтому провайдер видит только шифрованный трафик.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DNS_PRESETS.forEachIndexed { i, (n, _) -> FilterChip(AppState.dnsPreset == i, { AppState.dnsPreset = i; AppState.save() }, { Text(n) }) }
                    FilterChip(AppState.dnsPreset == 4, { AppState.dnsPreset = 4; AppState.save() }, { Text("Свой") })
                }
                if (AppState.dnsPreset == 4) OutlinedTextField(AppState.dnsCustom, { AppState.dnsCustom = it; AppState.save() }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("IP-адреса DNS через запятую") }, supportingText = { Text("Например: 1.1.1.1, 9.9.9.9") })
                Text("Сейчас: " + AppState.dnsIps().joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Протокол обычного DNS", style = MaterialTheme.typography.titleSmall)
                Segmented(listOf("UDP", "TCP"), if (AppState.dnsTcp) 1 else 0) { AppState.dnsTcp = it == 1; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
                Text(if (AppState.dnsDoh) "Сейчас включён DoH: он важнее, этот выбор действует при выключенном DoH." else "UDP: как обычно. TCP: помогает, когда UDP-запросы режут или теряют. Чуть медленнее.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Стратегия доменов DNS", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Segmented(listOf("Оба", "Только IPv4", "Только IPv6"), AppState.dnsQuery) { AppState.dnsQuery = it; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
                Text(when {
                    AppState.blockV6 -> "Сейчас включено «Не использовать IPv6» (Маршруты): оно важнее, Xray просит только IPv4."
                    AppState.dnsQuery == 2 -> "Только IPv6: сайты без IPv6 не откроются. Нужно редко, обычно выбирают «Оба»."
                    else -> "Что просит Xray, когда сам разбирает домены (правила, серверы). Ответы приложениям он меняет только при DoH или «Не использовать IPv6»."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            SwitchRow("Поддельный DNS (экспериментально)", "Приложения получают временные адреса вида 198.18.x.x, а Xray по ним узнаёт сайт и применяет правила по доменам даже без определения по байтам соединения. Побочные эффекты: правила по IP-адресам и странам (в том числе «Все, кроме РФ» по адресам) для таких соединений не работают, работают только правила по доменам; определение адреса назначения включается само. Адреса живут секунды, но часть приложений может после отключения VPN или смены сети ненадолго зависать. Если что-то сломалось, выключите. Не проверено на телефоне. Если VPN включён, туннель перезапустится.", AppState.fakeDns) { AppState.fakeDns = it; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
            Divider()
            SwitchRow("Шифрованный DNS (DoH)", "Запросы приложений разбирает сам Xray по HTTPS через сервер: провайдер видит только шифрованный трафик. Применяется при следующем подключении.", AppState.dnsDoh) { AppState.dnsDoh = it; AppState.save() }
        }
}

@Composable
internal fun MainActivity.SettingsAppearance() {
    val act = this
        GroupTitle("Язык")
        Panel { Column(Modifier.padding(16.dp)) { Segmented(listOf("Авто", "Русский", "English"), listOf("auto", "ru", "en").indexOf(AppState.lang).coerceAtLeast(0)) { AppState.lang = listOf("auto", "ru", "en")[it]; AppState.save() } } }
        GroupTitle("Оформление")
        Panel { Column(Modifier.padding(16.dp)) { Segmented(listOf("Система", "Тёмная", "Светлая"), AppState.theme) { AppState.theme = it; AppState.save() } } }
        GroupTitle("Отклик")
        Panel {
            SwitchRow("Вибрация", "Короткий отклик при нажатии на кнопки, вкладки и переключатели и при успешном подключении. Если выключить, приложение не вибрирует само (системные настройки телефона не меняются).", AppState.haptics) { AppState.haptics = it; AppState.save() }
            Divider()
            SwitchRow("Показывать скорость в уведомлении", "В уведомлении VPN каждую секунду обновляются скорости загрузки и отдачи. Уведомление обновляется чаще, расход заряда чуть выше.", AppState.notifSpeed) { AppState.notifSpeed = it; AppState.save() }
        }
}

@Composable
internal fun MainActivity.SettingsService() {
    val act = this
    var showLog by remember { mutableStateOf(false) }
        GroupTitle("Root", "Необязательные функции, которым нужен root-доступ. Они помечены словом root. Пока переключатель выключен, приложение вообще не обращается к su. Сам переключатель root не запрашивает: запрос покажет менеджер root при первом нажатии на кнопку такой функции или на «Проверить root». Сейчас такая функция одна: «Щит → Чьи это порты». Переключатель не переносится через резервную копию: после восстановления он выключен.")
        Panel {
            SwitchRow("Root-функции", "Показывать и разрешать функции, которым нужен root.", AppState.rootOn) { AppState.rootOn = it; if (!it) AppState.rootInfo = null; AppState.save() }
            if (AppState.rootOn) {
                Divider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (AppState.rootInfo) { true -> "Root доступен."; false -> "Root не выдан или su не найден. Выдайте доступ приложению в менеджере root."; null -> "Root не проверялся." },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton({ RootShell.checkAsync() }, enabled = !AppState.rootChecking) { Text(if (AppState.rootChecking) "Проверяю…" else "Проверить root") }
                }
            }
        }
        GroupTitle("Обновления")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(AppState.updateUrl, { AppState.updateUrl = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Адрес файла update.json (необязательно)") })
                Text("Оставьте пустым: проверка идёт по релизам проекта на GitHub (lolokeksu09/Vitrum), берётся файл под подпись вашей сборки. Запрос уходит на api.github.com и показывает GitHub ваш IP и версию приложения; больше ничего не отправляется. Автоматически не проверяется, пока вы не включите это ниже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (AppState.updateUrl.isBlank()) SwitchRow("Проверять раз в сутки (GitHub)", "При открытии приложения, не чаще раза в сутки. По умолчанию выключено.", AppState.updAutoDefault) { AppState.updAutoDefault = it; AppState.save() }
                Text("Файл на вашем сервере: {\"version\":\"0.9.0\",\"code\":14,\"url\":\"https://…/${BRAND}.apk\",\"sha256\":\"…\",\"notes\":\"…\"}. Проверка раз в сутки при открытии.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ AppState.checkUpdate(false) }) { Text("Проверить сейчас") }
            }
        }
        GroupTitle("Тест скорости", "Скачивает тестовый файл через VPN и показывает скорость загрузки в Мбит/с. Запускается только по кнопке, качает не больше 20 МБ и не дольше 8 секунд: на мобильной сети это тратит трафик. Работает только при подключении. Файл по умолчанию на стороннем сервере (speedtest.tele2.net), его доступность я не проверял; если не отвечает, укажите свой адрес файла по http. Скорость зависит от сервера, сети и времени суток, одно измерение это ориентир.")
        Panel {
            var busy by remember { mutableStateOf(false) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(AppState.speedUrl, { AppState.speedUrl = it.trim(); AppState.save() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Адрес файла для теста (http)") })
                val can = RayVpnService.connected && Pinger.probePath.isNotEmpty() && !busy
                FilledTonalButton({
                    busy = true; SpeedTest.last = null
                    Thread { SpeedTest.last = SpeedTest.measure(Pinger.connector(Pinger.probePath), AppState.speedUrl.ifBlank { SpeedTest.DEFAULT_URL }); busy = false }.start()
                }, enabled = can) { Text(if (busy) "Измеряю…" else "Измерить скорость через VPN") }
                if (!RayVpnService.connected) Text("Подключитесь к серверу, чтобы измерить.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SpeedTest.last?.let { r ->
                    if (r.ok) {
                        Text("↓ %.1f Мбит/с".format(r.mbps), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Pal.Green)
                        Text("Получено ${size(r.bytes)} за ${"%.1f".format(r.ms / 1000.0)} с. Ответ сервера через ${r.ttfbMs} мс.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text(r.note.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Pal.Amber)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                var askAll by remember { mutableStateOf(false) }
                val cands = remember(AppState.servers.size, AppState.favs.size) { Pinger.gameCandidates() }
                FilledTonalButton({ askAll = true }, enabled = !AppState.speedAllBusy && cands.isNotEmpty()) { Text(if (AppState.speedAllBusy) "Проверяю серверы…" else "Скорость всех серверов") }
                Text("Серверы проверяются по очереди без подключения к VPN: до ${cands.size} (избранные и лучшие по рейтингу), каждому не больше ${SpeedTest.ALL_MAX_BYTES / 1024 / 1024} МБ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ranked = SpeedTest.rank(AppState.speedResults)
                ranked.forEach { (id, r) ->
                    val sv = AppState.servers.firstOrNull { it.id == id }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(sv?.name ?: "—", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("%.1f Мбит/с".format(r.mbps), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Pal.Green)
                    }
                }
                val failed = AppState.speedResults.count { !it.value.ok }
                if (failed > 0) Text("Без результата: $failed.", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                SpeedTest.fastestId(AppState.speedResults)?.let { best ->
                    if (!AppState.speedAllBusy) TextButton({ AppState.selectedId = best; AppState.save(); AppState.message = if (RayVpnService.connected) "Самый быстрый сервер выбран. Переподключитесь, чтобы он начал работать" else "Самый быстрый сервер выбран" }) { Text("Выбрать самый быстрый") }
                }
                if (askAll) AlertDialog(onDismissRequest = { askAll = false }, title = { Text("Проверить скорость серверов?") },
                    text = { Text("Будет проверено серверов: ${cands.size}. С каждого скачивается до ${SpeedTest.ALL_MAX_BYTES / 1024 / 1024} МБ, всего не больше ${cands.size * (SpeedTest.ALL_MAX_BYTES / 1024 / 1024)} МБ. На мобильной сети это расходует трафик. Проверка идёт мимо VPN, в обход текущего подключения.") },
                    confirmButton = { TextButton({ askAll = false; Pinger.speedAll(AppState.speedUrl.ifBlank { SpeedTest.DEFAULT_URL }) }) { Text("Проверить") } },
                    dismissButton = { TextButton({ askAll = false }) { Text("Отмена") } })
            }
        }
        GroupTitle("Статистика трафика", "Сколько данных прошло через приложение, пока VPN был подключён: счётчик системы для приложения (зашифрованный обмен с сервером, прямой трафик и служебные запросы). Нужен, чтобы следить за лимитом тарифа. Это не счёт оператора: он может отличаться. Хранится только на телефоне, до 62 дней.")
        Panel {
            val tick = TrafficLog.tick   // перерисовка при новых данных
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fun row(label: String, v: Pair<Long, Long>) = Triple(label, v, v.first + v.second)
                listOf(row("Сегодня", TrafficLog.lastDays(1)), row("7 дней", TrafficLog.lastDays(7)), row("Этот месяц", TrafficLog.month())).forEach { (label, v, total) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("↓ ${size(v.first)}  ↑ ${size(v.second)}  = ${size(total)}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("По мобильной сети: сегодня ${size(TrafficLog.mobileLastDays(1))} · 7 дней ${size(TrafficLog.mobileLastDays(7))} · месяц ${size(TrafficLog.mobileMonth())}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                val rows = TrafficLog.byDay(7)
                val peak = rows.maxOf { it.second + it.third }.coerceAtLeast(1L)
                rows.forEach { (d, rx, tx) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.substring(5), Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UsageBar((rx + tx).toFloat() / peak, Modifier.weight(1f), height = 6.dp)
                        Text(size(rx + tx), Modifier.width(78.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.End)
                    }
                }
                var lim by remember { mutableStateOf(if (AppState.trafficLimitGb > 0) AppState.trafficLimitGb.toString() else "") }
                OutlinedTextField(lim, { v -> lim = v.filter { it.isDigit() }.take(5); AppState.trafficLimitGb = lim.toIntOrNull() ?: 0; AppState.save() }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    label = { Text("Лимит за месяц, ГБ") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    supportingText = { Text("Пусто или 0: не предупреждать. Иначе уведомление при 80 % и при 100 % лимита, по разу в месяц.") })
                if (AppState.trafficLimitGb > 0) SwitchRow("Лимит только по мобильной сети", "Wi-Fi в лимит не входит. Тип сети определяется раз в несколько секунд, поэтому разбивка приблизительная; общий итог выше не меняется.", AppState.trafficLimitMobileOnly) { AppState.trafficLimitMobileOnly = it; AppState.save() }
                if (AppState.trafficLimitGb > 0) {
                    val used = TrafficLimit.used(TrafficLog.month().let { it.first + it.second }, TrafficLog.mobileMonth(), AppState.trafficLimitMobileOnly); val limit = AppState.trafficLimitGb.toLong() * 1024 * 1024 * 1024
                    UsageBar(used.toFloat() / limit, Modifier.fillMaxWidth(), color = if (used >= limit) Pal.Red else if (used * 10 >= limit * 8) Pal.Amber else Pal.Green, height = 6.dp)
                }
                TextButton({ TrafficLog.clear(); TrafficLog.save(act) }, Modifier.padding(top = 4.dp)) { Text("Сбросить статистику") }
            }
        }
        GroupTitle("Трафик по приложениям", "Какие приложения расходуют трафик по Wi-Fi и по мобильной сети. Нужен «Доступ к истории использования» (особое разрешение, выдаётся в системных настройках). Приложение читает только счётчики трафика на этом телефоне и никуда их не отправляет.")
        Panel {
            var period by remember { mutableStateOf(AppTraffic.Period.TODAY) }
            var tick by remember { mutableIntStateOf(0) }
            val access = remember(tick) { AppTraffic.hasAccess(act) }
            fun load(p: AppTraffic.Period) {
                if (AppState.appTrafficBusy) return
                AppState.appTrafficBusy = true
                Thread { try { AppState.appTraffic = AppTraffic.query(act, p) } finally { AppState.appTrafficBusy = false } }.start()
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!access) {
                    Text("Доступ к истории использования не выдан.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton({ AppTraffic.openAccessSettings(act) }) { Text("Открыть настройки доступа") }
                    TextButton({ tick++ }, contentPadding = PaddingValues(0.dp)) { Text("Я выдал доступ: проверить снова") }
                } else {
                    Segmented(listOf("Сегодня", "7 дней", "Месяц"), period.ordinal) { period = AppTraffic.Period.entries[it]; load(period) }
                    FilledTonalButton({ load(period) }, enabled = !AppState.appTrafficBusy) { Text(if (AppState.appTrafficBusy) "Считаю…" else "Показать") }
                    val r = AppState.appTraffic
                    if (r == null || r.period != period) Text("Нажмите «Показать».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        if (r.rows.isEmpty()) Text("За этот период данных нет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val peak = r.rows.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                        r.rows.take(15).forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(size(row.total), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                                }
                                UsageBar(row.total.toFloat() / peak, Modifier.fillMaxWidth(), height = 4.dp)
                                Text(if (r.mobileKnown) "Wi-Fi ${size(row.wifi)} · мобильная ${size(row.mobile)}" else "Wi-Fi ${size(row.wifi)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!r.mobileKnown) Text("Мобильную сеть система не отдала: показан только Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                        if (r.rows.size > 15) Text("Показаны 15 самых больших из ${r.rows.size}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        GroupTitle("Память Xray", "Мягкий предел памяти для ядра (переменная Go GOMEMLIMIT). Если приложение закрывает система из-за нехватки памяти, попробуйте 256 МиБ, затем 128 МиБ. Чем меньше предел, тем чаще ядро чистит память и тем больше расходует процессор. Применяется при следующем подключении. Эффект на телефоне не проверялся.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("Выкл", "128 МиБ", "256 МиБ"), AppState.memLimit) { AppState.memLimit = it; AppState.save() }
                Text("Если VPN включён, переподключитесь, чтобы изменение вступило в силу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GroupTitle("Журнал Xray", "Уровень записи в журнал ядра. error: только ошибки. warning: ошибки и предупреждения (по умолчанию). info: ещё и сведения о соединениях. debug: подробно, для поиска причины сбоя. На info и debug в журнал попадают адреса и домены, к которым вы обращаетесь; журнал лежит только в закрытой папке приложения и очищается при следующем подключении. Верните warning, когда разберётесь.")
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("error", "warning", "info", "debug"), XrayLog.LEVELS.indexOf(AppState.xrayLog).coerceAtLeast(1)) { AppState.xrayLog = XrayLog.LEVELS[it]; AppState.save(); if (RayVpnService.connected) RayVpnService.requestReload() }
                TextButton({ runCatching { java.io.File(filesDir, "xray.log").writeText("") }; RayVpnService.logTail = ""; AppState.message = "Журнал Xray очищен" }) { Text("Очистить журнал Xray") }
                if (AppState.xrayLog == "info" || AppState.xrayLog == "debug")
                    Text("В журнале будут адреса и домены ваших соединений. Включайте на время поиска сбоя. Журнал можно посмотреть ниже в «Диагностике».", style = MaterialTheme.typography.bodySmall, color = Pal.Amber)
                Text("Если VPN включён, туннель перезапустится.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GroupTitle("Диагностика")
        Panel {
            Column(Modifier.padding(16.dp)) {
                val selSv = AppState.servers.firstOrNull { it.id == AppState.selectedId }
                Text(selSv?.let { "Выбран: ${it.name}" } ?: "Выберите сервер на главном экране", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ AppState.diagBusy = true; Thread { AppState.diag = try {
                    val w = ConnWizard.run(act, selSv)
                    // найденная причина уже названа: долгую подробную проверку запускаем, только если явной причины нет
                    if (selSv != null && !ConnWizard.hasCause(w)) w + "\n\n— Подробная проверка сервера —\n" + Diagnostics.run(selSv) else w
                } catch (e: Exception) { "Ошибка: ${e.message}" }; AppState.diagBusy = false }.start() },
                    Modifier.padding(top = 8.dp), enabled = !AppState.diagBusy) { Text(if (AppState.diagBusy) "Проверка…" else "Почему не подключается") }
                FilledTonalButton({ selSv?.let { sv -> AppState.diagBusy = true; Thread { AppState.diag = try { Diagnostics.run(sv) } catch (e: Exception) { "Ошибка: ${e.message}" }; AppState.diagBusy = false }.start() } },
                    Modifier.padding(top = 8.dp), enabled = selSv != null && !AppState.diagBusy) { Text(if (AppState.diagBusy) "Проверка…" else "Проверить выбранный сервер") }
                FilledTonalButton({ AppState.diagBusy = true; Thread { AppState.diag = try { LeakCheck.run() } catch (e: Exception) { "Ошибка: ${e.message}" }; AppState.diagBusy = false }.start() },
                    Modifier.padding(top = 8.dp), enabled = !AppState.diagBusy) { Text("Проверка портов и IP") }
                TextButton({ AppState.diagBusy = true; Thread { AppState.diag = try { LeakCheck.run(true) } catch (e: Exception) { "Ошибка: ${e.message}" }; AppState.diagBusy = false }.start() },
                    enabled = !AppState.diagBusy) { Text("Полное сканирование портов (до минуты)") }
                TextButton({ showLog = !showLog }, Modifier.padding(top = 4.dp)) { Text(if (showLog) "Скрыть лог Xray" else "Показать лог Xray") }
                if (showLog) Text(RayVpnService.logTail.ifBlank { "Пока пусто" }, style = MaterialTheme.typography.bodySmall)
            }
        }
}

@Composable
internal fun MainActivity.SettingsHelp() {
    Panel(Modifier.padding(top = 8.dp)) {
        GLOSSARY.forEachIndexed { i, (term, text) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(term, style = MaterialTheme.typography.titleSmall)
                Text(text, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < GLOSSARY.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}
