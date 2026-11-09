package app.rayclient

/** A user-selected server may be committed only by the active VPN watcher after validation. */
internal object ManualSwitchEpoch {
    fun mayRequest(
        connected: Boolean, busy: Boolean, blocked: Boolean, hasTun: Boolean,
        pid: Int, currentId: String?, targetId: String?, targetExists: Boolean
    ): Boolean = connected && !busy && !blocked && hasTun && pid > 0 &&
        currentId != null && !targetId.isNullOrBlank() && currentId != targetId && targetExists

    fun mayProceed(g: Int, generation: Int, hasTun: Boolean): Boolean =
        g == generation && hasTun
}
