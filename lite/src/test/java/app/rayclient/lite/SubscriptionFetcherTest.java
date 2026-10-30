package app.rayclient.lite;

import java.net.URL;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubscriptionFetcherTest {
    @Test public void acceptsHttpsSubscription() throws Exception {
        assertEquals("https", SubscriptionFetcher.requireHttps(new URL("https://example.com/token")).getProtocol());
    }
    @Test public void rejectsHttpAndDowngradeRedirect() throws Exception {
        for (String url : new String[]{"http://example.com/sub", "http://example.com/redirect"}) {
            try { SubscriptionFetcher.requireHttps(new URL(url)); fail("HTTP accepted"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("HTTPS")); }
        }
    }
    @Test public void rejectsPasswordInUrlAuthority() throws Exception {
        try { SubscriptionFetcher.requireHttps(new URL("https://user:pass@example.com/path")); fail("userinfo accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("HTTPS")); }
    }
}
