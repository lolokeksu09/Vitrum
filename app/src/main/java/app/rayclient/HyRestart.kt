package app.rayclient

/**
 * Перезапуск Xray по таймеру для подключений через Hysteria2 (по умолчанию выключен). Туннель не закрывается: рвутся только текущие соединения.
 * Зачем: у других клиентов описано, что Hysteria2 может «зависать» спустя время; здесь это не проверялось, поэтому функция необязательная.
 */
object HyRestart {
    const val MIN = 1
    const val MAX = 60
    const val DEFAULT = 5

    fun clamp(minutes: Int) = minutes.coerceIn(MIN, MAX)

    /** Идёт ли подключение через Hysteria2: сервер-ссылка hysteria2 или конфигурация (авто-сервер) с транспортом hysteria. */
    fun usesHysteria(proto: String, configText: String?): Boolean =
        proto == "hysteria2" || (configText != null && configText.filterNot { it.isWhitespace() }.contains("\"network\":\"hysteria\""))

    /** Пора ли перезапускать: включено, подключение через Hysteria2 и с последнего запуска Xray прошло не меньше заданного времени. */
    fun due(enabled: Boolean, uses: Boolean, minutes: Int, sinceStartMs: Long): Boolean =
        enabled && uses && sinceStartMs >= clamp(minutes) * 60_000L
}
