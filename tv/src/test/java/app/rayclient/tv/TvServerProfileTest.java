package app.rayclient.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TvServerProfileTest {
    private static final String UUID = "11111111-1111-4111-8111-111111111111";
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String REALITY = "vless://" + UUID +
        "@vpn.example.org:443?security=reality&type=tcp&encryption=none"
        + "&sni=www.example.org&pbk=" + KEY + "&sid=abcd&fp=chrome"
        + "&flow=xtls-rprx-vision";

    @Test public void parsesSupportedReality() {
        TvServerProfile result = TvServerProfile.parse(REALITY);
        assertEquals("vpn.example.org", result.server);
        assertEquals(443, result.port);
        assertEquals("reality", result.security);
        assertEquals("www.example.org", result.sni);
        assertEquals(KEY, result.publicKey);
        assertEquals("abcd", result.shortId);
        assertEquals("xtls-rprx-vision", result.flow);
    }

    @Test public void parsesSupportedTls() {
        TvServerProfile result = TvServerProfile.parse("vless://" + UUID +
            "@vpn.example.org:443?security=tls&type=tcp&sni=www.example.org");
        assertEquals("tls", result.security);
        assertEquals("", result.publicKey);
    }

    @Test public void rejectsUnsupportedProtocolAndTransport() {
        reject("vmess://" + UUID + "@vpn.example.org:443?security=reality&sni=www.example.org");
        reject(REALITY.replace("type=tcp", "type=grpc"));
        reject(REALITY.replace("security=reality", "security=none"));
        reject(REALITY.replace("flow=xtls-rprx-vision", "flow=unknown"));
    }

    @Test public void rejectsInvalidOrDuplicateSecurityParameters() {
        reject(REALITY.replace(KEY, "badkey"));
        reject(REALITY.replace("sid=abcd", "sid=abc"));
        reject(REALITY + "&sni=attacker.example");
        reject(REALITY + "&allowInsecure=1");
    }

    @Test public void rejectsMissingAndMalformedIdentity() {
        reject(REALITY.replace(UUID, "invalid-id"));
        reject(REALITY.replace("vpn.example.org", "invalid..host"));
        reject(REALITY.replace("&sni=www.example.org", ""));
        reject(REALITY.replace("443?", "0?"));
        reject(REALITY + "&cmd=;reboot");
        reject(null);
    }

    private static void reject(String value) {
        try {
            TvServerProfile.parse(value);
            fail("Unsupported input was accepted");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
