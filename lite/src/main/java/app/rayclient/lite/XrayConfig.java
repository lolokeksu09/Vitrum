package app.rayclient.lite;

import android.net.Uri;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.Locale;

final class XrayConfig {
    private XrayConfig() {}

    static Profile parse(String link) {
        if (link == null || link.trim().length() > 8192) throw new IllegalArgumentException("Ссылка пустая или слишком длинная");
        Uri uri = Uri.parse(link.trim());
        String type = uri.getScheme();
        if (!"vless".equals(type) && !"trojan".equals(type))
            throw new IllegalArgumentException("Lite поддерживает ссылки VLESS и Trojan");
        String host = uri.getHost();
        int port = uri.getPort();
        String user = uri.getUserInfo();
        if (TextUtils.isEmpty(host) || port <= 0 || port > 65535 || TextUtils.isEmpty(user))
            throw new IllegalArgumentException("Неверный адрес, порт или ключ сервера");
        if (host.contains("/") || host.contains("@") || host.contains("\\"))
            throw new IllegalArgumentException("Неверное имя сервера");
        String security = param(uri, "security", "none");
        if (!"tls".equals(security) && !"reality".equals(security))
            throw new IllegalArgumentException("Для теста требуется TLS или Reality");
        String transport = param(uri, "type", "tcp");
        if (!"tcp".equals(transport) && !"ws".equals(transport))
            throw new IllegalArgumentException("В Lite доступны TCP и WebSocket; другой транспорт не поддерживается");
        String id = Uri.decode(user);
        if ("vless".equals(type) && !id.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
            throw new IllegalArgumentException("Неверный UUID сервера");
        if ("reality".equals(security) && TextUtils.isEmpty(param(uri, "pbk", "")))
            throw new IllegalArgumentException("Reality: нужен публичный ключ pbk");
        if (TextUtils.isEmpty(param(uri, "sni", "")) && TextUtils.isEmpty(param(uri, "host", "")))
            throw new IllegalArgumentException("Не указан SNI или host для TLS");
        return new Profile(type, host, port, id, security, transport, link.trim());
    }

    static String generate(Profile p) throws Exception {
        Uri uri = Uri.parse(p.link);
        // Resolve before establishing the VPN, so Xray's outbound does not depend on DNS inside its own tunnel.
        InetAddress[] all = InetAddress.getAllByName(p.host);
        if (all.length == 0) throw new IllegalStateException("Не удалось определить адрес сервера");
        String serverIp = all[0].getHostAddress();
        for (InetAddress a : all) if (a.getAddress().length == 4) { serverIp = a.getHostAddress(); break; }
        JSONObject stream = new JSONObject().put("network", p.transport).put("security", p.security);
        String sni = param(uri, "sni", param(uri, "host", p.host));
        if ("reality".equals(p.security)) {
            stream.put("realitySettings", new JSONObject()
                .put("serverName", sni).put("fingerprint", param(uri, "fp", "chrome"))
                .put("publicKey", param(uri, "pbk", "")).put("shortId", param(uri, "sid", ""))
                .put("spiderX", param(uri, "spx", "/")));
        } else {
            stream.put("tlsSettings", new JSONObject()
                .put("serverName", sni).put("fingerprint", param(uri, "fp", "chrome"))
                .put("allowInsecure", false));
        }
        if ("ws".equals(p.transport)) {
            JSONObject ws = new JSONObject().put("path", param(uri, "path", "/"));
            String webHost = param(uri, "host", "");
            if (!webHost.isEmpty()) ws.put("headers", new JSONObject().put("Host", webHost));
            stream.put("wsSettings", ws);
        }
        JSONObject outbound = new JSONObject().put("tag", "proxy").put("protocol", p.type).put("streamSettings", stream);
        if ("vless".equals(p.type)) {
            JSONObject u = new JSONObject().put("id", p.credential).put("encryption", "none");
            String flow = param(uri, "flow", "");
            if (!flow.isEmpty()) u.put("flow", flow);
            outbound.put("settings", new JSONObject().put("vnext", new JSONArray().put(
                new JSONObject().put("address", serverIp).put("port", p.port)
                    .put("users", new JSONArray().put(u)))));
        } else {
            outbound.put("settings", new JSONObject().put("servers", new JSONArray().put(
                new JSONObject().put("address", serverIp).put("port", p.port)
                    .put("password", p.credential))));
        }
        JSONObject tun = new JSONObject().put("tag", "tun-in").put("protocol", "tun")
                .put("settings", new JSONObject().put("name", "lite0").put("MTU", 1500));
        return new JSONObject().put("log", new JSONObject().put("loglevel", "warning").put("access", "none"))
            .put("inbounds", new JSONArray().put(tun))
            .put("outbounds", new JSONArray().put(outbound)
                .put(new JSONObject().put("tag", "direct").put("protocol", "freedom")))
            .toString();
    }

    private static String param(Uri uri, String key, String fallback) {
        String value = uri.getQueryParameter(key);
        return value == null ? fallback : value.trim();
    }

    static final class Profile {
        final String type, host, credential, security, transport, link;
        final int port;
        Profile(String t, String h, int p, String c, String sec, String trans, String l) {
            type=t; host=h; port=p; credential=c; security=sec; transport=trans; link=l;
        }
    }
}
