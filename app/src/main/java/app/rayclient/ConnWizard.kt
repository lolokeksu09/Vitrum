package app.rayclient

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class WState(val mark: String) { OK("✓"), WARN("!"), FAIL("✗") }

/** Один шаг мастера: название, итог и что делать, если шаг не пройден. */
class WStep(val title: String, val state: WState, val detail: String, val hint: String = "")

/**
 * Мастер «Почему не подключается»: проверяет по порядку то, что чаще всего мешает подключению (сеть и вход в Wi-Fi, разрешение VPN, часы,
 * выбранный сервер, имя и порт сервера), и называет первую найденную причину. Все проверки идут мимо VPN (приложение вне туннеля), ничего
 * не отправляется никому, кроме самого выбранного сервера (DNS и TCP-соединение с ним). Логика шагов вынесена в чистые функции и покрыта тестами.
 */
object ConnWizard {
    fun networkStep(hasNet: Boolean, validated: Boolean, captive: Boolean): WStep = when {
        !hasNet -> WStep("Сеть", WState.FAIL, "нет подключения к интернету", "Включите Wi-Fi или мобильные данные и повторите.")
        captive -> WStep("Сеть", WState.FAIL, "Wi-Fi просит войти (страница входа)", "Откройте браузер, войдите в Wi-Fi и только потом подключайте VPN.")
        !validated -> WStep("Сеть", WState.WARN, "система не подтвердила выход в интернет", "Проверьте, открываются ли сайты без VPN: возможно, у сети нет интернета.")
        else -> WStep("Сеть", WState.OK, "интернет есть")
    }

    fun permissionStep(granted: Boolean): WStep =
        if (granted) WStep("Разрешение VPN", WState.OK, "выдано")
        else WStep("Разрешение VPN", WState.WARN, "ещё не выдано", "Нажмите «Подключиться» и разрешите VPN в окне системы.")

    fun blockedStep(blocked: Boolean): WStep? =
        if (blocked) WStep("Kill switch", WState.FAIL, "держит интернет закрытым после сбоя", "Нажмите «Отключиться», затем подключитесь заново.") else null

    /** autoTime: система сама ставит время (null — узнать не удалось). nowMs: время телефона, сверяется с датой выхода этой версии. */
    fun clockStep(autoTime: Boolean?, nowMs: Long, minPlausibleMs: Long = MIN_PLAUSIBLE): WStep = when {
        nowMs < minPlausibleMs -> WStep("Часы", WState.FAIL, "дата на телефоне слишком старая", "Включите автоматическую дату и время: при сбитых часах шифрованные соединения не устанавливаются.")
        autoTime == false -> WStep("Часы", WState.WARN, "автоматическая дата и время выключены", "Если часы идут неточно, шифрованные соединения могут не устанавливаться: включите автоопределение времени.")
        else -> WStep("Часы", WState.OK, "время телефона выглядит правдоподобно")
    }

    fun backgroundStep(ignoring: Boolean): WStep =
        if (ignoring) WStep("Экономия заряда", WState.OK, "для приложения отключена")
        else WStep("Экономия заряда", WState.WARN, "включена: система может закрывать VPN в фоне", "Откройте «Настройки → Подключение → Работа в фоне» и отключите экономию для Vitrum.")

    fun serverStep(hasServer: Boolean): WStep =
        if (hasServer) WStep("Сервер", WState.OK, "выбран") else WStep("Сервер", WState.FAIL, "не выбран", "Добавьте подписку или ссылку и выберите сервер в списке.")

    fun dnsStep(resolved: Boolean, ms: Long): WStep =
        if (resolved) WStep("Адрес сервера", WState.OK, "найден за $ms мс")
        else WStep("Адрес сервера", WState.FAIL, "не найден", "Имя сервера не разрешилось: проверьте интернет, DNS или попробуйте другую сеть. Если подписка устарела, обновите её.")

    fun tcpStep(opened: Boolean, ms: Long, udpOnly: Boolean): WStep = when {
        udpOnly -> WStep("Порт сервера", WState.WARN, "Hysteria2 работает по UDP, TCP не проверялся", "Если не подключается, сеть может блокировать UDP: попробуйте другой сервер или сеть.")
        opened -> WStep("Порт сервера", WState.OK, "открыт, $ms мс")
        else -> WStep("Порт сервера", WState.FAIL, "не отвечает", "Порт сервера закрыт или блокируется сетью: попробуйте другой сервер или другую сеть (мобильная вместо Wi-Fi и наоборот).")
    }

    /** Итог: первая найденная причина, иначе предупреждения, иначе «до сервера всё в порядке». */
    fun verdict(steps: List<WStep>): String {
        steps.firstOrNull { it.state == WState.FAIL }?.let { return "Причина: ${it.title.lowercase()}: ${it.detail}. ${it.hint}".trim() }
        val warns = steps.filter { it.state == WState.WARN }
        if (warns.isNotEmpty()) return "Явной причины не найдено, но стоит проверить: " + warns.joinToString("; ") { "${it.title.lowercase()} (${it.detail})" } + ". " + warns.first().hint
        return "Сеть, разрешение, часы, адрес и порт сервера в порядке. Если VPN всё равно не подключается, причина дальше (ключи, защита Reality, блокировка по протоколу): смотрите подробную проверку ниже."
    }

    /** Назвал ли мастер причину (тогда долгую подробную проверку не запускаем). */
    fun hasCause(text: String) = text.contains("Причина:")

    fun format(steps: List<WStep>): String =
        steps.joinToString("\n") { "${it.state.mark} ${it.title}: ${it.detail}" } + "\n\n" + verdict(steps)

    /** Дата выхода этой ветки приложения (2026-01-01 UTC): раньше ни одна честная дата быть не может. */
    const val MIN_PLAUSIBLE = 1_767_225_600_000L

    @Suppress("DEPRECATION")
    fun run(ctx: Context, sv: Server?): String {
        val steps = mutableListOf<WStep>()
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.filter { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } }.getOrDefault(emptyList())
        steps += networkStep(caps.isNotEmpty(), caps.any { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) },
            caps.isNotEmpty() && caps.none { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } && caps.any { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) })
        blockedStep(RayVpnService.blocked)?.let { steps += it }
        steps += permissionStep(VpnService.prepare(ctx) == null)
        steps += clockStep(runCatching { android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.AUTO_TIME) == 1 }.getOrNull(), System.currentTimeMillis())
        steps += backgroundStep(Background.ignoringBatteryOptimizations(ctx))
        steps += serverStep(sv != null)
        if (sv != null && !sv.isAuto) {
            val p = runCatching { Links.parse(sv.link) }.getOrNull()
            if (p == null) steps += WStep("Ссылка сервера", WState.FAIL, "не разобрана", "Удалите сервер и добавьте заново или обновите подписку.")
            else {
                val t0 = System.nanoTime()
                val addr = runCatching { InetAddress.getByName(p.host) }.getOrNull()
                steps += dnsStep(addr != null, (System.nanoTime() - t0) / 1_000_000)
                if (addr != null) {
                    if (p.proto == "hysteria2") steps += tcpStep(false, 0, udpOnly = true)
                    else {
                        val t1 = System.nanoTime()
                        val ok = runCatching { Socket().use { it.connect(InetSocketAddress(addr, p.port), 4000) } }.isSuccess
                        steps += tcpStep(ok, (System.nanoTime() - t1) / 1_000_000, udpOnly = false)
                    }
                }
            }
        } else if (sv != null) steps += WStep("Адрес сервера", WState.OK, "авто-сервер: сервер выбирает балансировщик, подробная проверка одной ссылки не нужна")
        return "Проверка идёт мимо VPN (приложение вне туннеля). Адрес сервера определяет системный DNS, остальные данные уходят на сам выбранный сервер.\n\n" + format(steps)
    }
}
