package app.rayclient

/** Only predicts membership in Android's TUN interface, not an Xray rule outcome. */
enum class AppTunPolicy { IN_TUN, EXCLUDED }

object AppRoutePreview {
    /**
     * Mirrors RayVpnService.Builder addAllowedApplication/addDisallowedApplication:
     * 0 = all except VITRUM, 1 = only allowlisted, 2 = all except denylisted.
     * Must not be mistaken for an observed network route.
     */
    fun evaluate(mode: Int, selected: Set<String>, candidate: String, vpnPackage: String): AppTunPolicy =
        when {
            candidate == vpnPackage -> AppTunPolicy.EXCLUDED
            mode == 1 -> if (candidate in selected) AppTunPolicy.IN_TUN else AppTunPolicy.EXCLUDED
            mode == 2 -> if (candidate in selected) AppTunPolicy.EXCLUDED else AppTunPolicy.IN_TUN
            else -> AppTunPolicy.IN_TUN
        }
}
