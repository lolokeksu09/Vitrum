package app.rayclient

import android.content.Context
import androidx.work.*
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object Pinger {
    /** Путь сокета служебного входа основного Xray (пусто, пока нет подключения). */
    @Volatile var probePath = ""
    @Volatile private var running = false

    fun connector(path: String) = Connector { t -> LocalConn(path, t) }

    private fun resolve(host: String): String = InetAddress.getAllByName(host).let { a -> (a.firstOrNull { it is Inet4Address } ?: a.first()).hostAddress!! }

    /** «Через сам сервер» в фоновом потоке. */
    fun viaServers(list: List<Server> = AppState.servers.toList(), fast: Boolean = false, onDone: ((String?) -> Unit)? = null) {
        if (running) { onDone?.invoke(null); return }
        Thread { runBatch(list, fast, onDone) }.start()
    }

    /**
     * Синхронный замер: временный Xray с отдельным сокетом на каждый сервер. Портов не открывается, подключение к VPN не нужно.
     * Каждый результат пишется в историю (рейтинг стабильности). onDone получает id лучшего сервера по рейтингу или null.
     */
    fun runBatch(list0: List<Server>, fast: Boolean, onDone: ((String?) -> Unit)? = null) {
        val list = list0.filter { !AutoSub.isAuto(it.link) }   // у авто-серверов нет одной ссылки: их выбирает балансировщик
        synchronized(this) { if (running) { onDone?.invoke(null); return }; running = true }
        val ctx = AppState.context()
        list.forEach { AppState.pings[it.id] = -2; AppState.pingNotes.remove(it.id) }
        AppState.pingBusy = true
        var pid = 0
        val dir = File(ctx.filesDir, "probe-ping").apply { deleteRecursively(); mkdirs() }
        try {
            val items = list.mapNotNull { sv ->
                runCatching { val p = Links.parse(sv.link); Triple(sv.id, p, resolve(p.host)) }
                    .getOrNull().also { if (it == null) { AppState.pingNotes[sv.id] = "адрес не найден"; AppState.pings[sv.id] = -1 } }
            }
            if (items.isEmpty()) return
            val cfg = File(ctx.filesDir, "ping-config.json").apply { writeText(ConfigBuilder.buildProbe(items.map { it.second to it.third }, dir.absolutePath)) }
            val log = File(ctx.filesDir, "ping.log").apply { writeText("") }
            pid = Native.spawn(xrayPath(ctx), cfg.absolutePath, ctx.filesDir.absolutePath, log.absolutePath, -1)
            val paths = items.indices.map { File(dir, "p$it.sock").absolutePath }
            var up = false
            repeat(40) { if (!up) { up = File(paths[0]).exists(); if (!up) Thread.sleep(150) } }
            if (!up) { items.forEach { AppState.pingNotes[it.first] = "проверка не запустилась"; AppState.pings[it.first] = -1 }; AppState.message = "Не удалось запустить проверку"; return }
            val byId = list.associateBy { it.id }
            HeadProbe.runAll(items.map { it.third }, paths.map { connector(it) }, AppState.pingUrl, AppState.pingWorkers(6), if (fast) 3500 else 8000, !fast) { i, r ->
                AppState.pingNotes[items[i].first] = if (items[i].second.proto == "hysteria2" && r.note == "таймаут") "таймаут: Hysteria2 идёт по UDP, сеть может его блокировать" else r.note
                AppState.pings[items[i].first] = r.ms
                // сбой самого служебного сокета не считается сбоем сервера
                if (r.note != HeadProbe.PROXY_UNAVAILABLE) byId[items[i].first]?.let { Health.add(it.link, r.ms >= 0, r.ms) }
            }
        } finally {
            Native.stop(pid)
            runCatching { dir.deleteRecursively() }
            runCatching { File(ctx.filesDir, "ping-config.json").delete(); File(ctx.filesDir, "ping.log").delete() }
            runCatching { Health.save(ctx) }
            running = false; AppState.pingBusy = false
            onDone?.invoke(list.filter { (AppState.pings[it.id] ?: -1) > 0 }.maxByOrNull { Health.rank(it.link, AppState.pings[it.id]) }?.id)
        }
    }

    /** Серверы-кандидаты для игрового теста: избранные, затем лучшие по рейтингу, не больше 10. */
    fun gameCandidates(): List<Server> = AppState.servers.filter { !AutoSub.isAuto(it.link) }.sortedWith(compareByDescending<Server> { it.link in AppState.favs }.thenByDescending { Health.rank(it.link, AppState.pings[it.id]?.takeIf { p -> p > 0 }) }).take(10)

    /**
     * Игровой тест: по каждому серверу 12 замеров по уже открытому туннелю (задержка, джиттер, потери). Результаты появляются по мере готовности.
     * Портов не открывается: временный Xray со своим сокетом на сервер.
     */
    fun gameTest(list: List<Server> = gameCandidates()) {
        if (AppState.gameBusy) return
        synchronized(this) { if (running) { AppState.message = "Подождите: идёт другой замер"; return }; running = true }
        AppState.gameBusy = true; AppState.gameResults = emptyList()
        Thread {
            val ctx = AppState.context(); var pid = 0
            val dir = File(ctx.filesDir, "probe-game").apply { deleteRecursively(); mkdirs() }
            try {
                val items = list.mapNotNull { sv -> runCatching { val p = Links.parse(sv.link); Triple(sv, p, resolve(p.host)) }.getOrNull() }
                if (items.isEmpty()) return@Thread
                val cfg = File(ctx.filesDir, "game-config.json").apply { writeText(ConfigBuilder.buildProbe(items.map { it.second to it.third }, dir.absolutePath)) }
                val log = File(ctx.filesDir, "game.log").apply { writeText("") }
                pid = Native.spawn(xrayPath(ctx), cfg.absolutePath, ctx.filesDir.absolutePath, log.absolutePath, -1)
                val paths = items.indices.map { File(dir, "p$it.sock").absolutePath }
                var up = false
                repeat(40) { if (!up) { up = File(paths[0]).exists(); if (!up) Thread.sleep(150) } }
                if (!up) { AppState.message = "Не удалось запустить тест"; return@Thread }
                val target = GameStats.parseTarget(AppState.gameTarget) ?: Target("cp.cloudflare.com", 80, "/generate_204")
                val rows = java.util.Collections.synchronizedList(mutableListOf<GameRow>())
                val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
                try {
                    pool.invokeAll(items.mapIndexed { i, it -> java.util.concurrent.Callable {
                        val row = GameStats.row(it.first.id, it.first.name, Series_.run(connector(paths[i]), target, 12, 2500))
                        rows += row; AppState.gameResults = rows.sortedBy { r -> r.score }
                        if (row.note != "локальный прокси недоступен или таймаут" || row.avg >= 0) Health.add(it.first.link, row.loss < 50, row.avg)
                    } })
                } finally { pool.shutdown() }
            } finally {
                Native.stop(pid)
                runCatching { dir.deleteRecursively() }; runCatching { Health.save(ctx) }
                runCatching { File(ctx.filesDir, "game-config.json").delete(); File(ctx.filesDir, "game.log").delete() }
                running = false; AppState.gameBusy = false
            }
        }.start()
    }

    /**
     * Скорость загрузки по серверам (до 10: избранные и лучшие по рейтингу), по очереди, чтобы они не делили канал. На каждый сервер не больше
     * [SpeedTest.ALL_MAX_BYTES] за [SpeedTest.ALL_SECONDS] секунд. Портов не открывается: временный Xray со своим сокетом на сервер.
     */
    fun speedAll(url: String, list: List<Server> = gameCandidates()) {
        if (AppState.speedAllBusy) return
        synchronized(this) { if (running) { AppState.message = "Подождите: идёт другой замер"; return }; running = true }
        AppState.speedAllBusy = true; AppState.speedResults.clear()
        Thread {
            val ctx = AppState.context(); var pid = 0
            val dir = File(ctx.filesDir, "probe-speed").apply { deleteRecursively(); mkdirs() }
            try {
                val items = list.mapNotNull { sv -> runCatching { val p = Links.parse(sv.link); Triple(sv, p, resolve(p.host)) }.getOrNull() }
                if (items.isEmpty()) { AppState.message = "Нет серверов для проверки"; return@Thread }
                val cfg = File(ctx.filesDir, "speed-config.json").apply { writeText(ConfigBuilder.buildProbe(items.map { it.second to it.third }, dir.absolutePath)) }
                val log = File(ctx.filesDir, "speed.log").apply { writeText("") }
                pid = Native.spawn(xrayPath(ctx), cfg.absolutePath, ctx.filesDir.absolutePath, log.absolutePath, -1)
                val paths = items.indices.map { File(dir, "p$it.sock").absolutePath }
                var up = false
                repeat(40) { if (!up) { up = File(paths[0]).exists(); if (!up) Thread.sleep(150) } }
                if (!up) { AppState.message = "Не удалось запустить проверку"; return@Thread }
                items.forEachIndexed { i, it -> AppState.speedResults[it.first.id] = SpeedTest.measure(connector(paths[i]), url, SpeedTest.ALL_SECONDS, SpeedTest.ALL_MAX_BYTES) }
            } finally {
                Native.stop(pid)
                runCatching { dir.deleteRecursively() }
                runCatching { File(ctx.filesDir, "speed-config.json").delete(); File(ctx.filesDir, "speed.log").delete() }
                running = false; AppState.speedAllBusy = false
            }
        }.start()
    }

    /** «Через VPN»: замер идёт через уже работающий туннель, поэтому только для подключённого сервера. */
    fun viaVpn() {
        if (!RayVpnService.connected || probePath.isEmpty()) { AppState.message = "Этот способ работает только при подключении к серверу"; return }
        val id = AppState.selectedId ?: return
        AppState.servers.forEach { AppState.pings.remove(it.id) }
        AppState.pings[id] = -2
        Thread {
            val r = HeadProbe.head(AppState.pingUrl, connector(probePath))
            AppState.pingNotes[id] = r.note; AppState.pings[id] = r.ms
        }.start()
    }
}

class SubWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        AppState.init(applicationContext)
        AppState.subs.toList().forEach { AppState.fetchSub(it) }
        return Result.success()
    }
}

/** Фоновый мониторинг: быстрый замер всех серверов, результаты копятся в истории. */
class MonitorWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        AppState.init(applicationContext)
        if (!AppState.monitor || AppState.servers.isEmpty()) return Result.success()
        Pinger.runBatch(AppState.servers.toList(), true)
        return Result.success()
    }
}

object Scheduler {
    fun apply(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!AppState.autoUpd) { wm.cancelUniqueWork("subs"); return }
        val req = PeriodicWorkRequestBuilder<SubWorker>(AppState.updMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork("subs", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun applyMonitor(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!AppState.monitor) { wm.cancelUniqueWork("monitor"); return }
        val req = PeriodicWorkRequestBuilder<MonitorWorker>(AppState.monitorMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (AppState.monitorWifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork("monitor", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
