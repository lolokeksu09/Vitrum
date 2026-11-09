package app.rayclient.lite;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static org.junit.Assert.*;

public class LiteSplitPolicyTest {
    private static final String SELF="app.rayclient.lite";
    @Test public void onlyModeRequiresAnApp() {
        try { new LiteSplitPolicy(1, Collections.<String>emptySet()).validate(SELF); fail("empty allowlist"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("выбери")); }
    }
    @Test public void cannotOnlyTunnelOwnVpnUid() {
        try { new LiteSplitPolicy(1, Collections.singleton(SELF)).validate(SELF); fail("own UID only"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected); }
    }
    @Test public void allowsSelectedApps() {
        LiteSplitPolicy p=new LiteSplitPolicy(1,new HashSet<>(Arrays.asList(SELF,"com.example.browser")));
        p.validate(SELF);
        assertEquals(2,p.packages.size());
    }
    @Test public void excludeAndAllAllowEmpty() {
        new LiteSplitPolicy(0,Collections.<String>emptySet()).validate(SELF);
        new LiteSplitPolicy(2,Collections.<String>emptySet()).validate(SELF);
    }
    @Test public void invalidModeIsRejected() {
        try { new LiteSplitPolicy(3,Collections.<String>emptySet()); fail("invalid"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected); }
    }
    @Test public void invalidPackagesAreNotPersisted() {
        LiteSplitPolicy p=new LiteSplitPolicy(2,new HashSet<>(Arrays.asList("com.example.ok","bad;cmd","",SELF)));
        assertEquals(2,p.packages.size());
        assertFalse(p.packages.contains("bad;cmd"));
    }
}
