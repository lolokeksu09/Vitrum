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
