package app.rayclient.lite;

/** UI connection policy: never claim a server switch while a VPN session remains active. */
final class LiteConnectionRules {
    private LiteConnectionRules() {}

    static boolean canStart(boolean connected, boolean busy, boolean stopping) {
        return !connected && !busy && !stopping;
    }

    static boolean requiresDisconnect(boolean connected, boolean busy, boolean stopping) {
        return connected || busy || stopping;
    }
}
