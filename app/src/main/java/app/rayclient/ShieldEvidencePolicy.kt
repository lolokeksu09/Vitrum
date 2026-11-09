package app.rayclient

/** Freshness of a manually requested local/network observation.
 * Token is invalidated when the service, server, TUN or underlying network changes.
 * Never promote a stale observation to a successful protection check.
 */
internal object ShieldEvidencePolicy {
    const val MAX_AGE_MS = 300_000L
    fun isCurrent(net: ShieldNet?, serverId: String?, session: Long,
                  token: Long, now: Long): Boolean =
        net != null && net.at > 0 && now >= net.at &&
            now - net.at <= MAX_AGE_MS &&
            net.serverId == serverId && net.session == session &&
            net.proofToken == token
}
