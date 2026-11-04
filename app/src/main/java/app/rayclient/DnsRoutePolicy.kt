package app.rayclient

/**
 * Observes the intended DNS rule policy without interpreting it as DNS leak proof.
 * Port-53 rules are transport-wide and apply only to apps actually included in the VPN.
 */
internal object DnsRoutePolicy {
    fun usesInternalDns(r: RoutingOptions): Boolean = r.doh || r.blockV6 || r.fakeDns

    /** A user rule for port 53 (including ranges) intentionally precedes the proxy guard. */
    fun hasUserPort53Rule(r: RoutingOptions): Boolean = r.rules.any {
        it.enabled && it.kind == 2 && it.value.split(',', ';', ' ', '\n').any { token ->
            val parts = token.trim().split('-')
            when (parts.size) {
                1 -> parts[0].toIntOrNull() == 53
                2 -> {
                    val low = parts[0].toIntOrNull()
                    val high = parts[1].toIntOrNull()
                    low != null && high != null && low <= 53 && high >= 53
                }
                else -> false
            }
        }
    }

    /** Describes source configuration, NOT observed traffic or Android per-app routes. */
    fun describe(r: RoutingOptions): String {
        val mode = when {
            r.fakeDns -> "FakeDNS: обычные DNS TCP/UDP 53 перехватывает Xray DNS."
            r.doh -> "DoH: обычные DNS TCP/UDP 53 перехватывает Xray DNS."
            r.blockV6 -> "IPv6 выключен в настройках: DNS TCP/UDP 53 перехватывает Xray DNS."
            hasUserPort53Rule(r) -> "Сначала применяются пользовательские правила для DNS-порта 53, затем резервный маршрут через Xray."
            else -> "DNS TCP/UDP 53 направляется через Xray перед общими direct/block-правилами."
        }
        return "$mode Это только схема маршрутизации текущих настроек, а не проверка DNS-утечек. Приложения вне VPN и DoH/DoT здесь не измеряются."
    }
}
