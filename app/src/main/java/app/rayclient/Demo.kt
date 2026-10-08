package app.rayclient

import android.content.Intent
import android.content.pm.ApplicationInfo
import kotlin.math.sin

/**
 * Демо-режим для скриншотов в эмуляторе (скрипты и workflow для снимков из репозитория убраны).
 * Только отладочная сборка и только по команде adb, например:
 *   am start -S -n app.rayclient/.MainActivity --ez vitrum_demo true --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
 * Данные выдуманные (домены example.com), на диск не сохраняются, подписки не обновляются.
 */
object Demo {
    /** Начальная вкладка и раздел настроек для этого запуска. */
    var tab = 0
    var settingsPage: String? = null
    var servers = false   // открыть шторку серверов

    /** Запущено в демо-режиме для снимков: блокировку приложения не включаем. */
    fun requested(act: MainActivity, i: Intent?): Boolean =
        i?.getBooleanExtra("vitrum_demo", false) == true && act.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun apply(act: MainActivity, i: Intent?) {
        if (i == null || !i.getBooleanExtra("vitrum_demo", false)) return
        if (act.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        AppState.autoUpd = false; AppState.monitor = false   // без фоновых запросов к выдуманной подписке
        load()
        when (i.getStringExtra("vitrum_theme")) { "dark" -> AppState.theme = 1; "light" -> AppState.theme = 2 }
        when (i.getStringExtra("vitrum_lang")) { "ru" -> AppState.lang = "ru"; "en" -> AppState.lang = "en" }   // у эмулятора английская локаль
        val screen = i.getStringExtra("vitrum_screen") ?: "home"
        tab = if (screen.startsWith("settings") || screen == "routing") 1 else 0
        servers = screen == "servers"
        settingsPage = if (screen == "routing") "routes" else screen.substringAfter("settings:", "").ifEmpty { null }
        when (screen) {
            "shield" -> AppState.showShield = true
            "game" -> AppState.showGame = true
            "journal" -> AppState.showJournal = true
        }
        if (i.getStringExtra("vitrum_state") == "connected") fakeConnected()
    }

    private fun load() {
        AppState.onboarded = true
        val now = System.currentTimeMillis()
        val names = listOf("🇩🇪 Германия · Франкфурт", "🇳🇱 Нидерланды · Амстердам", "🇫🇮 Финляндия · Хельсинки", "🇸🇪 Швеция · Стокгольм",
            "🇺🇸 США · Нью-Йорк", "🇯🇵 Япония · Токио", "🇹🇷 Турция · Стамбул", "🇰🇿 Казахстан · Алматы", "🇷🇺 Россия → 🇩🇪 Германия (мост)")
        val pings = listOf(48, 63, 71, 92, 138, 212, 104, 87, 156)
        val fresh = AppState.subs.none { it.id == "demo" }   // при повторном запуске подписка и серверы уже сохранены, а пинги и история живут только в памяти
        if (fresh) AppState.subs += Sub("demo", "https://sub.example.com/demo", "Demo VPN", now - 20 * 60_000L,
            used = 12_400_000_000L, total = 300_000_000_000L, expire = now / 1000 + 29 * 86_400L,
            announce = "Новые серверы в Японии и Турции")
        names.forEachIndexed { k, n ->
            val net = if (k % 3 == 2) "xhttp" else "tcp"
            val link = "vless://00000000-0000-4000-8000-00000000000$k@demo$k.example.com:443?type=$net&security=reality" +
                "&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.example.com&fp=chrome"
            if (fresh) AppState.servers += Server("demo-$k", n, link, "demo")
            AppState.pings["demo-$k"] = pings[k]
            repeat(12) { j -> Health.add(link, (j + k) % 9 != 0, pings[k] + j * 3) }   // история для рейтинга стабильности
        }
        AppState.favs.clear()
        AppState.favs += AppState.servers.filter { it.subId == "demo" }.take(2).map { it.link }
        AppState.selectedId = "demo-0"
        HealthUi.version++
    }

    /** Вид «Подключено» без настоящего туннеля: таймер, скорость и график. */
    private fun fakeConnected() {
        RayVpnService.connected = true; RayVpnService.busy = false; RayVpnService.blocked = false
        RayVpnService.since = System.currentTimeMillis() - 3_723_000L
        RayVpnService.state = "Подключено: " + (AppState.servers.firstOrNull { it.id == AppState.selectedId }?.name ?: "")
        Speed.reset(); var total = 1_480_000_000L
        for (k in 0 until Speed.WINDOW) {
            val d = (900_000 + 650_000 * sin(k / 3.0) + 300_000 * sin(k / 1.3)).toLong().coerceAtLeast(40_000L)
            val u = d / 7; total += d + u
            Speed.add(d, u, total)
        }
    }
}
