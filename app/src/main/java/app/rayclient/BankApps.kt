package app.rayclient

/**
 * Пресет «Банки и госуслуги мимо VPN»: имена пакетов известных российских банков и госсервисов. Приложения из списка пускаются
 * напрямую, мимо VPN, обычным исключением (`addDisallowedApplication`, режим «Кроме выбранных»); root не нужен.
 *
 * Список составлен вручную 2026-10-07 и может устареть (приложения переименовывают и выпускают заново). В него попали только те
 * пакеты, что нашлись в открытых источниках (RuStore, отчёты Exodus Privacy, списки пакетов антивирусных лабораторий); пакеты,
 * которые найти не удалось (Райффайзен, Россельхозбанк), не включены. Ненайденные на телефоне пакеты просто пропускаются.
 */
object BankApps {
    /** Имя пакета → название в приложении. */
    val list: List<Pair<String, String>> = listOf(
        "ru.sberbankmobile" to "СберБанк Онлайн",
        "com.idamob.tinkoff.android" to "Т-Банк",
        "ru.vtb24.mobilebanking.android" to "ВТБ",
        "ru.alfabank.mobile.android" to "Альфа-Банк",
        "ru.gazprombank.android.mobilebank.app" to "Газпромбанк",
        "ru.rostel" to "Госуслуги",
        "com.gnivts.selfemployed" to "Мой налог",
        "ru.nspk.mirpay" to "Мир Pay",
    )

    /**
     * Что изменится при нажатии. [mode] всегда 2 («Кроме выбранных»: список идёт мимо VPN). [flipsMeaning]: сейчас режим 1
     * («Только выбранные»), где тот же список идёт через VPN, и после переключения уже выбранные программы тоже пойдут мимо.
     */
    class Plan(val mode: Int, val found: List<String>, val added: List<String>, val flipsMeaning: Boolean)

    fun plan(mode: Int, current: Collection<String>, installed: Set<String>): Plan {
        val found = list.map { it.first }.filter { it in installed }
        return Plan(2, found, found.filter { it !in current }, flipsMeaning = mode == 1 && current.isNotEmpty())
    }

    /** Названия программ по именам пакетов, в порядке списка. */
    fun names(pkgs: Collection<String>): List<String> = list.filter { it.first in pkgs }.map { it.second }
}
