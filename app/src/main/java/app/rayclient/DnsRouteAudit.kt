package app.rayclient

/**
 * Configuration-only prediction for resolvers used by apps inside the Android
 * VPN; no DNS query, geolocation, packet sniffing or stored IP addresses.
 */
internal enum class DnsAuditKind(val label: String) {
    PROXY("по правилам: через Xray"),
    XRAY_DNS("по правилам: Xray DNS"),
    DIRECT("по правилам: прямое соединение"),
    BLOCK("по правилам: блокировка"),
    MIXED("разные маршруты"),
    UNKNOWN("нельзя определить по настройкам")
}

internal data class DnsAuditResult(
    val udp: DnsAuditKind,
    val tcp: DnsAuditKind,
    val overall: DnsAuditKind,
    val resolvers: Int
) {
    /** A generated config never proves actual device routing or leak freedom. */
    val isLeakProof: Boolean get() = false
}

internal object DnsRouteAudit {
    private fun combine(routes: List<DnsAuditKind>): DnsAuditKind {
        if (routes.isEmpty() || DnsAuditKind.UNKNOWN in routes) return DnsAuditKind.UNKNOWN
        val known = routes.distinct()
        return if (known.size == 1) known[0] else DnsAuditKind.MIXED
    }

    private fun verdict(verdict: RouteVerdict): DnsAuditKind = when (verdict) {
        RouteVerdict.VPN -> DnsAuditKind.PROXY
        RouteVerdict.DIRECT -> DnsAuditKind.DIRECT
        RouteVerdict.BLOCK -> DnsAuditKind.BLOCK
        RouteVerdict.EXCLUDED, RouteVerdict.UNKNOWN -> DnsAuditKind.UNKNOWN
    }

    /** The audit targets only configured numeric DNS resolvers and port 53. */
    fun inspect(options: RoutingOptions, servers: List<String>): DnsAuditResult {
        val valid = servers.filter(DnsAddressPolicy::isLiteral).distinct()
        if (valid.isEmpty()) return DnsAuditResult(
            DnsAuditKind.UNKNOWN, DnsAuditKind.UNKNOWN, DnsAuditKind.UNKNOWN, 0
        )
        if (DnsRoutePolicy.usesInternalDns(options)) return DnsAuditResult(
            DnsAuditKind.XRAY_DNS, DnsAuditKind.XRAY_DNS,
            DnsAuditKind.XRAY_DNS, valid.size
        )
        val udp = combine(valid.map {
            verdict(RouteExplain.evaluateTunRoute(options, it, 53, "udp").verdict)
        })
        val tcp = combine(valid.map {
            verdict(RouteExplain.evaluateTunRoute(options, it, 53, "tcp").verdict)
        })
        return DnsAuditResult(udp, tcp, combine(listOf(udp, tcp)), valid.size)
    }
}
