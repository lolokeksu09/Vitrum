package app.rayclient

/**
 * A queued START may outlive the command which created it. Only the most recent
 * service command is allowed to mutate session state or publish connection status.
 */
internal object VpnStartEpoch {
    fun canProceed(startToken: Int, currentToken: Int): Boolean = startToken == currentToken
}
