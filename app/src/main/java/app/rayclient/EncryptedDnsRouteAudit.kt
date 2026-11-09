package app.rayclient

/**
 * Read-only rule-order analysis of selected literal resolver addresses on
 * standard encrypted DNS ports: DoT TCP/853 and DoQ UDP/853.
 *
 * No DNS requests, packet capture, hostname resolution or stored endpoints.
 * Android Private DNS may use a different destination from configured Xray DNS.
 */
internal data class EncryptedDnsRouteResult(
    val tcp: DnsAuditKind,
    val udp: DnsAuditKind,
    val overall: DnsAuditKind,
    val resolvers: Int
) {
    val isLeakProof: Boolean get() = false
}

internal object EncryptedDnsRouteAudit {
    private fun verdict(verdict: RouteVerdict): DnsAuditKind = when (verdict) {
        RouteVerdict.VPN -> DnsAuditKind.PROXY
        RouteVerdict.DIRECT -> DnsAuditKind.DIRECT
        RouteVerdict.BLOCK -> DnsAuditKind.BLOCK
        RouteVerdict.EXCLUDED, RouteVerdict.UNKNOWN -> DnsAuditKind.UNKNOWN
    }

    private fun combine(results: List<DnsAuditKind>): DnsAuditKind {
        if (results.isEmpty() || DnsAuditKind.UNKNOWN in results) return DnsAuditKind.UNKNOWN
        return results.distinct().singleOrNull() ?: DnsAuditKind.MIXED
    }

    fun inspect(options: RoutingOptions, resolvers: List<String>): EncryptedDnsRouteResult {
        val valid = resolvers.filter(DnsAddressPolicy::isLiteral).distinct()
        if (valid.isEmpty()) return EncryptedDnsRouteResult(
            DnsAuditKind.UNKNOWN, DnsAuditKind.UNKNOWN, DnsAuditKind.UNKNOWN, 0
        )
        val tcp = combine(valid.map {
            verdict(RouteExplain.evaluateTunRoute(options, it, 853, "tcp").verdict)
        })
        val udp = combine(valid.map {
            verdict(RouteExplain.evaluateTunRoute(options, it, 853, "udp").verdict)
        })
        return EncryptedDnsRouteResult(tcp, udp, combine(listOf(tcp, udp)), valid.size)
    }
}
