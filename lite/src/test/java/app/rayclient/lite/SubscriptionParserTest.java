package app.rayclient.lite;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.junit.Assert.*;

public class SubscriptionParserTest {
    private static final String VLESS = "vless://abc@one.example:443?security=tls";
    private static final String TROJAN = "trojan://xyz@two.example:443?security=tls";

    @Test public void plaintextDeduplicatesAndIgnoresUnsupportedLines() {
        List<String> result = SubscriptionParser.parse("comment\\n" + VLESS + "\\n" + VLESS + "\\n" + TROJAN + "\\nvmess://ignored");
        assertEquals(2, result.size());
        assertEquals(VLESS, result.get(0));
        assertEquals(TROJAN, result.get(1));
    }
    @Test public void base64DecodesOnAndroid6CompatiblePath() {
        String payload = VLESS + "\\n" + TROJAN;
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, SubscriptionParser.parse(encoded).size());
    }
    @Test public void acceptsUnpaddedUrlSafeBase64() {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(VLESS.getBytes(StandardCharsets.UTF_8));
        assertEquals(VLESS, SubscriptionParser.parse(encoded).get(0));
    }
    @Test public void refusesUnexpectedFormat() {
        try { SubscriptionParser.parse("{\"proxies\": []}"); fail("must reject JSON"); }
        catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
    @Test public void capsServerCount() {
        StringBuilder many = new StringBuilder();
        for (int i=0; i<500; i++) many.append("vless://x@host").append(i).append(":443?security=tls\\n");
        assertEquals(SubscriptionParser.MAX_SERVERS, SubscriptionParser.parse(many.toString()).size());
    }
}
