package app.rayclient

/** A process observation belongs only to the watcher that inspected that exact PID. */
internal enum class WatchProcessDecision { IGNORE, RUNNING, RETRY, FAILED }

internal object WatchProcessEpoch {
    /**
     * Native.alive() may return after STOP, a new START or an Xray relaunch.
     * An obsolete observation must neither publish status nor invoke fail().
     */
    fun afterAlive(
        watchGeneration: Int,
        currentGeneration: Int,
        tunnelOpen: Boolean,
        checkedPid: Int,
        currentPid: Int,
        alive: Boolean,
        restarts: Int
    ): WatchProcessDecision = when {
        watchGeneration != currentGeneration || !tunnelOpen ||
            checkedPid <= 0 || checkedPid != currentPid -> WatchProcessDecision.IGNORE
        alive -> WatchProcessDecision.RUNNING
        restarts >= 3 -> WatchProcessDecision.FAILED
        else -> WatchProcessDecision.RETRY
    }
}
