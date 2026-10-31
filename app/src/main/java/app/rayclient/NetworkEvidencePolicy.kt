package app.rayclient

/**
 * Tracks default-network callbacks without assuming Android delivers onLost before onAvailable.
 * A lost network remains the reference until a new onAvailable: reconnect must occur only when
 * a network is available again, while previous manual HTTP evidence expires immediately.
 */
internal data class NetworkEvidenceState<T>(val last: T? = null, val lost: Boolean = false)

internal data class NetworkEvidenceEvent<T>(
    val state: NetworkEvidenceState<T>,
    val invalidateProof: Boolean,
    val reconnect: Boolean
)

internal object NetworkEvidencePolicy {
    fun <T> available(
        previous: NetworkEvidenceState<T>,
        network: T,
        connected: Boolean,
        reconnectEnabled: Boolean
    ): NetworkEvidenceEvent<T> {
        val changed = connected && previous.last != null &&
            (previous.last != network || previous.lost)
        return NetworkEvidenceEvent(
            NetworkEvidenceState(last = network),
            invalidateProof = changed,
            reconnect = changed && reconnectEnabled
        )
    }

    fun <T> lost(
        previous: NetworkEvidenceState<T>,
        network: T,
        connected: Boolean
    ): NetworkEvidenceEvent<T> {
        val tracked = previous.last != null && previous.last == network
        return NetworkEvidenceEvent(
            state = if (tracked) previous.copy(lost = true) else previous,
            invalidateProof = tracked && connected,
            reconnect = false
        )
    }
}
