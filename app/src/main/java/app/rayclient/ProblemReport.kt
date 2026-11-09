package app.rayclient

import android.content.Context
import java.io.File

/**
 * «Сообщить о проблеме»: отчёт составляется на устройстве и никуда не отправляется сам. Пользователь видит его целиком и сам решает,
 * копировать ли его или передать через системное меню «Поделиться». Перед показом из текста убираются ссылки, адреса, UUID, почта, ключи
 * и имена хостов. Лишнее замазать не страшно, пропустить нельзя.
 */
object ProblemReport {
    const val SUBJECT = "Vitrum: отчёт о проблеме"

    private val URL = Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://\S+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val UUID = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val IPV6 = Regex("""(?<![\w:])[0-9A-Fa-f:]*:[0-9A-Fa-f:]+(?![\w:])""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
    private val LONG = Regex("""\b[A-Za-z0-9+/_=-]{28,}\b""")
    private val HOST = Regex("""\b(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}\b""")
    private val KEEP_PREFIX = listOf("java.", "javax.", "android.", "androidx.", "kotlin.", "kotlinx.", "libcore.", "dalvik.", "com.android.", "com.google.", "org.json", "app.rayclient")
    private val KEEP_EXT = setOf("log", "json", "kt", "java", "txt", "dat", "apk", "xml", "sock")
    private val MAPPED = Regex("""(?i)::ffff:\d{1,3}(?:\.\d{1,3}){3}""")

    /** Похоже ли на IPv6-адрес: только 0-9a-f и «:», либо сокращение «::», либо полные 8 групп. Время вида 12:34:56 адресом не считается. */
    private fun isIpv6(s: String): Boolean {
        if (s.any { !(it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':') }) return false
        if (s.count { it == ':' } < 2 || s.none { it != ':' }) return false
        return "::" in s || s.count { it == ':' } == 7
    }

    /** Текст без ссылок, адресов, UUID, почты, длинных ключей и имён хостов. */
    fun sanitize(text: String): String {
        var t = text
        t = URL.replace(t, "<ссылка>")
        t = EMAIL.replace(t, "<почта>")
        t = UUID.replace(t, "<uuid>")
        t = MAPPED.replace(t, "<ip>")
        t = IPV6.replace(t) { if (isIpv6(it.value)) "<ip>" else it.value }
        t = IPV4.replace(t, "<ip>")
        t = LONG.replace(t, "<ключ>")
        t = HOST.replace(t) { m ->
            val v = m.value
            // имена классов и файлов из журнала остаются; хост вида android.example.com или notion.so — нет:
            // префикс пакета считается, только если в имени есть сегмент с заглавной буквы (класс), а .so — только у файлов lib*.so
            val ext = v.substringAfterLast('.').lowercase()
            val pkg = KEEP_PREFIX.any { v.startsWith(it) } && v.split('.').any { it.firstOrNull()?.isUpperCase() == true }
            if (pkg || ext in KEEP_EXT || (ext == "so" && v.startsWith("lib"))) v else "<хост>"
        }
        return t
    }

    private val DS = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
    private val ACT = listOf("через VPN", "напрямую", "блокировать")
    private val APP = listOf("все через VPN", "только выбранные", "кроме выбранных")
    private val TR = listOf("TCP и UDP", "только TCP", "только UDP")
    private val PING = listOf("TCP", "ICMP", "HEAD сервер", "HEAD VPN")

    /** Содержимое отчёта до замены: версия, система, состояние, ключевые настройки (числа и названия режимов), конец журнала Xray. */
    fun raw(ctx: Context, note: String): String {
        val b = StringBuilder()
        fun line(s: String = "") { b.append(s).append('\n') }
        line("Отчёт о проблеме $BRAND ${appVersion(ctx) ?: "?"}")
        if (note.isNotBlank()) { line(); line("Описание:"); line(note.trim().take(2000)) }
        line(); line("Система: Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        // название сервера в состоянии «Подключено: …» в отчёт не идёт
        line("Подключение: " + (if (RayVpnService.connected) "есть" else "нет") + ", состояние: " + RayVpnService.state.let { if (it.startsWith("Подключено")) "Подключено" else it })
        if (RayVpnService.hint.isNotBlank()) line("Подсказка службы: " + RayVpnService.hint)
        line(); line("Настройки:")
        line("• Программы: ${APP.getOrElse(AppState.appMode) { "?" }}; протоколы: ${TR.getOrElse(AppState.transport) { "?" }}; MTU ${AppState.mtu}")
        line("• Маршрутизация: стратегия ${DS.getOrElse(AppState.domainStrategy) { "?" }}; остальной трафик ${ACT.getOrElse(AppState.defaultAction) { "?" }}; правил ${AppState.rules.size}, из них выключено ${AppState.rules.count { !it.enabled }}")
        line("• Kill switch: ${AppState.killSwitch}; переподключение при смене сети: ${AppState.reconnectNet}; автопереключение: ${AppState.autoSwitch}")
        line("• Фрагментация: ${AppState.fragment}; без IPv6: ${AppState.blockV6}; поддельный DNS: ${AppState.fakeDns}; определение адреса: ${AppState.sniff}; игровой режим: ${AppState.gameMode}")
        line("• Замер: ${PING.getOrElse(AppState.pingMode) { "?" }}, потоков ${if (AppState.pingThreads == 0) "авто" else AppState.pingThreads.toString()}; Hysteria2 по таймеру: ${AppState.hyRestart}; Root-функции: ${AppState.rootOn}")
        line("• Серверов: ${AppState.servers.size}, подписок: ${AppState.subs.size}")
        val log = LogTail.read(File(ctx.filesDir, "xray.log")).ifBlank { RayVpnService.logTail }
        if (log.isNotBlank()) { line(); line("Конец журнала Xray:"); log.trim().lines().takeLast(40).forEach { line(it.take(300)) } }
        return b.toString()
    }

    /** Готовый отчёт для показа, копирования и передачи. */
    fun build(ctx: Context, note: String): String =
        sanitize(raw(ctx, note)) + "\nОтчёт составлен на устройстве и никуда не отправлялся. Ссылки, адреса, UUID, почта и ключи заменены.\n"
}
