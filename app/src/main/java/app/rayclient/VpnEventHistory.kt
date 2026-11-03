package app.rayclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-safe VPN lifecycle diagnostics. Only predefined event types and timestamps
 * are kept; no server names, URLs, domains, IP addresses, keys or traffic records.
 */
internal enum class VpnEventType(val title: String, val problem: Boolean = false) {
    START_REQUEST("Запрос подключения"),
    CONNECTED("Xray запущен"),
    STOP_REQUEST("Запрос отключения"),
    STOPPED("VPN отключён"),
    START_FAILED("Ошибка VPN", true),
    FAIL_CLOSED("Трафик заблокирован после ошибки", true),
    SWITCH_REQUEST("Запрос смены сервера"),
    SWITCHED("Сервер переключён"),
    SWITCH_RESTORED("Предыдущий сервер восстановлен", true),
    RECONNECTING("Перезапуск Xray"),
    RECONNECTED("Xray перезапущен"),
    NETWORK_LOST("Основная сеть недоступна", true),
    NETWORK_AVAILABLE("Основная сеть изменилась"),
    AUTO_SWITCHED("Автоматическая смена сервера")
}

internal data class VpnEvent(val type: VpnEventType, val atMs: Long, val count: Int = 1)
internal data class VpnEventSummary(val switches: Int, val problems: Int, val networkLosses: Int)

internal object VpnEventPolicy {
    const val MAX_ITEMS = 40
    private const val COALESCE_MS = 2_000L

    fun append(events: List<VpnEvent>, type: VpnEventType, atMs: Long): List<VpnEvent> {
        val last = events.lastOrNull()
        if (last != null && last.type == type && atMs >= last.atMs && atMs - last.atMs <= COALESCE_MS) {
            return events.dropLast(1) + last.copy(atMs = atMs, count = last.count + 1)
        }
        return (events + VpnEvent(type, atMs)).takeLast(MAX_ITEMS)
    }

    fun summary(events: List<VpnEvent>) = VpnEventSummary(
        switches = events.filter { it.type == VpnEventType.SWITCHED || it.type == VpnEventType.AUTO_SWITCHED }.sumOf { it.count },
        problems = events.filter { it.type.problem }.sumOf { it.count },
        networkLosses = events.filter { it.type == VpnEventType.NETWORK_LOST }.sumOf { it.count }
    )

    /** Only enum labels, counts and times enter the report. No untrusted data is accepted. */
    fun report(events: List<VpnEvent>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        val summary = summary(events)
        return buildString {
            appendLine("VITRUM — события VPN (только память устройства)")
            appendLine("Нет адресов, доменов, подписок, ключей и сетевого трафика.")
            appendLine("Смен серверов: ${summary.switches}; проблем: ${summary.problems}; потерь сети: ${summary.networkLosses}")
            events.forEach { event ->
                append(fmt.format(Date(event.atMs)))
                append(" | ")
                append(event.type.title)
                if (event.count > 1) append(" ×${event.count}")
                appendLine()
            }
        }
    }
}

/** Process-lifetime ring buffer. No permissions, files, telemetry or network calls. */
internal object VpnEventHistory {
    var entries by mutableStateOf<List<VpnEvent>>(emptyList())
        private set

    @Synchronized fun record(type: VpnEventType, atMs: Long = System.currentTimeMillis()) {
        entries = VpnEventPolicy.append(entries, type, atMs)
    }

    @Synchronized fun clear() { entries = emptyList() }
}
