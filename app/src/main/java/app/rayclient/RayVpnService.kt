package app.rayclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ServiceCompat
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

class RayVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.rayclient.START"
        const val ACTION_STOP = "app.rayclient.STOP"
        private const val HEALTH_URL = "http://cp.cloudflare.com/generate_204"
        var state by mutableStateOf("Отключено")
        var connected by mutableStateOf(false)
        var busy by mutableStateOf(false)
        var blocked by mutableStateOf(false)      // kill switch держит туннель закрытым
        var logTail by mutableStateOf("")
        var hint by mutableStateOf("")
        var since by mutableLongStateOf(0L)
        /** Ответ системы собственной службе: запущена ли она как «Постоянный VPN» и включена ли блокировка соединений без VPN (Android 10+). */
        var alwaysOn by mutableStateOf<Boolean?>(null)
        var lockdown by mutableStateOf<Boolean?>(null)
        @Volatile private var reloadRequested = false
        /** Перечитать настройки и перезапустить Xray, не закрывая туннель (например, после нового правила). */
        fun requestReload() { reloadRequested = true }

        /** Переводит сырой лог Xray в понятное сообщение. */
        fun friendly(raw: String): String {
            val t = raw.trim()
            Regex("""code not found in (geosite|geoip)\.dat: (\S+)""").find(t)?.let { return "В правилах неизвестный ${it.groupValues[1]}: ${it.groupValues[2].lowercase()}" }
            return when {
                "address already in use" in t -> "Порт занят другой программой"
                "failed to load config" in t || "Failed to start" in t -> "Ошибка конфигурации: " + t.lines().lastOrNull { it.isNotBlank() }.orEmpty().takeLast(160)
                "REALITY" in t && ("invalid" in t || "verification" in t) -> "Сервер отклонил соединение: проверьте ключ Reality, sid и sni"
                "connection refused" in t -> "Сервер отказал в соединении"
                "i/o timeout" in t || "timeout" in t -> "Сервер не отвечает (таймаут)"
                "no such host" in t -> "Не удалось найти адрес сервера"
                else -> t.lines().lastOrNull { it.isNotBlank() }.orEmpty().takeLast(160)
            }
        }
    }

    private var pfd: ParcelFileDescriptor? = null
    @Volatile private var pid = 0
    @Volatile private var gen = 0
    @Volatile private var netChanged = false
    @Volatile private var usesHy = false
    @Volatile private var lastNet: Network? = null
    private var cm: ConnectivityManager? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private lateinit var exe: String
    private lateinit var cfgPath: String
    private lateinit var logFile: File

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppState.init(this)
        when (intent?.action) {
            // «android.net.VpnService» приходит, когда систему просят поднять «Постоянный VPN» (Always-on)
            ACTION_START, "android.net.VpnService" -> { goForeground("Подключение…"); val g = ++gen; Thread { start(g) }.start() }
            // Отключение в фоне: остановка Xray ждёт до 300 мс, раньше это подвешивало интерфейс. Под тем же замком, что и start(),
            // поэтому не пересекается с идущим подключением; если после «Отключить» уже нажали «Подключить», ничего не делаем.
            ACTION_STOP -> { val g = ++gen; Thread { synchronized(this) { if (g == gen) { stop(true); XrayOrphans.kill(xrayPath(this)); stopForegroundAndSelf() } } }.start() }
        }
        return START_NOT_STICKY
    }

    // ---------- уведомление и плитка ----------
    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopI = PendingIntent.getService(this, 1, Intent(this, RayVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = Notification.Builder(this, "vpn").setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(BRAND).setContentText(I18n.tr(text)).setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, I18n.tr("Отключить"), stopI)
        if (AppState.privateNotif) b.setVisibility(Notification.VISIBILITY_PRIVATE)   // на заблокированном экране названия сервера нет
            .setPublicVersion(Notification.Builder(this, "vpn").setSmallIcon(R.drawable.ic_tile).setContentTitle(BRAND).setContentText(I18n.tr("VPN работает")).build())
        return b.build()
    }

    private fun goForeground(text: String) {
        runCatching {
            ServiceCompat.startForeground(this, 1, notification(text),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }
    }

    private fun updateNotification(text: String) { runCatching { getSystemService(NotificationManager::class.java).notify(1, notification(text)) } }

    private fun tileRefresh() { VpnWidget.refresh(this); runCatching { TileService.requestListeningState(this, ComponentName(this, VpnTileService::class.java)) } }

    private fun stopForegroundAndSelf() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun hasNetwork(): Boolean = runCatching {
        val c = cm ?: getSystemService(ConnectivityManager::class.java)
        val n = c.activeNetwork
        n != null && c.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    // ---------- запуск ----------
    private fun fail(msg: String, withLog: Boolean = false) {
        val raw = if (withLog) runCatching { LogTail.read(logFile) }.getOrDefault("") else ""
        logTail = raw.takeLast(1500)
        var text = if (raw.isNotBlank()) friendly(raw).ifBlank { msg } else msg
        // скачанная база не подошла (Xray не нашёл метку или не смог прочитать файл): возвращаемся на встроенную, чтобы приложение не осталось без VPN
        if (AppState.ruGeo && Regex("""(geoip|geosite)\.dat""").containsMatchIn(raw)) { AppState.ruGeo = false; AppState.save(); text += ". Скачанная база IP отключена, подключитесь снова" }
        if (AppState.killSwitchEffective() && pfd != null) {
            // Kill switch: Xray остановлен, а туннель остаётся открытым и никем не читается, поэтому трафик не уходит мимо VPN.
            val p = pid; pid = 0; Native.stop(p)
            unregisterNet()
            connected = false; busy = false; blocked = true; since = 0L
            state = "Ошибка: $text"; hint = "Kill switch: интернет заблокирован, пока вы не отключитесь"
            updateNotification("Kill switch: трафик заблокирован"); tileRefresh()
            return
        }
        stop(false); state = "Ошибка: $text"
        updateNotification("Ошибка: $text")
        stopForegroundAndSelf()
    }

    /** Конфигурация с ключами сервера: файл только для приложения, удаляется при отключении. */
    private fun writeConfig(text: String): String = File(filesDir, "config.json").apply {
        delete(); createNewFile(); setReadable(false, false); setReadable(true, true); setWritable(false, false); setWritable(true, true); writeText(text)
    }.absolutePath

    /** Замок на смену pid: `watch` не должен запускать Xray после остановки или нового подключения (иначе два Xray на одном туннеле). */
    private val pidLock = Any()

    /** Перезапуск Xray из `watch`: только если это всё ещё то же подключение (gen) и туннель открыт. */
    private fun relaunch(g: Int): Boolean = synchronized(pidLock) {
        if (g != gen || pfd == null) false else { pid = launch(); true }
    }

    private fun launch(): Int {
        // предел памяти Xray: переменная окружения наследуется дочерним процессом (применяется при запуске)
        runCatching { MemLimit.env(AppState.memLimit)?.let { android.system.Os.setenv("GOMEMLIMIT", it, true) } ?: android.system.Os.unsetenv("GOMEMLIMIT") }
        return Native.spawn(exe, cfgPath, GeoData.assetDir(this), logFile.absolutePath, pfd!!.fd)
    }

    /** Разбирает ссылку сервера, определяет IP и пишет config.json. Служебный вход — unix-сокет в закрытой папке. */
    private fun prepare(sv: Server): ParsedLink {
        if (sv.isAuto) return prepareAuto(sv)
        val parsed = Links.parse(sv.link)
        val ip = hostIp(parsed.host)
        val sock = File(File(filesDir, "probe").apply { mkdirs() }, "main.sock").apply { delete() }
        Pinger.probePath = sock.absolutePath
        val acc = File(filesDir, "access.log").apply { delete() }   // журнал адресов включается только по желанию
        cfgPath = writeConfig(ConfigBuilder.build(parsed, Links.withAddress(parsed, ip), AppState.routingOptions(), sock.absolutePath, if (AppState.connLog) acc.absolutePath else null))
        usesHy = HyRestart.usesHysteria(parsed.proto, null)
        return parsed
    }

    private fun hostIp(host: String): String = InetAddress.getAllByName(host).let { a -> (a.firstOrNull { it is Inet4Address } ?: a.first()).hostAddress!! }

    /** Авто-сервер (JSON-подписка): имена серверов заранее превращаются в IP, балансировщик и замеры берутся из конфигурации панели. */
    private fun prepareAuto(sv: Server): ParsedLink {
        val plan = AutoStore.get(this, sv.link) ?: error("конфигурация авто-сервера не найдена, обновите подписку")
        val ips = AutoSub.resolveAll(AutoSub.domains(plan), 8000) { runCatching { hostIp(it) }.getOrNull() }
        val pinned = AutoSub.pinAddresses(plan) { ips[it] } ?: throw java.net.UnknownHostException()
        val sock = File(File(filesDir, "probe").apply { mkdirs() }, "main.sock").apply { delete() }
        Pinger.probePath = sock.absolutePath
        val acc = File(filesDir, "access.log").apply { delete() }
        val cfgText = ConfigBuilder.buildAuto(pinned, AppState.routingOptions(), sock.absolutePath, if (AppState.connLog) acc.absolutePath else null)
        cfgPath = writeConfig(cfgText)
        usesHy = HyRestart.usesHysteria("auto", cfgText)
        return ParsedLink(sv.name, "auto", "", 0, org.json.JSONObject())
    }

    /** Выбранный сервер — авто: переключением между серверами занимается балансировщик Xray, поэтому своё автопереключение не нужно. */
    private fun selectedIsAuto() = AppState.servers.firstOrNull { it.id == AppState.selectedId }?.isAuto == true

    private fun registerNet() {
        runCatching {
            val c = getSystemService(ConnectivityManager::class.java); cm = c
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val prev = lastNet; lastNet = network
                    if (prev != null && prev != network && AppState.reconnectNet && connected) netChanged = true
                }
            }
            netCb = cb; c.registerDefaultNetworkCallback(cb)
        }
    }

    private fun unregisterNet() { runCatching { netCb?.let { cm?.unregisterNetworkCallback(it) } }; netCb = null; lastNet = null }

    @Synchronized private fun start(g: Int) {
        // Kill switch: старый туннель не закрываем, пока не открыт новый, иначе сбой DNS или «не выбран сервер» до establish() выпустил бы трафик мимо VPN
        val oldTun = if (AppState.killSwitchEffective()) pfd else null
        stop(false, keepTun = oldTun != null); busy = true; state = "Подключение…"; logTail = ""; hint = ""
        logFile = File(filesDir, "xray.log").apply { writeText("") }
        try {
            val sv = AppState.servers.firstOrNull { it.id == AppState.selectedId } ?: return fail("не выбран сервер")
            val parsed = try { prepare(sv) } catch (e: java.net.UnknownHostException) { return fail("не удалось найти адрес сервера") }
            exe = xrayPath(this)
            // Xray от прошлого запуска приложения (оно было убито системой) не должен остаться рядом с новым
            XrayOrphans.kill(exe)

            val b = Builder().setSession(parsed.name).setMtu(TunParams.orDefault(AppState.mtu))
                .addAddress("172.19.0.1", 30).addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("0.0.0.0", 0).addRoute("::", 0)
                .addDnsServer(AppState.dnsSystemIp())
            // Xray работает под UID приложения, поэтому приложение в туннель не пускаем: иначе петля.
            var allowed = 0
            when (AppState.appMode) {
                1 -> AppState.apps.filter { it != packageName }.forEach { runCatching { b.addAllowedApplication(it); allowed++ } }
                else -> {
                    b.addDisallowedApplication(packageName)
                    if (AppState.appMode == 2) AppState.apps.forEach { runCatching { b.addDisallowedApplication(it) } }
                }
            }
            // без единого разрешённого приложения туннель взял бы все приложения, включая само: Xray замкнулся бы на себя
            if (AppState.appMode == 1 && allowed == 0) return fail("не выбрано ни одного приложения для режима «Только выбранные»")
            if (g != gen) return
            pfd = b.establish() ?: return fail("система не выдала TUN (VPN отозван или нет разрешения)")
            oldTun?.let { runCatching { it.close() } }
            if (Build.VERSION.SDK_INT >= 29) { alwaysOn = isAlwaysOn; lockdown = isLockdownEnabled }
            pid = launch()
            Thread.sleep(1500)
            // alive() уже убрал завершившийся процесс: его PID больше не наш, kill по нему слать нельзя
            if (!Native.alive(pid)) { pid = 0; return fail("xray не запустился", withLog = true) }
            state = "Подключено: ${sv.name}"; connected = true; busy = false; blocked = false; since = System.currentTimeMillis()
            updateNotification("Подключено: ${sv.name}"); tileRefresh()
            registerNet()
            if (AppState.connLog) ConnLog.startReader(File(filesDir, "access.log"))
            Thread { watch(g, sv.name) }.start()
            Thread { gameMon(g, sv.name) }.start()
            Thread { speedMon(g) }.start()
        } catch (e: Exception) { fail(e.message ?: e.javaClass.simpleName, withLog = true) }
    }

    /** Проверка связи через туннель: HEAD через служебный сокет. Ошибки самого сокета не считаются сбоем сервера. */
    private fun probeOk(timeout: Int): HeadResult = HeadProbe.head(HEALTH_URL, Pinger.connector(Pinger.probePath), timeout, false)

    /** Следит за Xray: показывает лог, перезапускает при смене сети и падении процесса, уходит на другой сервер, если текущий замолчал. */
    private fun watch(g: Int, name0: String) {
        try { watchLoop(g, name0) } catch (e: Exception) {
            // исключение в фоновом потоке не должно ронять приложение; если это ещё текущее подключение, сообщаем об ошибке
            if (g == gen) fail("внутренняя ошибка: ${e.javaClass.simpleName}", withLog = true)
        }
    }

    private fun watchLoop(g: Int, name0: String) {
        var name = name0
        var restarts = 0; var lastStart = System.currentTimeMillis(); var lastCheck = lastStart
        val wd = Failover.Watch()
        while (g == gen && pid > 0) {
            if (reloadRequested) {
                reloadRequested = false; wd.reset()
                val cur = AppState.servers.firstOrNull { it.id == AppState.selectedId }
                if (cur != null && runCatching { prepare(cur) }.isSuccess) {
                    val old = pid; pid = 0; Native.stop(old); Thread.sleep(300)
                    if (g != gen || pfd == null) return
                    if (AppState.connLog) ConnLog.startReader(File(filesDir, "access.log")) else { ConnLog.stopReader(); ConnLog.clear() }
                    if (!relaunch(g)) return
                    Thread.sleep(1500); lastStart = System.currentTimeMillis()
                    if (Native.alive(pid)) { state = "Подключено: ${cur.name}"; updateNotification(state) }
                }
                continue
            }
            if (netChanged) {
                netChanged = false; wd.reset()
                state = "Смена сети…"; updateNotification(state)
                Native.stop(pid); Thread.sleep(300)
                if (g != gen || pfd == null) return
                File(Pinger.probePath).delete()
                if (!relaunch(g)) return
                Thread.sleep(1500); lastStart = System.currentTimeMillis()
                if (Native.alive(pid)) { state = "Подключено: $name"; updateNotification(state) }
                continue
            }
            if (Native.alive(pid)) {
                val raw = runCatching { LogTail.read(logFile) }.getOrDefault("")
                logTail = raw.takeLast(1500)
                hint = if (listOf("i/o timeout", "connection refused", "REALITY", "no such host").any { it in raw.takeLast(800) }) friendly(raw.takeLast(800)) else ""
                val now = System.currentTimeMillis()
                if (now - lastStart > 60_000) restarts = 0
                if (connected && HyRestart.due(AppState.hyRestart, usesHy, AppState.hyRestartMin, now - lastStart)) {
                    // Hysteria2 по таймеру: Xray перезапускается, туннель остаётся открытым
                    wd.reset()
                    state = "Перезапуск Xray (Hysteria2)…"; updateNotification(state)
                    Native.stop(pid); Thread.sleep(300)
                    if (g != gen || pfd == null) return
                    File(Pinger.probePath).delete()
                    if (!relaunch(g)) return
                    Thread.sleep(1500); lastStart = System.currentTimeMillis()
                    if (Native.alive(pid)) { state = "Подключено: $name"; updateNotification(state) }
                    continue
                }
                if (AppState.autoSwitch && !wd.disabled && connected && !selectedIsAuto() && now - lastStart > 12_000 && now - lastCheck >= 10_000) {
                    lastCheck = now
                    val r = probeOk(5000)
                    if (r.note != HeadProbe.PROXY_UNAVAILABLE) AppState.servers.firstOrNull { it.id == AppState.selectedId }?.let { Health.add(it.link, r.ms >= 0, r.ms) }
                    when (wd.onResult(r.ms >= 0, r.note, hasNetwork())) {
                        Failover.Verdict.DISABLED -> AppState.message = "Автопереключение отключено: служебный сокет недоступен"
                        Failover.Verdict.SWITCH -> {
                            if (!failover(g)) return
                            name = AppState.servers.firstOrNull { it.id == AppState.selectedId }?.name ?: name
                            lastStart = System.currentTimeMillis(); lastCheck = lastStart; restarts = 0
                        }
                        else -> {}
                    }
                }
                Thread.sleep(1000)
            } else {
                if (restarts >= 3) { pid = 0; fail("xray остановился", withLog = true); return }   // процесс уже убран alive()
                restarts++; connected = false; state = "Переподключение ($restarts/3)…"
                updateNotification(state)
                Thread.sleep(restarts * 1000L)
                if (g != gen || pfd == null) return
                File(Pinger.probePath).delete()
                if (!relaunch(g)) return
                Thread.sleep(1500); lastStart = System.currentTimeMillis()
                if (Native.alive(pid)) { connected = true; state = "Подключено: $name"; updateNotification(state) }
            }
        }
    }

    /** Игровой режим: постоянный туннель через служебный сокет, замер раз в 2 с; пинг, джиттер и потери в приложении и в уведомлении. */
    private fun gameMon(g: Int, name: String) {
        var tun: Tunnel? = null; var tgt = ""; var lastNote = 0L
        val buf = ArrayDeque<Int>()
        while (g == gen && pfd != null) {   // не pid: при перезапуске Xray он на время равен 0, и мониторинг выходил насовсем
            if (!AppState.gameMode || !connected || Pinger.probePath.isEmpty()) {
                tun?.close(); tun = null; tgt = ""; buf.clear()
                if (AppState.gameLive != null) AppState.gameLive = null
                try { Thread.sleep(1500) } catch (_: InterruptedException) { return }
                continue
            }
            val target = AppState.gameTarget
            if (tun == null || tgt != target) {
                tun?.close(); buf.clear(); tgt = target
                val t = GameStats.parseTarget(target) ?: Target("cp.cloudflare.com", 80, "/generate_204")
                tun = Tunnel(Pinger.connector(Pinger.probePath), t.host, t.port, t.path, 3000).also { if (it.open() >= 0) it.ping() }   // прогрев не считается
            }
            val r = tun!!.ping()
            if (r < 0 && tun.open() >= 0) tun.ping()
            buf.addLast(r); while (buf.size > 40) buf.removeFirst()
            val live = GameStats.live(buf.toList()); AppState.gameLive = live
            val now = System.currentTimeMillis()
            if (now - lastNote > 6000 && connected) { lastNote = now; updateNotification("Подключено: $name · " + if (live.avg >= 0) "пинг ${live.avg} мс · джиттер ${live.jitter} мс · потери ${live.loss}%" else "нет ответа") }
            try { Thread.sleep(2000) } catch (_: InterruptedException) { return }
        }
        tun?.close(); AppState.gameLive = null
    }

    /** Текущий сервер не отвечает: пробуем до 6 других (сначала избранные, потом с лучшим пингом). Туннель при этом не закрывается. */
    private fun failover(g: Int): Boolean {
        val cur = AppState.selectedId
        val cands = Failover.candidates(AppState.servers.filter { !it.isAuto }, cur, AppState.favs.toSet(), { Health.rank(it.link, AppState.pings[it.id]?.takeIf { p -> p > 0 }) })
        if (cands.isEmpty()) return true
        state = "Сервер не отвечает, ищу другой…"; updateNotification(state)
        for (c in cands) {
            if (g != gen || pfd == null) return false
            runCatching { prepare(c) }.getOrNull() ?: continue
            val old = pid; pid = 0; Native.stop(old)
            Thread.sleep(300)
            if (g != gen || pfd == null) return false
            if (!relaunch(g)) return false
            Thread.sleep(1500)
            if (!Native.alive(pid)) { pid = 0; continue }
            var ok = false
            repeat(2) { if (!ok) ok = probeOk(6000).ms >= 0 }
            if (ok) {
                AppState.selectedId = c.id; AppState.save()
                state = "Подключено: ${c.name}"; connected = true; hint = ""
                updateNotification(state)
                AppState.message = "Сервер не отвечал. Переключился на ${c.name}"
                return true
            }
        }
        fail("ни один сервер не отвечает")
        return false
    }

    /** Скорость для главного экрана считается здесь, а не в интерфейсе: график не обнуляется при смене вкладки. */
    private fun speedMon(g: Int) {
        val uid = android.os.Process.myUid()
        var lr = TrafficStats.getUidRxBytes(uid); var lt = TrafficStats.getUidTxBytes(uid); var t = System.nanoTime()
        val r0 = lr; val t0 = lt
        Speed.reset()
        var lastSave = System.currentTimeMillis()
        var mobile = TrafficLog.mobileNow(this); var step = 0   // тип сети для статистики (Wi-Fi или мобильная) проверяется раз в 5 секунд
        try {
        while (g == gen && pfd != null) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { return }
            if (step++ % 5 == 4) mobile = TrafficLog.mobileNow(this)
            val r = TrafficStats.getUidRxBytes(uid); val x = TrafficStats.getUidTxBytes(uid); val now = System.nanoTime()
            val sec = ((now - t) / 1e9).coerceAtLeast(0.2)
            val d = ((r - lr).coerceAtLeast(0) / sec).toLong(); val u = ((x - lt).coerceAtLeast(0) / sec).toLong()
            Speed.add(d, u, (r - r0) + (x - t0))
            // «Показывать скорость в уведомлении» (по умолчанию выключено): раз в секунду, только пока подключено
            if (AppState.notifSpeed && connected && state.startsWith("Подключено")) updateNotification("$state · ↓ ${speed(d)} ↑ ${speed(u)}")
            // трафик по дням: приращения счётчика за этот шаг (на запись на диск раз в минуту и при остановке)
            if (lr >= 0 && lt >= 0 && r >= 0 && x >= 0) TrafficLog.add(TrafficLog.today(), (r - lr).coerceAtLeast(0), (x - lt).coerceAtLeast(0), mobile)   // -1: система не отдаёт счётчик
            if (System.currentTimeMillis() - lastSave > 60_000) { lastSave = System.currentTimeMillis(); TrafficLog.save(this); TrafficLimit.check(this) }
            lr = r; lt = x; t = now
        }
        } finally { TrafficLog.save(this) }
    }

    /** [keepTun]: туннель остаётся открытым (kill switch при переподключении); закрыть его должен тот, кто открыл новый или решил отключиться. */
    private fun stop(user: Boolean, keepTun: Boolean = false) {
        val p = synchronized(pidLock) { pid.also { pid = 0 } }
        Native.stop(p)
        unregisterNet()
        ConnLog.stopReader(); ConnLog.clear(); runCatching { File(filesDir, "access.log").delete() }
        runCatching { Health.save(this) }
        runCatching { File(filesDir, "config.json").delete() }   // в конфигурации ключи сервера
        if (!keepTun) synchronized(pidLock) { runCatching { pfd?.close() }; pfd = null }
        connected = false; busy = false; blocked = false; hint = ""; since = 0L; Pinger.probePath = ""; Speed.reset()
        AppState.myIp = ""
        if (!keepTun) { alwaysOn = null; lockdown = null }   // системные настройки могли измениться; до следующего подключения Shield не должен считать их проверенными
        if (user) state = "Отключено" else if (!state.startsWith("Ошибка")) state = "Отключено"
        tileRefresh()
    }

    override fun onRevoke() { gen++; stop(true); stopForegroundAndSelf(); super.onRevoke() }
    override fun onDestroy() { gen++; stop(false); super.onDestroy() }
}
