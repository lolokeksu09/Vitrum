package app.rayclient.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiteProfileStoreTest {
    @Test public void acceptsSupportedEncryptedProfileSource() {
        LiteProfileStore.validate("https://subscription.invalid/path?token=fake");
        LiteProfileStore.validate("vless://test-not-real");
    }

    @Test public void rejectsInsecureOrOversizedSource() {
        expectInvalid(() -> LiteProfileStore.validate("http://subscription.invalid/key"));
        expectInvalid(() -> LiteProfileStore.validate("file:///tmp/secret"));
        expectInvalid(() -> LiteProfileStore.validate("x".repeat(8193)));
        expectInvalid(() -> LiteProfileStore.validate(""));
    }

    @Test public void requiresSingleServerForQuickConnect() {
        LiteProfileStore.validateSelected("trojan://test-not-real");
        expectInvalid(() -> LiteProfileStore.validateSelected("https://subscription.invalid"));
        expectInvalid(() -> LiteProfileStore.validateSelected("http://subscription.invalid"));
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); fail("Expected rejected profile"); }
        catch (IllegalArgumentException expected) { /* allow */ }
    }
}
