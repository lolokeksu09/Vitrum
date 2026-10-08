package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

class ParsedLink(
    val name: String, val proto: String, val host: String, val port: Int,
    val outbound: JSONObject,
)

object Links {
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    /** Пароль, UUID и прочее до «@»: обычное процентное декодирование. Правило «+ значит пробел» только для форм:
     *  URLDecoder превращал «pa+ss» в «pa ss», и серверы с «+» в пароле не подключались. */
    private fun decUser(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun b64(s: String): String {
        val t = s.trim().replace('-', '+').replace('_', '/')
        val padded = t + "=".repeat((4 - t.length % 4) % 4)
        return String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }

    private fun query(q: String) = q.split('&').filter { it.contains('=') }
        .associate { dec(it.substringBefore('=')) to dec(it.substringAfter('=')) }

    private fun hostPort(hp: String): Pair<String, Int> =
        if (hp.startsWith("[")) hp.substringAfter('[').substringBefore(']') to hp.substringAfter("]:").trim('/').toInt()
        else hp.substringBeforeLast(':') to hp.substringAfterLast(':').trim('/').toInt()

    /** Общая часть: network + security для TCP-подобных протоколов. */
    private fun stream(q: Map<String, String>, host: String, defaultSecurity: String): JSONObject {
        val network = when (val n = q["type"] ?: "tcp") { "ws", "grpc", "httpupgrade", "xhttp", "tcp" -> n; "h2", "http" -> "xhttp"; else -> "tcp" }
        val security = q["security"] ?: defaultSecurity
        val s = JSONObject().put("network", network).put("security", security)
        val hostHdr = q["host"]?.takeIf { it.isNotEmpty() } ?: host
        when (network) {
            "ws" -> s.put("wsSettings", JSONObject().put("path", q["path"] ?: "/")
                .put("headers", JSONObject().put("Host", hostHdr)))
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"] ?: "")
                .put("multiMode", q["mode"] == "multi"))
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", q["path"] ?: "/").put("host", hostHdr))
            "xhttp" -> s.put("xhttpSettings", JSONObject().put("path", q["path"] ?: "/")
                .put("mode", q["mode"] ?: "auto").put("host", hostHdr)
                .apply { q["extra"]?.takeIf { it.startsWith("{") }?.let { e -> runCatching { put("extra", JSONObject(e)) } } })
        }
        when (security) {
            "tls" -> s.put("tlsSettings", JSONObject()
                .put("serverName", q["sni"] ?: hostHdr).put("fingerprint", q["fp"] ?: "chrome")
                .apply {
                    q["alpn"]?.takeIf { it.isNotEmpty() }?.let { put("alpn", JSONArray(it.split(','))) }
                    q["pcs"]?.takeIf { it.isNotEmpty() }?.let { put("pinnedPeerCertSha256", JSONArray(it.split(','))) }
                })
            "reality" -> s.put("realitySettings", JSONObject()
                .put("serverName", q["sni"] ?: host).put("fingerprint", q["fp"] ?: "chrome")
                .put("publicKey", q["pbk"] ?: error("В ссылке нет pbk (Reality)"))
                .put("shortId", q["sid"] ?: "").put("spiderX", q["spx"] ?: ""))
        }
        return s
    }

    private fun ob(proto: String, settings: JSONObject, stream: JSONObject?) =
        JSONObject().put("tag", "proxy").put("protocol", proto).put("settings", settings)
            .apply { stream?.let { put("streamSettings", it) } }

    fun parse(raw: String): ParsedLink {
        val link = raw.trim()
        return when {
            link.startsWith("vless://") -> vless(link)
            link.startsWith("vmess://") -> vmess(link)
            link.startsWith("trojan://") -> trojan(link)
            link.startsWith("ss://") -> ss(link)
            link.startsWith("hy2://") || link.startsWith("hysteria2://") -> hy2(link)
            else -> error("Неизвестный формат ссылки")
        }
    }

    /** Описание сервера из параметра serverDescription (панель присылает его только известным ей клиентам): обычный текст или base64. */
    fun serverDescription(link: String): String? = runCatching {
        val q = link.substringAfter('?', "").substringBefore('#'); if (q.isEmpty()) return null
        val raw = query(q)["serverDescription"]?.trim().orEmpty(); if (raw.isEmpty()) return null
        val decoded = runCatching { b64(raw) }.getOrNull()?.takeIf { d -> d.isNotBlank() && d.none { it.isISOControl() || it == '\uFFFD' } }
        (decoded ?: raw).replace(Regex("\\s+"), " ").trim().take(120).ifBlank { null }
    }.getOrNull()

    fun isSupported(s: String) = listOf("vless://", "vmess://", "trojan://", "ss://", "hy2://", "hysteria2://").any { s.trim().startsWith(it) }

    private fun split(link: String, scheme: String): Triple<String, String, String> { // body, query, name
        var rest = link.removePrefix(scheme)
        val name = if ('#' in rest) dec(rest.substringAfter('#')).also { rest = rest.substringBefore('#') } else ""
        val qs = if ('?' in rest) rest.substringAfter('?').also { rest = rest.substringBefore('?') } else ""
        return Triple(rest, qs, name)
    }

    private fun vless(link: String): ParsedLink {
        val (body, qs, nm) = split(link, "vless://")
        val q = query(qs)
        val uuid = decUser(body.substringBefore('@'))
        val (host, port) = hostPort(body.substringAfter('@'))
        val user = JSONObject().put("id", uuid).put("encryption", q["encryption"] ?: "none")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        val st = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))))
        return ParsedLink(nm.ifEmpty { host }, "vless", host, port, ob("vless", st, stream(q, host, "none")))
    }

    private fun vmess(link: String): ParsedLink {
        val j = JSONObject(b64(link.removePrefix("vmess://").substringBefore('#')))
        val host = j.getString("add"); val port = j.get("port").toString().toInt()
        val q = mutableMapOf<String, String>()
        q["type"] = j.optString("net", "tcp"); q["host"] = j.optString("host"); q["path"] = j.optString("path", "/")
        q["security"] = if (j.optString("tls") == "tls") "tls" else "none"
        q["sni"] = j.optString("sni").ifEmpty { j.optString("host") }.ifEmpty { host }
        q["fp"] = j.optString("fp").ifEmpty { "chrome" }; q["alpn"] = j.optString("alpn")
        q["serviceName"] = j.optString("path")
        val user = JSONObject().put("id", j.getString("id")).put("alterId", j.optString("aid", "0").toInt())
            .put("security", j.optString("scy").ifEmpty { "auto" })
        val st = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))))
        return ParsedLink(j.optString("ps").ifEmpty { host }, "vmess", host, port, ob("vmess", st, stream(q, host, "none")))
    }

    private fun trojan(link: String): ParsedLink {
        val (body, qs, nm) = split(link, "trojan://")
        val q = query(qs)
        val pass = decUser(body.substringBefore('@'))
        val (host, port) = hostPort(body.substringAfter('@'))
        val st = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("password", pass)))
        return ParsedLink(nm.ifEmpty { host }, "trojan", host, port, ob("trojan", st, stream(q, host, "tls")))
    }

    private fun ss(link: String): ParsedLink {
        val (body0, qs, nm) = split(link, "ss://")
        var body = body0
        if ('@' !in body) body = b64(body)           // старый формат: всё в base64
        val cred = body.substringBeforeLast('@')
        val credDec = if (':' in cred) decUser(cred) else b64(decUser(cred))
        val method = credDec.substringBefore(':'); val pass = credDec.substringAfter(':')
        val (host, port) = hostPort(body.substringAfterLast('@'))
        val st = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("method", method).put("password", pass)))
        val q = query(qs)
        val s = JSONObject().put("network", "tcp").put("security", "none")
        if (q["plugin"] != null) error("SS с plugin не поддерживается")
        return ParsedLink(nm.ifEmpty { host }, "ss", host, port, ob("shadowsocks", st, s))
    }

    private fun hy2(link: String): ParsedLink {
        val scheme = if (link.startsWith("hy2://")) "hy2://" else "hysteria2://"
        val (body, qs, nm) = split(link, scheme)
        val q = query(qs)
        val auth = decUser(body.substringBeforeLast('@'))
        val (host, port) = hostPort(body.substringAfterLast('@').substringBefore(','))
        val st = JSONObject().put("version", 2).put("address", host).put("port", port)
        val s = JSONObject().put("network", "hysteria").put("security", "tls")
            .put("tlsSettings", JSONObject().put("serverName", q["sni"] ?: host)
                .put("alpn", JSONArray(listOf("h3"))).put("enableSessionResumption", false)
                .apply { q["pcs"]?.takeIf { it.isNotEmpty() }?.let { put("pinnedPeerCertSha256", JSONArray(it.split(','))) } })
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", auth))
        if (q["obfs"] == "salamander") s.put("finalmask", JSONObject().put("udp", JSONArray().put(
            JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", q["obfs-password"] ?: "")))))
        return ParsedLink(nm.ifEmpty { host }, "hysteria2", host, port, ob("hysteria", st, s))
    }

    /** Подставляет IP вместо домена в адресе сервера (чтобы Xray не резолвил его через собственный туннель). */
    fun withAddress(l: ParsedLink, ip: String): JSONObject {
        val o = JSONObject(l.outbound.toString())
        val st = o.getJSONObject("settings")
        st.optJSONArray("vnext")?.getJSONObject(0)?.put("address", ip)
        st.optJSONArray("servers")?.getJSONObject(0)?.put("address", ip)
        if (st.has("address")) st.put("address", ip)
        return o
    }
}
