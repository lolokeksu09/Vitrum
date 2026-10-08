package app.rayclient

import androidx.compose.runtime.*

// ---------- DNS ----------
private val ipRe = Regex("""^(\d{1,3}(\.\d{1,3}){3}|[0-9a-fA-F:]{3,39})$""")

internal fun AppState.dnsIps(): List<String> =
    if (dnsPreset in DNS_PRESETS.indices) DNS_PRESETS[dnsPreset].second
    else dnsCustom.split(',', ' ', '\n', ';').map { it.trim() }.filter { ipRe.matches(it) }.ifEmpty { DNS_PRESETS[0].second }

/** IPv4-адрес для системного DNS туннеля (запросы приложений всё равно перехватывает Xray). */
internal fun AppState.dnsSystemIp(): String = dnsIps().firstOrNull { it.contains('.') } ?: "1.1.1.1"

