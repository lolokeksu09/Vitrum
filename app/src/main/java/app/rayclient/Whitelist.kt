package app.rayclient

/**
 * Белый список доменов скачивается из чужого репозитория, а домены из него идут мимо VPN.
 * Поэтому оставляем только строки, похожие на домен (и комментарии), и не принимаем подозрительно короткий список.
 */
object Whitelist {
    private val DOMAIN = Regex("""^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9-]{2,63}$""")

    /** Очищенный текст списка или null, если годных доменов меньше 50 (ответ сломан или подменён). */
    fun clean(raw: String): String? {
        val lines = raw.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val good = lines.filter { it.startsWith("#") || DOMAIN.matches(it) }
        return if (good.count { !it.startsWith("#") } < 50) null else good.joinToString("\n", postfix = "\n")
    }
}
