package app.rayclient.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiteConnectionRulesTest {
    @Test public void permitsFreshStartWhenIdle() {
        assertTrue(LiteConnectionRules.canStart(false, false, false));
        assertFalse(LiteConnectionRules.requiresDisconnect(false, false, false));
    }
    @Test public void deniesSwitchDuringActiveVpnOrStartup() {
        assertFalse(LiteConnectionRules.canStart(true, false, false));
        assertFalse(LiteConnectionRules.canStart(false, true, false));
        assertTrue(LiteConnectionRules.requiresDisconnect(true, false, false));
        assertTrue(LiteConnectionRules.requiresDisconnect(false, true, false));
    }
    @Test public void deniesStartDuringStop() {
        assertFalse(LiteConnectionRules.canStart(false, false, true));
        assertTrue(LiteConnectionRules.requiresDisconnect(false, false, true));
    }
}
