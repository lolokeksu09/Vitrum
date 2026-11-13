package app.rayclient.tv;

/**
 * UI-only navigation state. Deliberately has no VPN, networking or profile API.
 * This shell must never claim an active tunnel.
 */
public final class TvNavigationModel {
    public enum Page { HOME, SERVERS, APPS, SETUP, SETTINGS }

    private Page current = Page.HOME;

    public Page current() {
        return current;
    }

    public Page open(Page destination) {
        if (destination == null) throw new IllegalArgumentException("destination");
        current = destination;
        return current;
    }

    /** Back from a sub-page returns Home; Back from Home goes to Android. */
    public boolean backToHome() {
        if (current == Page.HOME) return false;
        current = Page.HOME;
        return true;
    }

    public void restore(String name) {
        if (name == null) return;
        try {
            current = Page.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            current = Page.HOME;
        }
    }

    public boolean canConnect() {
        return false; // No VpnService implementation until Stage 2.
    }
}
