package app.rayclient

/** Pure first-run status; never treats a requested VPN permission as granted. */
internal data class OnboardingReadiness(
    val hasServer: Boolean,
    val vpnGranted: Boolean,
    val notificationsGranted: Boolean
) {
    /** Android may ask for VPN permission when the user explicitly connects. */
    val canRequestConnection: Boolean get() = hasServer

    companion object {
        fun evaluate(
            ids: List<String>,
            selectedId: String?,
            vpnGranted: Boolean,
            notificationsGranted: Boolean
        ) = OnboardingReadiness(
            hasServer = selectedId != null && selectedId in ids,
            vpnGranted = vpnGranted,
            notificationsGranted = notificationsGranted
        )
    }
}
