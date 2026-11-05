package app.rayclient

/** UI-only wording: VPN running never means DNS, IPv6 or leak freedom was verified. */
internal data class HomeStatusUi(val title: String, val action: String, val subtitle: String)

internal object HomeStatusUi {
    fun from(
        connected: Boolean, busy: Boolean, blocked: Boolean,
        error: Boolean, selecting: Boolean
    ): HomeStatusUi = when {
        selecting -> HomeStatusUi("Подбираю сервер…", "Подбираю…", "Ищем отвечающий сервер")
        blocked -> HomeStatusUi("Трафик заблокирован", "Отключиться", "Соединение недоступно, трафик заблокирован")
        error -> HomeStatusUi("Ошибка", "Подключиться", "Не удалось установить VPN")
        busy -> HomeStatusUi("Подключение…", "Подключаюсь…", "Ожидается соединение с сервером")
        connected -> HomeStatusUi("VPN запущен", "Отключиться", "Xray работает, утечки проверяются отдельно")
        else -> HomeStatusUi("Не подключено", "Подключиться", "VPN выключен")
    }
}
