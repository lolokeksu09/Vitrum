package app.rayclient.lite;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Small, offline subscription format parser. No Android APIs, crypto keys or disk state. */
final class SubscriptionParser {
    static final int MAX_SERVERS = 128;
    private SubscriptionParser() {}

    static List<String> parse(String source) {
        if (source == null || source.trim().isEmpty())
            throw new IllegalArgumentException("Подписка пустая");
        String content = source.trim().replace("\uFEFF", "");
        if (!content.contains("vless://") && !content.contains("trojan://")) {
            try { content = new String(decodeBase64(content), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException ignored) { throw new IllegalArgumentException("Неподдерживаемый формат подписки (нужен список VLESS или Trojan)"); }
        }
        LinkedHashSet<String> links = new LinkedHashSet<>();
        for (String raw : content.split("\\r?\\n")) {
            String line = raw.trim();
            if ((line.startsWith("vless://") || line.startsWith("trojan://")) && line.length() <= 8192) {
                links.add(line);
                if (links.size() >= MAX_SERVERS) break;
            }
        }
        if (links.isEmpty()) throw new IllegalArgumentException("В подписке нет поддерживаемых серверов VLESS/Trojan");
        return new ArrayList<>(links);
    }

    private static byte[] decodeBase64(String input) {
        String compact = input.replaceAll("\\s", "");
        if (compact.length() == 0 || compact.length() % 4 == 1) throw new IllegalArgumentException();
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(compact.length(), 1_048_576));
        int bits = 0, buffer = 0;
        boolean padding = false;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (c == '=') { padding = true; continue; }
            if (padding) throw new IllegalArgumentException();
            int v = c >= 'A' && c <= 'Z' ? c - 'A' :
                c >= 'a' && c <= 'z' ? c - 'a' + 26 :
                c >= '0' && c <= '9' ? c - '0' + 52 :
                c == '+' || c == '-' ? 62 : c == '/' || c == '_' ? 63 : -1;
            if (v < 0) throw new IllegalArgumentException();
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) { bits -= 8; output.write((buffer >>> bits) & 255); }
        }
        return output.toByteArray();
    }
}
