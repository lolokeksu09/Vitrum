package app.rayclient

import java.util.Locale

/**
 * Страна сервера по флагу-эмодзи в его названии («🇳🇱 Амстердам»): панели почти всегда так подписывают серверы. Базы IP не нужны.
 * Серверы без флага в названии в фильтр по странам не попадают (видны в «Все»).
 */
object Countries {
    private const val BASE = 0x1F1E6   // региональный индикатор «A»

    /** Двухбуквенный код страны из первой пары региональных индикаторов в названии (верхний регистр) или null. */
    fun flagCode(name: String): String? {
        var i = 0
        while (i < name.length) {
            val a = name.codePointAt(i); val n = Character.charCount(a)
            if (a in BASE..(BASE + 25) && i + n < name.length) {
                val b = name.codePointAt(i + n)
                if (b in BASE..(BASE + 25)) return "" + ('A' + (a - BASE)) + ('A' + (b - BASE))
            }
            i += n
        }
        return null
    }

    fun flag(code: String): String = code.uppercase().map { String(Character.toChars(BASE + (it - 'A'))) }.joinToString("")

    /** Название страны на языке приложения; если система не знает код, сам код. */
    fun title(code: String, locale: Locale): String = Locale("", code).getDisplayCountry(locale).takeIf { it.isNotBlank() && it != code } ?: code

    /** Коды стран по убыванию числа серверов (при равенстве по алфавиту). */
    fun available(names: List<String>): List<Pair<String, Int>> =
        names.mapNotNull { flagCode(it) }.groupingBy { it }.eachCount().toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
}
