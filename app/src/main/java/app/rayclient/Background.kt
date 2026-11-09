package app.rayclient

import android.content.Context
import android.os.PowerManager

/** Работа VPN в фоне без root: состояние экономии заряда и подсказка по автозапуску для оболочки производителя. */
object Background {
    /** Исключено ли приложение из экономии заряда (узнать можно без разрешений). */
    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        runCatching { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)

    /** Подсказка по производителю телефона. Названия пунктов меняются между версиями прошивок: это ориентир, а не точный путь. */
    fun hint(manufacturer: String): String = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi, Redmi, POCO: «Безопасность» → «Разрешения» → «Автозапуск», включите приложение; в сведениях о приложении «Экономия заряда» → «Нет ограничений»."
        "huawei", "honor" -> "Huawei, Honor: «Приложения» → «Запуск приложений», найдите приложение, выберите «Управлять вручную» и включите все три пункта."
        "samsung" -> "Samsung: «Батарея» → «Ограничения в фоновом режиме» → «Приложения, которые никогда не переходят в спящий режим», добавьте приложение."
        "oppo", "realme", "oneplus" -> "Oppo, Realme, OnePlus: «Батарея» → «Оптимизация батареи» → приложение → «Не оптимизировать»; в управлении запуском разрешите автозапуск."
        "vivo", "iqoo" -> "Vivo, iQOO: «Батарея» → «Фоновое энергопотребление» → «Разрешить»; автозапуск включается в «Приложения» → «Автозапуск»."
        else -> "На некоторых телефонах есть отдельный пункт «Автозапуск» или «Запуск в фоне»: если VPN сам отключается, поищите его в настройках телефона."
    }
}
