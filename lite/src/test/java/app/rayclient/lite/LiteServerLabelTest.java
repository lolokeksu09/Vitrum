package app.rayclient.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiteServerLabelTest {
    @Test public void usesRemarkAndProtocol() {
        assertEquals("VLESS · Amsterdam", LiteServerLabel.format("vless", "Amsterdam", "vpn.example"));
    }
    @Test public void fallsBackToHostWithoutExposingCredentials() {
        assertEquals("TROJAN · vpn.example", LiteServerLabel.format("trojan", "", "vpn.example"));
    }
    @Test public void removesControlCharactersAndBoundsLabelLength() {
        assertEquals("VMESS · A B C", LiteServerLabel.format("vmess", "A\nB\tC", "host"));
        String label=LiteServerLabel.format("ss", "x".repeat(120), "host");
        assertTrue(label.length() <= 56);
    }
}
