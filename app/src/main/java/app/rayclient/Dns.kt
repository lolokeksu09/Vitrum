package app.rayclient

import androidx.compose.runtime.*

// ---------- DNS ----------
internal fun AppState.dnsIps(): List<String> =
    if (dnsPreset in DNS_PRESETS.indices) DNS_PRESETS[dnsPreset].second
    else DnsAddressPolicy.parse(dnsCustom).select(DNS_PRESETS[0].second)

/** IPv4-адрес для системного DNS туннеля (запросы приложений всё равно перехватывает Xray). */
internal fun AppState.dnsSystemIp(): String = dnsIps().firstOrNull { it.contains('.') } ?: "1.1.1.1"

