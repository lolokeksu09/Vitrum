package app.rayclient

/** Only a disconnected VPN can select a different server without lying about the live tunnel. */
internal enum class ServerChoiceDecision { SELECT, NO_CHANGE, REQUIRE_DISCONNECT }

internal object ServerChoicePolicy {
    fun decide(
        targetId: String,
        selectedId: String?,
        connected: Boolean,
        busy: Boolean,
        blocked: Boolean
    ): ServerChoiceDecision = when {
        targetId == selectedId -> ServerChoiceDecision.NO_CHANGE
        connected || busy || blocked -> ServerChoiceDecision.REQUIRE_DISCONNECT
        else -> ServerChoiceDecision.SELECT
    }
}
