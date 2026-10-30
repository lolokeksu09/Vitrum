package app.rayclient.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiteServerProbeTest {
    @Test public void distinguishesTcpProbeFromUdp() {
        assertEquals("TCP · 19 мс", LiteServerProbe.describe("vless", 19));
        assertEquals("UDP · TCP-проверка недоступна", LiteServerProbe.describe("hysteria2", 19));
        assertFalse(LiteServerProbe.supportsTcp("hysteria2"));
    }
    @Test public void representsUncheckedAndFailuresAccurately() {
        assertEquals("TCP · не проверен", LiteServerProbe.describe("trojan", LiteServerProbe.UNCHECKED));
        assertEquals("TCP · нет ответа", LiteServerProbe.describe("vmess", LiteServerProbe.FAILED));
        assertEquals("TCP · проверяется…", LiteServerProbe.describe("ss", LiteServerProbe.CHECKING));
    }
    @Test public void invalidEndpointFailsWithoutNetworkAccess() {
        assertEquals(LiteServerProbe.FAILED, LiteServerProbe.measureTcp("", 443, 300));
        assertEquals(LiteServerProbe.FAILED, LiteServerProbe.measureTcp("invalid", 0, 300));
    }
}
