package app.rayclient

/** Only the VPN service may change the selected ID during an active connection. */
internal enum class ServerChoiceDecision { SELECT, REQUEST_SWITCH, NO_CHANGE, REQUIRE_DISCONNECT }

internal object ServerChoicePolicy {
    fun decide(
        targetId: String,
        selectedId: String?,
        connected: Boolean,
        busy: Boolean,
        blocked: Boolean
    ): ServerChoiceDecision = when {
        targetId == selectedId -> ServerChoiceDecision.NO_CHANGE
        busy || blocked || (connected && selectedId == null) -> ServerChoiceDecision.REQUIRE_DISCONNECT
        connected -> ServerChoiceDecision.REQUEST_SWITCH
        else -> ServerChoiceDecision.SELECT
    }
}


/** Deleting the active route must not leave a live TUN attached to a different UI selection. */
internal object ServerDeletionPolicy {
    fun mayDelete(
        selectedId: String?,
        affectedServerIds: Set<String>,
        connected: Boolean,
        busy: Boolean,
        blocked: Boolean,
        switching: Boolean
    ): Boolean {
        val removesSelectedServer = selectedId != null && selectedId in affectedServerIds
        return !removesSelectedServer || !(connected || busy || blocked || switching)
    }
}


/** A subscription update must not remove the server backing a live/protected VPN session. */
internal object SubscriptionRefreshPolicy {
    fun mayApply(
        selectedSubId: String?,
        selectedLink: String?,
        updatingSubId: String,
        incomingLinks: Set<String>,
        routeProtected: Boolean
    ): Boolean {
        val removesSelectedRoute =
            selectedSubId == updatingSubId &&
                !selectedLink.isNullOrBlank() &&
                selectedLink !in incomingLinks
        return !removesSelectedRoute || !routeProtected
    }

    fun mayRemoveAutoServers(selectedId: String?, autoServerIds: Set<String>, routeProtected: Boolean): Boolean {
        val removesSelectedRoute = selectedId != null && selectedId in autoServerIds
        return !removesSelectedRoute || !routeProtected
    }
}
