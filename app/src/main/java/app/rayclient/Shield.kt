package app.rayclient

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings

/** OK — в порядке, WARN — стоит исправить, BAD — критично, UNKNOWN — не проверено, INFO — к сведению (в оценку не входит). */
enum class St { OK, WARN, BAD, UNKNOWN, INFO }
enum class Act { NONE, KILL, DOH, RECONNECT, VPN_SETTINGS }

/** weight — сколько очков за пункт; key — устойчивый идентификатор для логики и тестов (title переводится и меняется). */
class Check(val title: String, val st: St, val detail: String, val weight: Int = 0, val act: Act = Act.NONE, val actLabel: String = "", val key: String = "") {
    val points: Int get() = if (st == St.OK) weight else 0
}

/** Итог проверки сети. serverId и session нужны, чтобы не принять устаревший результат за актуальный. realIp == null: узнать не удалось. */
class ShieldNet(val ports: List<Int>, val realIp: String?, val vpnIp: String?, val vpnNote: String,
                val at: Long = 0L, val serverId: String? = null, val session: Long = 0L,
                val skewMs: Long? = null)   // расхождение часов телефона с сервером по заголовку Date (null — не удалось)

class ShieldScore(val value: Int, val scored: Int, val verified: Int, val preliminary: Boolean, val capReason: String?)

/** Всё, от чего зависит оценка. Собирается из настроек и системы, а сама оценка считается без Android (проверяется тестами). */
class ShieldInput(
    val killSwitch: Boolean, val reconnectNet: Boolean, val doh: Boolean,
    val httpSubs: List<String>, val hasSubs: Boolean, val selectedLink: String?,
    val debuggable: Boolean, val pkg: String,
    val hiddenAlwaysOnApp: String?, val hiddenLockdown: Int,     // запасной способ: скрытая настройка системы
    val alwaysOn: Boolean?, val lockdown: Boolean?,             // надёжный способ: спрашиваем у собственной службы VpnService
    val net: ShieldNet?, val connected: Boolean, val selectedId: String?, val session: Long,
    val directSources: Int, val now: Long,
    val signer: String? = null,                                  // SHA-256 сертификата приложения (null — не удалось получить)
    val batteryOk: Boolean? = null,                             // приложение исключено из экономии заряда (null — не удалось узнать)
    val autoTime: Boolean? = null,                               // «Дата и время сети» в системе (null — не удалось узнать)
    val vpnIfaces: List<String>? = null,                         // чужие VPN-интерфейсы (tun, ppp, wg…) вне нашего подключения (null — не смотрели)
    val vpnApps: List<String>? = null,                           // названия программ с VpnService, кроме нашей (null — не удалось узнать)
    val installerKnown: Boolean = false, val installer: String? = null,   // кто установил приложение (installer == null при known: установлено вручную из файла)
)

/**
 * «Щит приватности»: оценка защиты по настройкам и по проверке сети.
 * Оценка = доля набранных очков, но с потолками: критичная проблема не выше 3, выключенный kill switch не выше 6.
 * Непроверенные пункты очков не дают (оценка «предварительная»), а не исчезают из подсчёта.
 */
object Shield {
    fun evaluate(ctx: Context): List<Check> = evaluate(inputFrom(ctx))

    fun inputFrom(ctx: Context): ShieldInput {
        val ao = runCatching { Settings.Secure.getString(ctx.contentResolver, "always_on_vpn_app") }.getOrNull()
        val lock = runCatching { Settings.Secure.getInt(ctx.contentResolver, "always_on_vpn_lockdown", 0) }.getOrDefault(0)
        val direct = (if (AppState.appMode != 0) 1 else 0) + (if (AppState.exceptRu) 1 else 0) + (if (AppState.useWhitelist) 1 else 0) + AppState.presets.size + AppState.rules.count { it.action == 1 }
        return ShieldInput(AppState.killSwitchEffective(), AppState.reconnectNet, AppState.dnsDoh,
            AppState.subs.filter { it.url.startsWith("http://") }.map { it.name }, AppState.subs.isNotEmpty(),
            AppState.servers.firstOrNull { it.id == AppState.selectedId }?.link,
            (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0, ctx.packageName, ao, lock,
            RayVpnService.alwaysOn, RayVpnService.lockdown, AppState.shieldNet, RayVpnService.connected, AppState.selectedId, RayVpnService.since,
            direct, System.currentTimeMillis(), Integrity.signer(ctx),
            batteryOk = Background.ignoringBatteryOptimizations(ctx),
            autoTime = runCatching { Settings.Global.getInt(ctx.contentResolver, "auto_time") == 1 }.getOrNull(),
            vpnIfaces = if (RayVpnService.connected) null else vpnIfaces(), vpnApps = vpnApps(ctx),
            installerKnown = installer(ctx).first, installer = installer(ctx).second)
    }

    private val vpnIfaceRe = Regex("^(tun|tap|ppp|wg|ipsec|utun)\\d*.*")

    /** Имена VPN-интерфейсов в системе. Метод косвенный: на новых версиях Android список может быть неполным, поэтому «не найдено» ничего не доказывает. */
    private fun vpnIfaces(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && vpnIfaceRe.matches(it.name) }.map { it.name }
    }.getOrDefault(emptyList())

    /** Программы с VpnService (кроме нашей): нужен <queries> с действием android.net.VpnService в манифесте. */
    private fun vpnApps(ctx: Context): List<String>? = runCatching {
        val pm = ctx.packageManager
        pm.queryIntentServices(android.content.Intent("android.net.VpnService"), 0)
            .map { it.serviceInfo }.filter { it.packageName != ctx.packageName }
            .map { pm.getApplicationLabel(it.applicationInfo).toString() }.distinct().sorted()
    }.getOrNull()

    /** (удалось ли узнать, пакет установщика). Без новых разрешений: PackageManager отвечает про собственное приложение. */
    @Suppress("DEPRECATION")
    private fun installer(ctx: Context): Pair<Boolean, String?> = runCatching {
        val pm = ctx.packageManager
        true to (if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(ctx.packageName).installingPackageName else pm.getInstallerPackageName(ctx.packageName))
    }.getOrDefault(false to null)

    /** Целые фразы, а не подстановка: перевод ищет строку целиком. */
    private fun installerText(pkg: String) = when (pkg) {
        "com.android.vending" -> "Установлено через Google Play."
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.samsung.android.packageinstaller" -> "Установлено системным установщиком (из APK-файла)."
        "ru.vk.store" -> "Установлено через RuStore."
        else -> "Установлено через пакет $pkg."
    }

    private fun ago(ms: Long): String { val s = ms / 1000; return when { s < 60 -> "Проверено только что."; s < 3600 -> "Проверено ${s / 60} мин назад."; else -> "Проверено ${s / 3600} ч назад." } }

    fun evaluate(i: ShieldInput): List<Check> {
        val l = mutableListOf<Check>()
        l += if (i.killSwitch) Check("Kill switch", St.OK, "Если Xray упадёт, трафик блокируется, а не утекает мимо VPN.", 2, key = "kill")
        else Check("Kill switch", St.WARN, "Выключен: при сбое Xray туннель закроется, и трафик пойдёт напрямую.", 2, Act.KILL, "Включить", "kill")

        val ao = i.alwaysOn; val lk = i.lockdown
        l += when {
            ao == true && lk == true -> Check("Постоянный VPN в системе", St.OK, "${BRAND} назначен постоянным VPN, соединения без VPN блокируются системой.", 2, key = "alwayson")
            ao == true -> Check("Постоянный VPN в системе", St.WARN, "Включён, но не включено «Блокировать соединения без VPN». Включите его в системных настройках.", 2, Act.VPN_SETTINGS, "Открыть настройки VPN", "alwayson")
            ao == false -> Check("Постоянный VPN в системе", St.WARN, "Не включён (по данным последнего подключения): если приложение закроют или оно упадёт, система не удержит защиту. Выберите ${BRAND} в системных настройках VPN и включите блокировку соединений без VPN.", 2, Act.VPN_SETTINGS, "Открыть настройки VPN", "alwayson")
            i.hiddenAlwaysOnApp == i.pkg && i.hiddenLockdown == 1 -> Check("Постоянный VPN в системе", St.OK, "${BRAND} назначен постоянным VPN, соединения без VPN блокируются системой.", 2, key = "alwayson")
            i.hiddenAlwaysOnApp == i.pkg -> Check("Постоянный VPN в системе", St.WARN, "Включён, но не включено «Блокировать соединения без VPN». Включите его в системных настройках.", 2, Act.VPN_SETTINGS, "Открыть настройки VPN", "alwayson")
            else -> Check("Постоянный VPN в системе", St.UNKNOWN, "Пока не удалось определить, включён ли он: подключитесь к серверу, и приложение спросит об этом у системы. Защита работает даже при закрытом приложении, если выбрать ${BRAND} в системных настройках и включить блокировку.", 0, Act.VPN_SETTINGS, "Открыть настройки VPN", "alwayson")
        }

        l += Check("IPv6", St.INFO, "По настройке: IPv6-трафик заходит в туннель. Если у сервера нет IPv6, соединения не пойдут, но и не утекут. На практике это не проверялось, поэтому очков пункт не даёт.", 0, key = "ipv6")
        l += if (i.reconnectNet) Check("Переподключение при смене сети", St.OK, "Xray перезапускается при переходе Wi-Fi ↔ мобильная сеть.", 1, key = "reconnect")
        else Check("Переподключение при смене сети", St.WARN, "Выключено: после смены сети соединения могут зависать.", 1, Act.RECONNECT, "Включить", "reconnect")
        l += if (i.doh) Check("Шифрованный DNS", St.INFO, "DoH включён: запросы приложений разбирает Xray по HTTPS через сервер.", 0, key = "doh")
        else Check("Шифрованный DNS", St.INFO, "Запросы идут внутри туннеля обычным DNS. DoH шифрует их отдельно (функция новая: после включения проверьте интернет).", 0, Act.DOH, "Включить", "doh")

        if (i.httpSubs.isNotEmpty()) l += Check("Подписка без HTTPS", St.WARN, "Ссылку «${i.httpSubs.joinToString()}» по дороге могут подменить. Лучше https://.", 1, key = "subs")
        else if (i.hasSubs) l += Check("Подписки по HTTPS", St.OK, "Ссылки подписок защищены шифрованием.", 1, key = "subs")

        i.selectedLink?.let { link ->
            runCatching {
                val p = Links.parse(link); val sec = p.outbound.optJSONObject("streamSettings")?.optString("security") ?: "none"
                l += when {
                    p.proto == "hysteria2" -> Check("Транспорт выбранного сервера", St.OK, "Hysteria2 шифрует трафик (QUIC/TLS).", 1, key = "transport")
                    p.proto == "ss" -> Check("Транспорт выбранного сервера", St.INFO, "Shadowsocks шифрует трафик сам, без TLS.", 0, key = "transport")
                    sec == "tls" || sec == "reality" -> Check("Транспорт выбранного сервера", St.OK, "${p.proto.uppercase()} через ${if (sec == "reality") "Reality" else "TLS"}.", 1, key = "transport")
                    else -> Check("Транспорт выбранного сервера", St.WARN, "${p.proto.uppercase()} без TLS/Reality: провайдер увидит, что это за трафик.", 1, key = "transport")
                }
            }
        }

        l += if (i.debuggable) Check("Сборка приложения", St.WARN, "Это отладочная сборка (debuggable): на устройстве с отладкой данные приложения достать проще. Для раздачи нужна релизная сборка с постоянной подписью.", 1, key = "build")
        else Check("Сборка приложения", St.OK, "Релизная сборка.", 1, key = "build")

        i.signer?.let { sig ->
            l += if (Integrity.matches(sig)) Check("Подпись приложения", St.OK, "Подпись совпадает с ключом выпуска Vitrum.", 0, key = "signature")
            else Check("Подпись приложения", St.WARN, "Подпись не совпадает с ключом выпуска Vitrum: это отладочная сборка или чужая копия. Ставьте приложение только из источника, которому доверяете.", 0, key = "signature")
        }

        val skew = i.net?.skewMs
        if (skew != null) {
            val sec = Math.abs(skew) / 1000
            l += if (sec <= 300) Check("Время на телефоне", St.OK, "Часы телефона и сервера расходятся на $sec с: TLS и Reality не собьются.", 0, key = "clock")
            else if (skew > 0) Check("Время на телефоне", St.WARN, "Часы телефона спешат примерно на ${sec / 60} мин относительно сервера: соединения по TLS и Reality могут не устанавливаться. Включите автоматические дату и время в системных настройках.", 0, key = "clock")
            else Check("Время на телефоне", St.WARN, "Часы телефона отстают примерно на ${sec / 60} мин относительно сервера: соединения по TLS и Reality могут не устанавливаться. Включите автоматические дату и время в системных настройках.", 0, key = "clock")
        } else i.autoTime?.let { on ->
            l += if (on) Check("Время на телефоне", St.OK, "Дата и время берутся из сети: TLS и Reality не собьются из-за неверных часов.", 0, key = "clock")
            else Check("Время на телефоне", St.WARN, "Автоматическая дата и время выключены: при неверных часах соединения по TLS и Reality могут не устанавливаться. Включите их в системных настройках.", 0, key = "clock")
        }
        i.batteryOk?.let { ok ->
            l += if (ok) Check("Работа в фоне", St.OK, "Экономия заряда для приложения отключена: система реже закрывает VPN в фоне.", 0, key = "battery")
            else Check("Работа в фоне", St.WARN, "Для приложения включена экономия заряда: на части телефонов система может закрыть VPN в фоне.", 0, key = "battery")
        }
        i.vpnIfaces?.let { f ->
            l += if (f.isNotEmpty()) Check("Другой VPN на телефоне", St.WARN, "Похоже, активен чужой VPN (интерфейс ${f.joinToString()}). Тогда «реальный IP» в проверке может быть выходом того VPN, а не вашим. Отключите его и проверьте снова.", 0, key = "othervpn")
            else Check("Другой VPN на телефоне", St.INFO, "Чужих VPN-интерфейсов не видно. Метод косвенный: на новых версиях Android список интерфейсов может быть неполным.", 0, key = "othervpn")
        }
        i.vpnApps?.takeIf { it.isNotEmpty() }?.let { a ->
            l += Check("VPN и прокси-программы", St.INFO, "На телефоне есть программы с VPN-службой: ${a.joinToString()}. Это не значит, что они включены: известно только, что они установлены.", 0, key = "vpnapps")
        }
        if (i.installerKnown) l += Check("Источник установки", St.INFO,
            if (i.installer == null) "Установлено вручную, магазин не указан. Убедитесь, что файл скачан из вашего источника."
            else installerText(i.installer), 0, key = "installer")

        val net = i.net
        val age = if (net != null && net.at > 0) "\n" + ago(i.now - net.at) else ""
        l += when {
            net == null -> Check("Открытые порты на телефоне", St.UNKNOWN, "Не проверено. Нажмите «Запустить проверку сети».", 2, key = "ports")
            net.ports.isEmpty() -> Check("Открытые порты на телефоне", St.OK, "На 127.0.0.1 нет открытых портов (быстрый просмотр типичных). ${BRAND} служебный вход держит в закрытом сокете.$age", 2, key = "ports")
            else -> Check("Открытые порты на телефоне", St.BAD, "Найдены открытые порты: ${net.ports.joinToString(", ")}. Они принадлежат каким-то программам (не ${BRAND}): детекторы видят их так же.$age", 2, key = "ports")
        }

        val fresh = net != null && net.serverId == i.selectedId && net.session == i.session
        val note = "Проверяет, что выход VPN отличается от вашего реального IP. Утечки трафика других приложений отсюда не проверить: приложение исключено из туннеля."
        l += when {
            net == null -> Check("IP через VPN", St.UNKNOWN, "Не проверено. Нажмите «Запустить проверку сети».\n$note", 2, key = "ip")
            net.realIp == null -> Check("IP через VPN", St.UNKNOWN, "Не удалось узнать реальный IP (нет сети или служба не отвечает). Проверьте снова.$age", 2, key = "ip")
            !i.connected -> Check("IP через VPN", St.UNKNOWN, "Реальный IP ${net.realIp}. Подключитесь к серверу и проверьте снова, чтобы сравнить IP.$age", 2, key = "ip")
            !fresh -> Check("IP через VPN", St.UNKNOWN, "Результат устарел: сервер или подключение изменились. Проверьте снова.$age", 2, key = "ip")
            net.vpnIp == null -> Check("IP через VPN", St.WARN, "Не удалось получить IP через VPN (${net.vpnNote}). Реальный IP: ${net.realIp}.$age", 2, key = "ip")
            net.vpnIp == net.realIp -> Check("IP через VPN", St.BAD, "IP через VPN совпадает с реальным (${net.realIp}): трафик, возможно, идёт мимо VPN.$age", 2, key = "ip")
            else -> Check("IP через VPN", St.OK, "Реальный IP ${net.realIp}, через VPN ${net.vpnIp}.\n$note$age", 2, key = "ip")
        }

        if (i.directSources > 0) l += Check("Трафик мимо VPN", St.INFO, "Часть трафика идёт напрямую (источников правил: ${i.directSources}): режимы по приложениям, «Все, кроме РФ», белый список, игры, свои правила. Сайты из них видят ваш реальный IP.", 0, key = "direct")
        return l
    }

    /** Оценка 0..10 с потолками и признаком «предварительная» (есть непроверенные пункты с очками). */
    fun score(list: List<Check>): ShieldScore {
        val scored = list.filter { it.weight > 0 }
        val total = scored.sumOf { it.weight }
        if (total == 0) return ShieldScore(0, 0, 0, true, null)
        var v = Math.round(10.0 * scored.sumOf { it.points } / total).toInt()
        var reason: String? = null
        list.firstOrNull { it.st == St.BAD }?.let { if (v > 3) { v = 3; reason = "Критичная проблема: «${it.title}»" } }
        if (list.any { it.key == "kill" && it.st != St.OK } && v > 6) { v = 6; if (reason == null) reason = "Kill switch выключен: при сбое трафик пойдёт мимо VPN" }
        return ShieldScore(v, scored.size, scored.count { it.st != St.UNKNOWN }, scored.any { it.st == St.UNKNOWN }, reason)
    }

    /** Что кнопка «Усилить защиту» может включить сама, без системных настроек: Kill switch, переподключение при смене сети, DoH. Только то, что сейчас не в порядке. */
    fun fixable(checks: List<Check>): List<Act> =
        checks.filter { it.st != St.OK && it.act in setOf(Act.KILL, Act.RECONNECT, Act.DOH) }.map { it.act }.distinct()

    /** Есть ли проблема, о которой стоит сообщить после проверки: критичный пункт или потолок оценки (утечка IP, чужой открытый порт). */
    fun hasProblem(checks: List<Check>, score: ShieldScore): Boolean = score.capReason != null || checks.any { it.st == St.BAD }

    fun runNet() {
        if (AppState.shieldBusy) return
        AppState.shieldBusy = true
        val serverId = AppState.selectedId; val session = RayVpnService.since
        val viaVpn = RayVpnService.connected && Pinger.probePath.isNotEmpty()
        Thread {
            try {
                val ports = LeakCheck.scan(false)
                val (real, skew) = LeakCheck.directProbe()
                var vpn: String? = null; var note = ""
                if (viaVpn) { val r = LeakCheck.vpnIp(Pinger.connector(Pinger.probePath)); vpn = r.first; note = r.second }
                AppState.shieldNet = ShieldNet(ports, real, vpn, note, System.currentTimeMillis(), serverId, session, skew)
            } finally { AppState.shieldBusy = false }
        }.start()
    }
}
