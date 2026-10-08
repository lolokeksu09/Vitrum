package app.rayclient

import android.content.Context

/**
 * «Работа в фоне» с root: исключить Vitrum из экономии заряда и разрешить работу в фоне системными командами.
 * Меняет настройки системы только для этого приложения, по кнопке и после подтверждения; сбросить можно кнопкой «Сбросить к умолчанию».
 * Команды (по памяти, на устройстве не проверялись): `dumpsys deviceidle whitelist ±пакет`, `cmd appops set пакет RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND allow|default`.
 */
object BgRoot {
    private val PKG = Regex("^[A-Za-z0-9._]{1,100}$")

    fun applyCommands(pkg: String): List<String>? = if (!PKG.matches(pkg)) null else listOf(
        "dumpsys deviceidle whitelist +$pkg",
        "cmd appops set $pkg RUN_IN_BACKGROUND allow",
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")

    fun revertCommands(pkg: String): List<String>? = if (!PKG.matches(pkg)) null else listOf(
        "dumpsys deviceidle whitelist -$pkg",
        "cmd appops set $pkg RUN_IN_BACKGROUND default",
        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default")

    fun statusCommand(pkg: String): String? = if (!PKG.matches(pkg)) null else "cmd appops get $pkg RUN_ANY_IN_BACKGROUND"

    /** Режим из ответа `cmd appops get`: «RUN_ANY_IN_BACKGROUND: allow; time=…» → «allow». null — ответ не разобран. */
    fun parseMode(out: String): String? =
        Regex("RUN_ANY_IN_BACKGROUND\\s*:\\s*([A-Za-z_]+)").find(out)?.groupValues?.get(1)?.lowercase()

    /** Итог для экрана: каждая строка переводится отдельно. */
    fun describe(ignoringBattery: Boolean, mode: String?, failed: Int): String {
        val l = mutableListOf<String>()
        l += if (ignoringBattery) "Экономия заряда для приложения: отключена." else "Экономия заряда для приложения: включена."
        l += when {
            mode == null -> "Запуск в фоне: не удалось определить."
            mode == "allow" -> "Запуск в фоне: разрешён."
            else -> "Запуск в фоне: не разрешён (режим $mode)."
        }
        if (failed > 0) l += "Команд не принято: $failed. Прошивка могла отказать."
        return l.joinToString("\n")
    }

    /** action: 0 — только проверить, 1 — применить, 2 — сбросить к умолчанию. Результат в AppState.bgRootInfo. */
    fun run(ctx: Context, action: Int) {
        if (AppState.bgRootBusy) return
        if (!AppState.rootOn) { AppState.message = "Root-функции выключены. Включите их в «Настройки → Сервис → Root»."; return }
        val pkg = ctx.packageName
        val cmds = when (action) { 1 -> applyCommands(pkg); 2 -> revertCommands(pkg); else -> emptyList() } ?: return
        val status = statusCommand(pkg) ?: return
        AppState.bgRootBusy = true
        val app = ctx.applicationContext
        Thread {
            try {
                if (!RootShell.available()) { AppState.message = "Нет root-доступа. Выдайте его приложению в менеджере root и повторите."; return@Thread }
                val failed = cmds.count { !RootShell.run(it).ok }
                val mode = parseMode(RootShell.run(status).out)
                AppState.bgRootInfo = describe(Background.ignoringBatteryOptimizations(app), mode, failed)
            } finally { AppState.bgRootBusy = false }
        }.start()
    }
}
