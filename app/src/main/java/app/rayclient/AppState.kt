package app.rayclient

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class Server(val id: String, val name: String, val link: String, val subId: String?)
/** Авто-сервер из JSON-подписки: вместо ссылки у него служебный адрес vitrum-auto://, сама конфигурация лежит в AutoStore. */
val Server.isAuto: Boolean get() = AutoSub.isAuto(link)
data class Sub(val id: String, val url: String, val name: String, val updated: Long,
               val used: Long = 0, val total: Long = 0, val expire: Long = 0, val support: String = "", val announce: String = "", val routing: String = "",
               val intervalH: Int = 0, val refill: Long = 0, val page: String = "", val fallback: String = "", val declinedUrl: String = "",
               val json: Boolean = false)   // режим JSON-подписки: кроме списка ссылок берём авто-серверы из <адрес>/json
class Profile(val id: String, val name: String, val rules: List<Rule>, val presets: Set<String>, val exceptRu: Boolean, val wl: Boolean, val ds: Int, val dflt: Int = 0)

val DNS_PRESETS = listOf("Cloudflare" to listOf("1.1.1.1", "1.0.0.1"), "Google" to listOf("8.8.8.8", "8.8.4.4"), "Quad9" to listOf("9.9.9.9", "149.112.112.112"), "AdGuard" to listOf("94.140.14.14", "94.140.15.15"))

const val WHITELIST_URL = "https://raw.githubusercontent.com/hxehex/russia-mobile-internet-whitelist/main/whitelist.txt"

object AppState {
    internal var ctx: Context? = null
    val servers = mutableStateListOf<Server>()
    val subs = mutableStateListOf<Sub>()
    val pings = mutableStateMapOf<String, Int>()
    val pingNotes = mutableStateMapOf<String, String>()
    val trustedSsids = mutableStateListOf<String>()    // доверенные Wi-Fi сети (названия): в них VPN выключается сценарием; хранятся зашифрованно
    val favs = mutableStateListOf<String>()            // ссылки избранных серверов (id меняются при обновлении подписки)
    var killSwitch by mutableStateOf(false)
    var autoOpen by mutableStateOf(false)
    var autoBoot by mutableStateOf(false)
    var reconnectNet by mutableStateOf(true)
    var sortPing by mutableStateOf(false)
    var query by mutableStateOf("")
    var onlyFav by mutableStateOf(false)
    var exceptRu by mutableStateOf(false)
    var autoSwitch by mutableStateOf(true)
    var showAdd by mutableStateOf(false)
    var quickBusy by mutableStateOf(false)
    var myIp by mutableStateOf("")
    var diag by mutableStateOf<String?>(null)
    var diagBusy by mutableStateOf(false)
    var appTraffic by mutableStateOf<AppTraffic.Result?>(null)   // «Трафик по приложениям»: последний результат (не сохраняется)
    var appTrafficBusy by mutableStateOf(false)
    var balancerStrategy by mutableIntStateOf(0)   // стратегия балансировщика авто-серверов: 0 как в панели, 1 случайный, 2 по очереди, 3 быстрый
    var countryFilter by mutableStateOf("")   // фильтр списка серверов по стране (код из флага в названии); "" — все
    val speedResults = mutableStateMapOf<String, SpeedResult>()   // «Скорость всех серверов»: результат по id сервера
    var speedAllBusy by mutableStateOf(false)
    var showShield by mutableStateOf(false)
    var pendingMove by mutableStateOf<Pair<String, String>?>(null)   // (id подписки, новый адрес): сервис сообщил о переезде, ждём решения
    var pingBusy by mutableStateOf(false)
    var subBusy by mutableIntStateOf(0)
    var onboarded by mutableStateOf(true)
    var pendingInput by mutableStateOf<String?>(null)   // ссылка из внешнего источника ждёт подтверждения пользователя
    var showGame by mutableStateOf(false)
    var gameMode by mutableStateOf(false)
    var fragment by mutableStateOf(false)
    var speedUrl by mutableStateOf(SpeedTest.DEFAULT_URL)   // файл для теста скорости (http)
    var bgHintShown by mutableStateOf(false)   // подсказка про фон после первого подключения уже показана
    var trafficLimitMobileOnly by mutableStateOf(false)   // лимит считается только по мобильной сети
    var trafficLimitGb by mutableIntStateOf(0)   // месячный лимит трафика, ГБ; 0 — не предупреждать
    var memLimit by mutableIntStateOf(0)         // предел памяти Xray: индекс в MemLimit.CHOICES
    var updAutoDefault by mutableStateOf(false)   // раз в сутки проверять релизы на GitHub (по умолчанию выключено)
    var pendingConnect by mutableStateOf(false)   // ярлык «Подключить» на рабочем столе (не сохраняется)
    var fakeDns by mutableStateOf(false)   // поддельный DNS (экспериментально)
    var dnsTcp by mutableStateOf(false)   // обычный DNS по TCP
    var dnsQuery by mutableIntStateOf(0)  // стратегия доменов DNS: 0 оба, 1 IPv4, 2 IPv6
    var xrayLog by mutableStateOf(XrayLog.DEFAULT)   // уровень журнала Xray
    var notifSpeed by mutableStateOf(false)   // скорость в уведомлении VPN
    var haptics by mutableStateOf(true)       // вибрация при нажатиях и подключении
    var rootOn by mutableStateOf(false)       // root-функции включены (по умолчанию выключено; в резервную копию не переносится)
    var rootInfo by mutableStateOf<Boolean?>(null)   // последняя проверка root: true — есть, false — нет, null — не проверялось (не сохраняется)
    var rootChecking by mutableStateOf(false)
    var blockAds by mutableStateOf(false)
    var blockV6 by mutableStateOf(false)
    var bypassLan by mutableStateOf(true)
    var sniff by mutableStateOf(true)
    var mtu by mutableIntStateOf(TunParams.DEFAULT_MTU)   // MTU туннеля, 1280..1500
    var fragLength by mutableStateOf(FragmentParams.DEFAULT_LENGTH)
    var fragInterval by mutableStateOf(FragmentParams.DEFAULT_INTERVAL)
    var fingerprint by mutableStateOf("")   // отпечаток TLS для всех серверов; пусто — как в ссылке
    var privateNotif by mutableStateOf(true)   // на экране блокировки в уведомлении без названия сервера
    var appLock by mutableStateOf(false)   // блокировка входа в приложение системным способом (отпечаток, PIN, рисунок)
    var secureScreen by mutableStateOf(false)   // FLAG_SECURE: без скриншотов и превью в недавних
    var shieldAuto by mutableStateOf(false)     // проверка сети из «Щита» через несколько секунд после подключения (выключена по умолчанию)
    var gameTarget by mutableStateOf("http://cp.cloudflare.com/generate_204")
    var gameLive by mutableStateOf<GameLive?>(null)
    var gameResults by mutableStateOf<List<GameRow>>(emptyList())
    var gameBusy by mutableStateOf(false)
    var schedOn by mutableStateOf(false)            // расписание VPN: в окне schedFrom–schedTo VPN выключен
    var schedFrom by mutableIntStateOf(23 * 60)     // минуты от полуночи
    var schedTo by mutableIntStateOf(7 * 60)
    var scenariosOn by mutableStateOf(false)
    val scen = mutableStateListOf(Scen(), Scen(), Scen(), Scen())     // Wi-Fi, мобильная сеть, роуминг, открытая Wi-Fi
    var scenarioKs by mutableIntStateOf(0)                    // 0 как в настройках, 1 включить, 2 выключить (задаёт сценарий)
    /** Kill switch с учётом сценария. */
    fun killSwitchEffective(): Boolean = when (scenarioKs) { 1 -> true; 2 -> false; else -> killSwitch }
    var showJournal by mutableStateOf(false)
    var connLog by mutableStateOf(false)
    var monitor by mutableStateOf(false)
    var monitorMin by mutableIntStateOf(60)
    var monitorWifi by mutableStateOf(true)
    var sortRating by mutableStateOf(false)
    var shieldBusy by mutableStateOf(false)
    var shieldNet by mutableStateOf<ShieldNet?>(null)
    var traceBusy by mutableStateOf(false)                      // идёт проверка «что видят другие приложения»
    var traceResult by mutableStateOf<TraceResult?>(null)       // её результат (не сохраняется)
    var bgRootBusy by mutableStateOf(false)                     // идёт root-настройка работы в фоне
    var bgRootInfo by mutableStateOf<String?>(null)             // её итог (не сохраняется)
    var truthBusy by mutableStateOf(false)                      // идёт root-сравнение с эталоном
    var truthResult by mutableStateOf<TruthResult?>(null)       // его результат (не сохраняется)
    var ownersBusy by mutableStateOf(false)                     // идёт root-проверка «чьи это порты»
    var portOwners by mutableStateOf<PortOwnersResult?>(null)   // её результат (не сохраняется)
    val apps = mutableStateListOf<String>()
    val presets = mutableStateListOf<String>()
    var selectedId by mutableStateOf<String?>(null)
    var theme by mutableIntStateOf(0)        // 0 система, 1 тёмная, 2 светлая
    var lang by mutableStateOf("auto")       // "auto" (язык телефона), "ru", "en"
    var appMode by mutableIntStateOf(0)      // 0 все через VPN, 1 только выбранные, 2 выбранные мимо VPN
    var transport by mutableIntStateOf(0)    // 0 TCP+UDP, 1 только TCP, 2 только UDP
    var pingMode by mutableIntStateOf(0)     // 0 TCP, 1 ICMP, 2 HEAD через каждый сервер, 3 HEAD через VPN
    var hyRestart by mutableStateOf(false)   // перезапускать Xray по таймеру при подключении через Hysteria2
    var hyRestartMin by mutableIntStateOf(HyRestart.DEFAULT)
    var pingThreads by mutableIntStateOf(0)  // 0 — как раньше (TCP и ICMP 16, HEAD 6), иначе число параллельных замеров
    val rules = mutableStateListOf<Rule>()
    var defaultAction by mutableIntStateOf(0) // остальной трафик: 0 через VPN, 1 напрямую, 2 блокировать (у каждого профиля свой)
    var domainStrategy by mutableIntStateOf(1) // 0 AsIs, 1 IPIfNonMatch, 2 IPOnDemand
    var useWhitelist by mutableStateOf(false)
    var ruGeo by mutableStateOf(false)        // скачанная база IP для России включена
    var ruGeoAt by mutableLongStateOf(0L)
    var ruGeoBusy by mutableStateOf(false)
    var asnBusy by mutableStateOf(false)
    var message by mutableStateOf("")
    var pingUrl by mutableStateOf("http://cp.cloudflare.com/generate_204")
    var autoUpd by mutableStateOf(true)
    var updMinutes by mutableIntStateOf(1440)
    val profiles = mutableStateListOf<Profile>()
    var activeProfile by mutableStateOf("main")   // "sub" — профиль из подписки (только чтение)
    var dnsPreset by mutableIntStateOf(0)         // 0..3 — DNS_PRESETS, 4 — свой список
    var dnsCustom by mutableStateOf("")
    var dnsDoh by mutableStateOf(false)
    var updateUrl by mutableStateOf("")
    var lastUpd by mutableLongStateOf(0L)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    private fun prefs() = ctx!!.getSharedPreferences("ray", Context.MODE_PRIVATE)

    /** Зашифрованные поля, которые не удалось расшифровать при запуске: до перезапуска их не перезаписываем. */
    private val lockedKeys = mutableSetOf<String>()

    /** Зашифрованное поле: не пишем, если его не удалось прочитать или зашифровать (открытым текстом не храним). */
    private fun putSealed(e: SharedPreferences.Editor, key: String, plain: String) {
        if (key in lockedKeys) return
        Vault.seal(plain)?.let { e.putString(key, it) } ?: run { message = "Не удалось зашифровать список серверов: изменения не сохранены" }
    }

    @Synchronized fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        val p = prefs()
        fun sealed(key: String): String = p.safeString(key, "[]")!!.let { raw -> Vault.open(raw) ?: run { lockedKeys += key; "[]" } }
        runCatching {
            JSONArray(sealed("servers")).let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
                servers += Server(it.getString("id"), it.getString("name"), it.getString("link"), it.optString("subId").ifEmpty { null }) } }
            JSONArray(sealed("subs")).let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
                subs += Sub(it.getString("id"), it.getString("url"), it.getString("name"), it.optLong("updated"),
                    it.optLong("used"), it.optLong("total"), it.optLong("expire"), it.optString("support"), it.optString("announce"), it.optString("routing"),
                    it.optInt("ih"), it.optLong("rf"), it.optString("pg"), it.optString("fb"), it.optString("dn"), it.optBoolean("js")) } }
            JSONArray(p.safeString("apps", "[]")).let { a -> for (i in 0 until a.length()) apps += a.getString(i) }
            JSONArray(p.safeString("presets", "[]")).let { a -> for (i in 0 until a.length()) presets += a.getString(i) }
            JSONArray(sealed("favs")).let { a -> for (i in 0 until a.length()) favs += a.getString(i) }
            JSONArray(sealed("ssids")).let { a -> for (i in 0 until a.length()) trustedSsids += a.getString(i) }
            JSONArray(p.safeString("rules", "[]")).let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { rules += Rule(it.getInt("k"), it.getString("v"), it.getInt("a"), it.optBoolean("e", true)) } }
        }
        runCatching {
            JSONArray(p.safeString("profiles", "[]")).let { arr -> for (i in 0 until arr.length()) arr.getJSONObject(i).let { o ->
                profiles += Profile(o.getString("id"), o.getString("name"),
                    o.getJSONArray("rules").let { r -> (0 until r.length()).map { k -> r.getJSONObject(k).let { x -> Rule(x.getInt("k"), x.getString("v"), x.getInt("a"), x.optBoolean("e", true)) } } },
                    o.getJSONArray("presets").let { r -> (0 until r.length()).map { k -> r.getString(k) }.toSet() }, o.optBoolean("exRu"), o.optBoolean("wl"), o.optInt("ds", 1), o.optInt("df", 0).coerceIn(0, 2)) } }
        }
        if (lockedKeys.isNotEmpty()) message = "Не удалось расшифровать сохранённые серверы. Данные на диске не тронуты; перезапустите приложение"
        selectedId = p.safeString("selected", null)
        onboarded = p.safeBoolean("onboarded", servers.isNotEmpty())   // у тех, кто уже пользуется приложением, знакомство не показываем
        lang = p.safeString("lang", "auto") ?: "auto"; I18n.init(c)
        theme = p.safeInt("theme", 0); appMode = p.safeInt("appMode", 0); transport = p.safeInt("transport", 0)
        pingMode = p.safeInt("pingMode", 0); pingThreads = p.safeInt("pingThreads", 0).coerceIn(0, 16); hyRestart = p.safeBoolean("hyRestart", false); hyRestartMin = HyRestart.clamp(p.safeInt("hyRestartMin", HyRestart.DEFAULT)); domainStrategy = p.safeInt("ds", 1); defaultAction = p.safeInt("dflt", 0).coerceIn(0, 2)
        killSwitch = p.safeBoolean("ks", false); exceptRu = p.safeBoolean("exRu", false); autoSwitch = p.safeBoolean("autoSw", true); autoOpen = p.safeBoolean("autoOpen", false); autoBoot = p.safeBoolean("autoBoot", false)
        reconnectNet = p.safeBoolean("recNet", true); sortPing = p.safeBoolean("sortPing", false)
        pingUrl = p.safeString("pingUrl", pingUrl)!!; autoUpd = p.safeBoolean("autoUpd", true); updMinutes = p.safeInt("updMin", 1440)
        useWhitelist = p.safeBoolean("wl", false); ruGeo = p.safeBoolean("ruGeo", false); ruGeoAt = p.safeLong("ruGeoAt", 0L)
        connLog = p.safeBoolean("connLog", false)
        fragment = p.safeBoolean("fragment", false); mtu = TunParams.orDefault(p.safeInt("mtu", TunParams.DEFAULT_MTU)); speedUrl = p.safeString("speedUrl", SpeedTest.DEFAULT_URL)!!.ifBlank { SpeedTest.DEFAULT_URL }; bgHintShown = p.safeBoolean("bgHint", false); trafficLimitMobileOnly = p.safeBoolean("trLimMob", false); trafficLimitGb = p.safeInt("trLimitGb", 0).coerceIn(0, 100000); memLimit = p.safeInt("memLimit", 0).coerceIn(0, MemLimit.CHOICES.lastIndex); updAutoDefault = p.safeBoolean("updAutoDef", false); fakeDns = p.safeBoolean("fakeDns", false); dnsTcp = p.safeBoolean("dnsTcp", false); dnsQuery = p.safeInt("dnsQ", 0).coerceIn(0, 2); xrayLog = XrayLog.orDefault(p.safeString("xlog", XrayLog.DEFAULT)!!); notifSpeed = p.safeBoolean("notifSpeed", false); haptics = p.safeBoolean("haptics", true); rootOn = p.safeBoolean("rootOn", false);blockAds = p.safeBoolean("blockAds", false); blockV6 = p.safeBoolean("blockV6", false); bypassLan = p.safeBoolean("bypassLan", true); sniff = p.safeBoolean("sniff", true); fragLength = p.safeString("fragLen", fragLength)!!; fragInterval = p.safeString("fragInt", fragInterval)!!; fingerprint = p.safeString("fp", "")!!; secureScreen = p.safeBoolean("secure", false); shieldAuto = p.safeBoolean("shieldAuto", false); appLock = p.safeBoolean("appLock", false); privateNotif = p.safeBoolean("privNotif", true); gameMode = p.safeBoolean("gameMode", false); gameTarget = p.safeString("gameTarget", gameTarget)!!
        scenariosOn = p.safeBoolean("scenOn", false); balancerStrategy = p.safeInt("balStrat", 0).coerceIn(0, 3); schedOn = p.safeBoolean("schOn", false); schedFrom = p.safeInt("schFrom", 23 * 60).coerceIn(0, 1439); schedTo = p.safeInt("schTo", 7 * 60).coerceIn(0, 1439); scenarioKs = p.safeInt("scenKs", 0)
        runCatching { JSONArray(p.safeString("scen", "[]")).let { a -> for (i in 0 until minOf(4, a.length())) a.getJSONObject(i).let { scen[i] = Scen(it.optInt("v"), it.optString("p"), it.optInt("k")) } } }
        monitor = p.safeBoolean("mon", false); monitorMin = p.safeInt("monMin", 60); monitorWifi = p.safeBoolean("monWifi", true); sortRating = p.safeBoolean("sortRating", false)
        Health.onChange = { HealthUi.version++ }; Health.load(c); TrafficLog.load(c)
        dnsPreset = p.safeInt("dnsPreset", 0); dnsCustom = p.safeString("dnsCustom", "")!!; dnsDoh = p.safeBoolean("dnsDoh", false)
        updateUrl = p.safeString("updateUrl", "")!!; lastUpd = p.safeLong("lastUpd", 0L)
        activeProfile = p.safeString("activeProfile", "main")!!
        if (profiles.isEmpty()) { profiles += Profile("main", "Основной профиль", rules.toList(), presets.toSet(), exceptRu, useWhitelist, domainStrategy, defaultAction); activeProfile = "main" }
        if (activeProfile == "sub") { val sp = subProfile(); if (sp != null) loadProfile(sp) else activeProfile = profiles.first().id.also { id -> loadProfile(profiles.first { it.id == id }) } }
        else if (profiles.none { it.id == activeProfile }) activeProfile = profiles.first().id
        // остатки прошлых запусков: временные конфигурации содержат ключи серверов
        listOf("ping-config.json", "game-config.json", "diag-config.json", "speed-config.json", "config.json", "ping.log", "game.log", "diag.log", "speed.log", "access.log").forEach { runCatching { File(c.filesDir, it).delete() } }
        File(c.filesDir, "geoip.dat").let { if (!it.exists()) c.assets.open("geoip.dat").use { i -> it.outputStream().use { o -> i.copyTo(o) } } }
        File(c.filesDir, "geosite.dat").let { if (!it.exists()) c.assets.open("geosite.dat").use { i -> it.outputStream().use { o -> i.copyTo(o) } } }
        File(c.filesDir, "whitelist.txt").let { if (!it.exists()) c.assets.open("whitelist.txt").use { i -> it.outputStream().use { o -> i.copyTo(o) } } }
        if (prefs().safeString("servers", "")?.startsWith("v1:") != true) save()   // миграция: ссылки больше не лежат открытым текстом
    }

    /** После восстановления копии хранилище уже заменено, а приложение ждёт перезапуска: запись из памяти затёрла бы восстановленное. */
    @Volatile var frozen = false

    fun save() {
        if (frozen) return
        val sv = JSONArray(); servers.toList().forEach { sv.put(JSONObject().put("id", it.id).put("name", it.name).put("link", it.link).put("subId", it.subId ?: "")) }
        val sb = JSONArray(); subs.toList().forEach { sb.put(JSONObject().put("id", it.id).put("url", it.url).put("name", it.name).put("updated", it.updated)
            .put("used", it.used).put("total", it.total).put("expire", it.expire).put("support", it.support).put("announce", it.announce).put("routing", it.routing)
                .put("ih", it.intervalH).put("rf", it.refill).put("pg", it.page).put("fb", it.fallback).put("dn", it.declinedUrl).put("js", it.json)) }
        if (activeProfile != "sub") profiles.indexOfFirst { it.id == activeProfile }.let { if (it >= 0) profiles[it] = snapshotOf(profiles[it]) }
        val pf = JSONArray(); profiles.toList().forEach { pr -> pf.put(JSONObject().put("id", pr.id).put("name", pr.name).put("exRu", pr.exceptRu).put("wl", pr.wl).put("ds", pr.ds).put("df", pr.dflt)
            .put("rules", JSONArray().also { ar -> pr.rules.forEach { x -> ar.put(JSONObject().put("k", x.kind).put("v", x.value).put("a", x.action).put("e", x.enabled)) } })
            .put("presets", JSONArray(pr.presets.toList()))) }
        prefs().edit()
            .also { e -> putSealed(e, "servers", sv.toString()); putSealed(e, "subs", sb.toString()); putSealed(e, "favs", JSONArray(favs.toList()).toString()); putSealed(e, "ssids", JSONArray(trustedSsids.toList()).toString()) }
            .putString("profiles", pf.toString())
            .putString("activeProfile", activeProfile)
            .putBoolean("onboarded", onboarded)
            .putBoolean("scenOn", scenariosOn)
            .putInt("balStrat", balancerStrategy)
            .putBoolean("schOn", schedOn).putInt("schFrom", schedFrom).putInt("schTo", schedTo)
            .putInt("scenKs", scenarioKs)
            .putString("scen", JSONArray().also { a -> scen.forEach { s -> a.put(JSONObject().put("v", s.vpn).put("p", s.profile).put("k", s.ks)) } }.toString())
            .putBoolean("gameMode", gameMode)
            .putBoolean("fragment", fragment).putInt("mtu", mtu).putString("speedUrl", speedUrl).putBoolean("bgHint", bgHintShown).putInt("trLimitGb", trafficLimitGb).putBoolean("trLimMob", trafficLimitMobileOnly).putInt("memLimit", memLimit).putBoolean("updAutoDef", updAutoDefault).putBoolean("fakeDns", fakeDns).putBoolean("dnsTcp", dnsTcp).putInt("dnsQ", dnsQuery).putString("xlog", xrayLog).putBoolean("notifSpeed", notifSpeed).putBoolean("haptics", haptics).putBoolean("rootOn", rootOn).putBoolean("blockAds", blockAds).putBoolean("blockV6", blockV6).putBoolean("bypassLan", bypassLan).putBoolean("sniff", sniff).putString("fragLen", fragLength).putString("fragInt", fragInterval).putString("fp", fingerprint)
            .putBoolean("secure", secureScreen).putBoolean("appLock", appLock).putBoolean("privNotif", privateNotif)
            .putBoolean("shieldAuto", shieldAuto)
            .putString("gameTarget", gameTarget)
            .putBoolean("connLog", connLog)
            .putBoolean("mon", monitor)
            .putInt("monMin", monitorMin)
            .putBoolean("monWifi", monitorWifi)
            .putBoolean("sortRating", sortRating)
            .putInt("dnsPreset", dnsPreset)
            .putString("dnsCustom", dnsCustom)
            .putBoolean("dnsDoh", dnsDoh)
            .putString("updateUrl", updateUrl)
            .putLong("lastUpd", lastUpd)
            .putString("apps", JSONArray(apps.toList()).toString())
            .putString("presets", JSONArray(presets.toList()).toString())
            .putString("selected", selectedId)
            .putString("lang", lang)
            .putInt("theme", theme)
            .putInt("appMode", appMode)
            .putInt("transport", transport)
            .putInt("pingMode", pingMode)
            .putInt("pingThreads", pingThreads)
            .putBoolean("hyRestart", hyRestart)
            .putInt("hyRestartMin", hyRestartMin)
            .putInt("ds", domainStrategy)
            .putInt("dflt", defaultAction)
            .putBoolean("wl", useWhitelist)
            .putBoolean("ruGeo", ruGeo)
            .putLong("ruGeoAt", ruGeoAt)
            .putBoolean("ks", killSwitch)
            .putBoolean("exRu", exceptRu)
            .putBoolean("autoSw", autoSwitch)
            .putBoolean("autoOpen", autoOpen)
            .putBoolean("autoBoot", autoBoot)
            .putBoolean("recNet", reconnectNet)
            .putBoolean("sortPing", sortPing)
            .putString("pingUrl", pingUrl)
            .putBoolean("autoUpd", autoUpd)
            .putInt("updMin", updMinutes)
            .putString("rules", JSONArray().also { arr -> rules.forEach { arr.put(JSONObject().put("k", it.kind).put("v", it.value).put("a", it.action).put("e", it.enabled)) } }.toString())
            .apply()
    }

    fun routingOptions(): RoutingOptions {
        val wl = if (useWhitelist) runCatching { File(ctx!!.filesDir, "whitelist.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } }.getOrDefault(emptyList()) else emptyList()
        // правила: выключенные не идут в конфигурацию, метки geoip/geosite, которых нет в базе, пропускаются (иначе Xray не запустится)
        val known = GeoTags.load(GeoData.assetDir(ctx!!))
        val dropped = mutableListOf<String>()
        val active = rules.filter { it.enabled }.mapNotNull { r -> GeoTags.sanitize(r, known).also { dropped += it.dropped }.rule }
        if (dropped.isNotEmpty()) message = "Правила: пропущены метки, которых нет в базах (${dropped.distinct().joinToString(", ")})"
        return RoutingOptions(active, listOf("AsIs", "IPIfNonMatch", "IPOnDemand")[domainStrategy], presets.toSet(), wl, transport, exceptRu, dnsIps(), dnsDoh,
            presetIps = AsnPrefixes.prefixes(ctx!!, presets), whitelistOn = useWhitelist, geoRu = ruGeo && GeoData.ready(ctx!!), fragment = fragment, fakeDns = fakeDns, dnsTcp = dnsTcp, dnsQuery = dnsQuery, logLevel = xrayLog, blockAds = blockAds, blockV6 = blockV6, bypassLan = bypassLan, sniff = sniff, defaultAction = defaultAction, balancerStrategy = balancerStrategy, mtu = mtu, fragLength = fragLength, fragInterval = fragInterval, fingerprint = fingerprint, game = gameMode)
    }

    fun context(): Context = ctx!!

    // ---------- пинг ----------
    /** Сколько замеров идёт одновременно: [auto] при «Авто», иначе выбранное число (1–16). */
    fun pingWorkers(auto: Int): Int = if (pingThreads in 1..16) pingThreads else auto

    fun pingAll() {
        when (pingMode) {
            2 -> Pinger.viaServers()
            3 -> Pinger.viaVpn()
            // не больше 16 потоков на любой размер подписки (раньше поток на каждый сервер)
            else -> java.util.concurrent.Executors.newFixedThreadPool(pingWorkers(16)).also { pool ->
                val rootReady by lazy { rootOn && RootShell.available() }
                servers.toList().filter { !it.isAuto }.forEach { sv ->
                    pool.execute {
                        pings[sv.id] = runCatching {
                            val p = Links.parse(sv.link)
                            val addr = InetAddress.getByName(p.host)   // DNS до начала замера: в пинг входит только сама связь
                            if (pingMode == 0) {
                                val t0 = System.nanoTime()
                                Socket().use { it.connect(InetSocketAddress(addr, p.port), 3000) }
                                ((System.nanoTime() - t0) / 1_000_000).toInt()
                            } else IcmpPing.ping(addr) { rootReady }.also { if (it < 0) error("нет ответа") }
                        }.getOrDefault(-1)
                    }
                }
            }.shutdown()
        }
    }
}

// Значение другого типа (повреждённая или чужая копия) не должно ронять запуск: берётся значение по умолчанию.
private fun android.content.SharedPreferences.safeInt(k: String, d: Int) = try { getInt(k, d) } catch (_: ClassCastException) { d }
private fun android.content.SharedPreferences.safeBoolean(k: String, d: Boolean) = try { getBoolean(k, d) } catch (_: ClassCastException) { d }
private fun android.content.SharedPreferences.safeLong(k: String, d: Long) = try { getLong(k, d) } catch (_: ClassCastException) { d }
private fun android.content.SharedPreferences.safeString(k: String, d: String?) = try { getString(k, d) } catch (_: ClassCastException) { d }
