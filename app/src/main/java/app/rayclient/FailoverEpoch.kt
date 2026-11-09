package app.rayclient

/**
 * Gates asynchronous failover work at each boundary after blocking DNS / HEAD
 * operations. A stale watcher may not touch a newer VPN session.
 */
internal object FailoverEpoch {
    fun canModify(watchGeneration: Int, currentGeneration: Int, tunOpen: Boolean): Boolean =
        watchGeneration == currentGeneration && tunOpen
}
