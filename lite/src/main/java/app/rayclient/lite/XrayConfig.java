package app.rayclient.lite;

import android.net.Uri;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Deliberately limited protocol-to-Xray adapter. Never silently switch transport/security. */
final class XrayConfig {
    private XrayConfig() {}

    private static String param(Uri uri, String key, String fallback) {
        String value = uri.getQueryParameter(key);
        return value == null ? fallback : value.trim();
    }
    private static int port(String raw) {
        int p;
        try { p = Integer.parseInt(raw); } catch (Exception e) { throw new IllegalArgumentException("Неверный порт сервера"); }
        if (p < 1 || p > 65535) throw new IllegalArgumentException("Неверный порт сервера");
        return p;
    }
    private static String host(String value) {
        if (TextUtils.isEmpty(value) || value.length() > 255 || value.contains("/") || value.contains("@") || value.contains("\\"))
            throw new IllegalArgumentException("Неверный адрес сервера");
        return value;
    }
    private static String credential(String value) {
        if (TextUtils.isEmpty(value) || value.length() > 2048) throw new IllegalArgumentException("Пустой ключ сервера");
        return value;
    }
    static boolean supported(String link) {
        return link != null && SubscriptionParser.supportedLink(link.trim());
    }

    static Profile parse(String link) {
        if (link == null || link.trim().isEmpty() || link.length() > 8192)
            throw new IllegalArgumentException("Ссылка пустая или слишком длинная");
        String raw = link.trim();
        String proto = raw.substring(0, raw.indexOf(':')).toLowerCase(java.util.Locale.ROOT);
        if (!supported(raw)) throw new IllegalArgumentException("Неподдерживаемый протокол сервера");
        if ("vmess".equals(proto)) {
            String encoded = raw.substring("vmess://".length()).split("#", 2)[0];
            JSONObject data;
            try { data = new JSONObject(new String(SubscriptionParser.decodeBase64(encoded), StandardCharsets.UTF_8)); }
            catch (Exception e) { throw new IllegalArgumentException("Неверная VMess-ссылка"); }
            String host = host(data.optString("add", ""));
            int port = port(data.opt("port") == null ? "" : data.opt("port").toString());
            String id = credential(data.optString("id", ""));
            if (!id.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$"))
                throw new IllegalArgumentException("Некорректный VMess UUID");
            // Validate stream before including in chooser.
            stream(data.optString("net", "tcp"), "tls".equals(data.optString("tls", "")) ? "tls" : "none");
            return new Profile("vmess", host, port, id, raw, data);
        }
        if ("ss".equals(proto)) {
            String body = raw.substring(5).split("#",2)[0].split("\\?",2)[0];
            if (body.isEmpty()) throw new IllegalArgumentException("Пустая Shadowsocks-ссылка");
            Uri uri = Uri.parse(raw);
            if (!param(uri, "plugin", "").isEmpty()) throw new IllegalArgumentException("Shadowsocks с plugin не поддерживается");
            String target;
            if (!body.contains("@")) {
                try { target = new String(SubscriptionParser.decodeBase64(body), StandardCharsets.UTF_8); }
                catch (Exception e) { throw new IllegalArgumentException("Неверная SS-ссылка"); }
            } else {
                String[] parts = body.split("@",2);
                String cred = Uri.decode(parts[0]);
                if (!cred.contains(":")) cred = new String(SubscriptionParser.decodeBase64(cred), StandardCharsets.UTF_8);
                target = cred + "@" + parts[1];
            }
            int at = target.lastIndexOf('@');
            if (at <= 0) throw new IllegalArgumentException("Нет данных сервера SS");
            String auth = target.substring(0, at), endpoint = target.substring(at + 1);
            int separator = auth.indexOf(':');
            if (separator < 1) throw new IllegalArgumentException("Нет метода Shadowsocks");
            String method = auth.substring(0, separator);
            if (!Arrays.asList("aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "xchacha20-poly1305").contains(method))
                throw new IllegalArgumentException("Метод Shadowsocks не поддерживается: " + method);
            Uri address = Uri.parse("ss://" + endpoint);
            return new Profile("ss", host(address.getHost()), port(String.valueOf(address.getPort())), credential(auth.substring(separator+1)), raw,
                new JSONObject().put("method", method));
        }
        Uri uri = Uri.parse(raw);
        String type = "hy2".equals(proto) ? "hysteria2" : proto;
        String h = host(uri.getHost());
        int p = port(String.valueOf(uri.getPort()));
        String user = credential(uri.getUserInfo() == null ? "" : Uri.decode(uri.getUserInfo()));
        if ("hysteria2".equals(type)) {
            String obfs = param(uri,"obfs","");
            if (!obfs.isEmpty() && !"salamander".equals(obfs))
                throw new IllegalArgumentException("Тип Hysteria2 obfs не поддерживается");
            if ("salamander".equals(obfs) && param(uri,"obfs-password","").isEmpty())
                throw new IllegalArgumentException("Для Hysteria2 obfs нужен пароль");
        } else {
            if ("vless".equals(type) && !user.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$"))
                throw new IllegalArgumentException("Неверный VLESS UUID");
            String sec = param(uri,"security", "trojan".equals(type) ? "tls" : "none");
            String transport = param(uri,"type","tcp");
            stream(transport, sec);
            if ("reality".equals(sec) && param(uri,"pbk","").isEmpty())
                throw new IllegalArgumentException("Reality: отсутствует pbk");
        }
        return new Profile(type, h, p, user, raw, null);
    }

    /** Xray stream options that are supported by the pinned Xray 26.3.27. */
    private static void stream(String network, String security) {
        if (!Arrays.asList("tcp","ws","grpc","httpupgrade","xhttp").contains(network))
            throw new IllegalArgumentException("Транспорт " + network + " не поддерживается");
        if (!Arrays.asList("tls","reality","none").contains(security))
            throw new IllegalArgumentException("Защита " + security + " не поддерживается");
    }

    static String generate(Profile p) throws Exception {
        InetAddress[] all = InetAddress.getAllByName(p.host);
        if (all.length == 0) throw new IllegalStateException("Не удалось определить адрес сервера");
        String ip = all[0].getHostAddress();
        for (InetAddress candidate : all) if (candidate.getAddress().length == 4) { ip = candidate.getHostAddress(); break; }
        JSONObject outbound = new JSONObject().put("tag","proxy");
        if ("vmess".equals(p.type)) {
            JSONObject data = p.vmess;
            JSONObject user = new JSONObject().put("id",p.credential)
                .put("alterId",data.optInt("aid",0)).put("security",data.optString("scy","auto"));
            outbound.put("protocol","vmess")
                .put("settings",new JSONObject().put("vnext",new JSONArray().put(new JSONObject()
                    .put("address",ip).put("port",p.port).put("users",new JSONArray().put(user)))));
            Uri placeholder = Uri.parse("vmess://placeholder");
            JSONObject stream = streamOptions(data.optString("net","tcp"),
                "tls".equals(data.optString("tls",""))?"tls":"none",
                data.optString("sni",data.optString("host",p.host)),data.optString("host",""),
                data.optString("path","/"), data.optString("fp","chrome"),
                "", "", "",data.optString("path",""),"", "");
            outbound.put("streamSettings",stream);
        } else if ("ss".equals(p.type)) {
            outbound.put("protocol","shadowsocks")
                .put("settings",new JSONObject().put("servers",new JSONArray().put(
                    new JSONObject().put("address",ip).put("port",p.port)
                        .put("method",p.vmess.getString("method")).put("password",p.credential))));
        } else if ("hysteria2".equals(p.type)) {
            Uri u = Uri.parse(p.link);
            JSONObject stream = new JSONObject().put("network","hysteria").put("security","tls")
                .put("tlsSettings",new JSONObject().put("serverName",param(u,"sni",p.host))
                    .put("alpn",new JSONArray().put("h3")).put("enableSessionResumption",false))
                .put("hysteriaSettings",new JSONObject().put("version",2).put("auth",p.credential));
            if ("salamander".equals(param(u,"obfs",""))) stream.put("finalmask",new JSONObject()
                .put("udp",new JSONArray().put(new JSONObject().put("type","salamander")
                .put("settings",new JSONObject().put("password",param(u,"obfs-password",""))))));
            outbound.put("protocol","hysteria")
                .put("settings",new JSONObject().put("version",2).put("address",ip).put("port",p.port))
                .put("streamSettings",stream);
        } else {
            Uri u = Uri.parse(p.link);
            String sec = param(u,"security", "trojan".equals(p.type)?"tls":"none");
            JSONObject stream = streamOptions(param(u,"type","tcp"),sec,
                param(u,"sni",p.host),param(u,"host",""),param(u,"path","/"),param(u,"fp","chrome"),
                param(u,"pbk",""),param(u,"sid",""),param(u,"spx","/"),
                param(u,"serviceName",""),param(u,"mode",""),param(u,"alpn",""));
            outbound.put("protocol",p.type).put("streamSettings",stream);
            if ("vless".equals(p.type)) {
                JSONObject user = new JSONObject().put("id",p.credential).put("encryption","none");
                String flow=param(u,"flow","");
                if (!flow.isEmpty()) user.put("flow",flow);
                outbound.put("settings",new JSONObject().put("vnext",new JSONArray().put(new JSONObject()
                    .put("address",ip).put("port",p.port).put("users",new JSONArray().put(user)))));
            } else {
                outbound.put("settings",new JSONObject().put("servers",new JSONArray().put(new JSONObject()
                    .put("address",ip).put("port",p.port).put("password",p.credential))));
            }
        }
        JSONObject tun=new JSONObject().put("tag","tun-in").put("protocol","tun")
            .put("settings",new JSONObject().put("name","lite0").put("MTU",1500));
        return new JSONObject().put("log",new JSONObject().put("loglevel","warning").put("access","none"))
            .put("inbounds",new JSONArray().put(tun))
            .put("outbounds",new JSONArray().put(outbound).put(new JSONObject()
                .put("tag","direct").put("protocol","freedom"))).toString();
    }

    private static JSONObject streamOptions(String net,String sec,String sni,String host,String path,String fp,
        String pbk,String sid,String spx,String serviceName,String mode,String alpn) throws Exception {
        stream(net,sec);
        JSONObject st=new JSONObject().put("network",net).put("security",sec);
        if ("ws".equals(net)) st.put("wsSettings",new JSONObject().put("path",path)
            .put("headers",new JSONObject().put("Host",host.isEmpty()?sni:host)));
        else if ("grpc".equals(net)) st.put("grpcSettings",new JSONObject()
            .put("serviceName",serviceName).put("multiMode","multi".equals(mode)));
        else if ("httpupgrade".equals(net)) st.put("httpupgradeSettings",new JSONObject()
            .put("path",path).put("host",host.isEmpty()?sni:host));
        else if ("xhttp".equals(net)) st.put("xhttpSettings",new JSONObject()
            .put("path",path).put("host",host.isEmpty()?sni:host)
            .put("mode",mode.isEmpty()?"auto":mode));
        if ("tls".equals(sec)) {
            JSONObject tls=new JSONObject().put("serverName",sni).put("fingerprint",fp)
                .put("allowInsecure",false);
            if (!alpn.isEmpty()) tls.put("alpn",new JSONArray(Arrays.asList(alpn.split(","))));
            st.put("tlsSettings",tls);
        }
        if ("reality".equals(sec)) st.put("realitySettings",new JSONObject()
            .put("serverName",sni).put("fingerprint",fp).put("publicKey",pbk)
            .put("shortId",sid).put("spiderX",spx));
        return st;
    }
    static final class Profile {
        final String type,host,credential,link;
        final int port;
        final JSONObject vmess;
        Profile(String type,String host,int port,String credential,String link,JSONObject vmess) {
            this.type=type;this.host=host;this.port=port;this.credential=credential;
            this.link=link;this.vmess=vmess;
        }
    }
}
