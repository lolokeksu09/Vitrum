package app.rayclient.tv;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal, strict VLESS+REALITY/TLS TCP profile. Fail closed on protocols or
 * options the independent TV engine has not implemented.
 *
 * This class never logs links, UUIDs, security params or endpoint addresses.
 */
final class TvServerProfile {
    final String server;
    final int port;
    final String uuid;
    final String security;
    final String sni;
    final String publicKey;
    final String shortId;
    final String fingerprint;
    final String flow;

    private TvServerProfile(String server, int port, String uuid, String security,
                            String sni, String publicKey, String shortId,
                            String fingerprint, String flow) {
        this.server = server;
        this.port = port;
        this.uuid = uuid;
        this.security = security;
        this.sni = sni;
        this.publicKey = publicKey;
        this.shortId = shortId;
        this.fingerprint = fingerprint;
        this.flow = flow;
    }

    static TvServerProfile parse(String input) {
        if (input == null || input.length() > 4096 || input.length() < 30) {
            throw new IllegalArgumentException("Invalid profile length");
        }
        URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed profile");
        }
        if (!"vless".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getUserInfo() == null || uri.getUserInfo().contains(":")) {
            throw new IllegalArgumentException("Unsupported VLESS link");
        }
        String id = uri.getUserInfo();
        try {
            if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("Invalid UUID");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID");
        }

        Map<String, String> query = new HashMap<>();
        if (uri.getRawQuery() != null) {
            for (String item : uri.getRawQuery().split("&", -1)) {
                int separator = item.indexOf('=');
                if (separator <= 0) throw new IllegalArgumentException("Invalid query");
                String key;
                String value;
                try {
                    key = URLDecoder.decode(item.substring(0, separator), "UTF-8");
                    value = URLDecoder.decode(item.substring(separator + 1), "UTF-8");
                } catch (java.io.UnsupportedEncodingException error) {
                    throw new IllegalArgumentException("UTF-8 unavailable");
                }
                if (query.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Duplicate query parameter");
                }
            }
        }
        String type = query.getOrDefault("type", "tcp");
        String security = query.getOrDefault("security", "none");
        if (!"tcp".equals(type) || (!"reality".equals(security) && !"tls".equals(security))
                || !"none".equals(query.getOrDefault("encryption", "none"))) {
            throw new IllegalArgumentException("Unsupported protocol or encryption");
        }
        for (String key : query.keySet()) {
            if (!key.equals("type") && !key.equals("security") && !key.equals("encryption")
                && !key.equals("sni") && !key.equals("pbk") && !key.equals("sid")
                && !key.equals("fp") && !key.equals("flow") && !key.equals("spx")) {
                throw new IllegalArgumentException("Unsupported VLESS option");
            }
        }
        if (!query.getOrDefault("spx", "").isEmpty()
                && !"/".equals(query.get("spx"))) {
            throw new IllegalArgumentException("Unsupported spiderX value");
        }
        String sni = query.getOrDefault("sni", "");
        if (!sni.matches("[a-zA-Z0-9.-]{1,253}") || sni.contains("..")
                || sni.startsWith("-") || sni.endsWith("-")) {
            throw new IllegalArgumentException("Invalid server name");
        }
        String flow = query.getOrDefault("flow", "");
        if (!flow.isEmpty() && !"xtls-rprx-vision".equals(flow)) {
            throw new IllegalArgumentException("Unsupported flow");
        }
        String fingerprint = query.getOrDefault("fp", "chrome");
        if (!fingerprint.matches("(chrome|firefox|safari|edge|random)")) {
            throw new IllegalArgumentException("Unsupported TLS fingerprint");
        }
        String key = query.getOrDefault("pbk", "");
        String sid = query.getOrDefault("sid", "");
        if ("reality".equals(security) && (!key.matches("[A-Za-z0-9_-]{43}")
                || !sid.matches("(?i)[0-9a-f]{0,16}")
                || sid.length() % 2 != 0)) {
            throw new IllegalArgumentException("Invalid REALITY key or short ID");
        }
        if ("tls".equals(security) && (!key.isEmpty() || !sid.isEmpty())) {
            throw new IllegalArgumentException("REALITY parameters not allowed for TLS");
        }
        if (uri.getHost().length() > 253 || uri.getHost().contains("..")) {
            throw new IllegalArgumentException("Invalid endpoint");
        }
        return new TvServerProfile(
            uri.getHost(), uri.getPort(), id, security, sni, key, sid,
            fingerprint, flow
        );
    }
}
