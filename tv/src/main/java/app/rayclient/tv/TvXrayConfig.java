package app.rayclient.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Minimal independent Xray config: Android VpnService owns TUN; no direct fallback. */
final class TvXrayConfig {
    private TvXrayConfig() {}

    static String build(TvServerProfile profile) throws JSONException {
        JSONObject user = new JSONObject()
            .put("id", profile.uuid)
            .put("encryption", "none");
        if (!profile.flow.isEmpty()) user.put("flow", profile.flow);

        JSONObject endpoint = new JSONObject()
            .put("address", profile.server)
            .put("port", profile.port)
            .put("users", new JSONArray().put(user));

        JSONObject transportSecurity;
        if ("reality".equals(profile.security)) {
            transportSecurity = new JSONObject()
                .put("serverName", profile.sni)
                .put("fingerprint", profile.fingerprint)
                .put("publicKey", profile.publicKey)
                .put("shortId", profile.shortId)
                .put("show", false);
        } else {
            transportSecurity = new JSONObject()
                .put("serverName", profile.sni)
                .put("allowInsecure", false);
        }

        JSONObject streamSettings = new JSONObject()
            .put("network", "tcp")
            .put("security", profile.security)
            .put("tcpSettings", new JSONObject())
            .put("reality".equals(profile.security) ? "realitySettings" : "tlsSettings",
                transportSecurity);

        JSONObject outbound = new JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", new JSONObject().put("vnext",
                new JSONArray().put(endpoint)))
            .put("streamSettings", streamSettings);

        JSONObject tunInbound = new JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put("settings", new JSONObject().put("mtu", 1500));

        return new JSONObject()
            .put("log", new JSONObject().put("loglevel", "warning"))
            .put("inbounds", new JSONArray().put(tunInbound))
            .put("outbounds", new JSONArray().put(outbound))
            .put("routing", new JSONObject().put("domainStrategy", "IPIfNonMatch")
                .put("rules", new JSONArray()))
            .toString();
    }
}
