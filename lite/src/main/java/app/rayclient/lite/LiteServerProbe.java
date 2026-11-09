package app.rayclient.lite;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Bounded TCP connect time, not ICMP or a protocol-level VPN health check. */
final class LiteServerProbe {
    static final int UNCHECKED = -3;
    static final int CHECKING = -2;
    static final int FAILED = -1;

    private LiteServerProbe() {}

    static boolean supportsTcp(String type) {
        return !"hysteria2".equals(type);
    }

    static String describe(String type, int millis) {
        if (!supportsTcp(type)) return "UDP · TCP-проверка недоступна";
        if (millis == UNCHECKED) return "TCP · не проверен";
        if (millis == CHECKING) return "TCP · проверяется…";
        if (millis == FAILED) return "TCP · нет ответа";
        return "TCP · " + Math.max(1, millis) + " мс";
    }

    static int measureTcp(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535)
            return FAILED;
        int bounded = Math.max(200, Math.min(2000, timeoutMs));
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), bounded);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            return (int)Math.max(1L, Math.min(120_000L, elapsed));
        } catch (Exception e) {
            return FAILED;
        }
    }
}
