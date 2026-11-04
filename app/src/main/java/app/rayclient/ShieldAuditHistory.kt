package app.rayclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Privacy-preserving summary: no IPs, ports, server IDs, domains, paths or Xray logs. */
internal enum class PortObservation(val label: String) {
    NONE_FOUND("типичные порты закрыты"), OPEN("обнаружены локальные порты"),
    UNKNOWN("не проверено")
}
internal enum class ProbeObservation(val label: String) {
    RESPONSE("получен ответ"), UNKNOWN("не подтверждено")
}
internal data class ShieldAuditEntry(
    val atMs: Long,
    val ports: PortObservation,
    val ipv4: ProbeObservation,
    val ipv6: ProbeObservation
)

internal object ShieldAuditPolicy {
    const val MAX_ITEMS = 12

    fun capture(proof: FamilyHttpSnapshot, network: ShieldNet?, now: Long): ShieldAuditEntry {
        val ports = when {
            !ShieldEvidencePolicy.isCurrent(network, proof.serverId, proof.session, proof.evidenceToken, now) ->
                PortObservation.UNKNOWN
            network!!.ports.isEmpty() -> PortObservation.NONE_FOUND
            else -> PortObservation.OPEN
        }
        return ShieldAuditEntry(
            atMs = proof.atMs,
            ports = ports,
            ipv4 = if (proof.ipv4?.status == FamilyHttpStatus.REACHABLE)
                ProbeObservation.RESPONSE else ProbeObservation.UNKNOWN,
            ipv6 = if (proof.ipv6?.status == FamilyHttpStatus.REACHABLE)
                ProbeObservation.RESPONSE else ProbeObservation.UNKNOWN
        )
    }

    fun append(events: List<ShieldAuditEntry>, entry: ShieldAuditEntry): List<ShieldAuditEntry> =
        (events + entry).takeLast(MAX_ITEMS)

    fun diff(previous: ShieldAuditEntry, current: ShieldAuditEntry): List<String> = buildList {
        if (previous.ports != current.ports) add("Локальные порты: ${current.ports.label}")
        if (previous.ipv4 != current.ipv4) add("IPv4: ${current.ipv4.label}")
        if (previous.ipv6 != current.ipv6) add("IPv6: ${current.ipv6.label}")
    }

    fun report(events: List<ShieldAuditEntry>): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        return buildString {
            appendLine("VITRUM — история проверок Щита приватности")
            appendLine("Только отметки времени и категории результатов.")
            appendLine("Не доказывает отсутствие DNS, IPv4/IPv6 или других утечек.")
            events.forEach {
                appendLine("${date.format(Date(it.atMs))} | порты: ${it.ports.label} | IPv4: ${it.ipv4.label} | IPv6: ${it.ipv6.label} | DNS: UNKNOWN")
            }
        }
    }
}

/** Process-only buffer; no file writes, networking, permission requests or telemetry. */
internal object ShieldAuditHistory {
    var entries by mutableStateOf<List<ShieldAuditEntry>>(emptyList())
        private set

    @Synchronized fun record(proof: FamilyHttpSnapshot, network: ShieldNet?) {
        entries = ShieldAuditPolicy.append(entries,
            ShieldAuditPolicy.capture(proof, network, System.currentTimeMillis()))
    }
    @Synchronized fun clear() { entries = emptyList() }
}
