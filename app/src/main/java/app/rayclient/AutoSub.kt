package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/**
 * Режим JSON-подписки (D-lite). Панель отдаёт по адресу `<подписка>/json` массив полных конфигов Xray.
 * Конфиги с балансировщиком показываются как «авто-серверы». Из такого конфига берутся только `outbounds`
 * (серверы), `routing.balancers` и `burstObservatory`. Входы, DNS, правила маршрута и служебные выходы
 * (direct, block, dns) в приложении свои.
 */
class AutoPlan(
    val name: String,
    val outbounds: List<JSONObject>,      // только серверы (без freedom/blackhole/dns)
    val balancers: List<JSONObject>,
    val burst: JSONObject?,
) {
    /** Теги балансировщиков: по ним в маршрутах указывается balancerTag. Основной (первый) принимает весь пользовательский трафик. */
    val mainBalancer: String get() = balancers.first().getString("tag")
}

class AutoRef(val key: String, val name: String, val plan: AutoPlan)

object AutoSub {
    const val SCHEME = "vitrum-auto://"
    // служебные выходы панели не берём: свои direct/block/dns у приложения, а loopback ведёт обратно во входы
    private val SERVICE = setOf("freedom", "blackhole", "dns", "loopback")

    /** Адрес самого телефона. Выход панели туда — не сервер, а чужая служба на устройстве: такой выход не берём. */
    fun isLocalAddr(a: String): Boolean {
        val s = a.trim().lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
        // IPv4 внутри IPv6 (::ffff:127.0.0.1 или ::ffff:7f00:1): проверяем как обычный адрес
        val mapped = s.removePrefix("0:0:0:0:0:ffff:").removePrefix("::ffff:")
        if (mapped != s) return if ('.' in mapped) isLocalAddr(mapped) else mapped.substringBefore(':').toIntOrNull(16)?.let { it shr 8 == 127 || it shr 8 == 0 } == true
        return s == "localhost" || s.endsWith(".localhost") || s.startsWith("127.") || s == "0.0.0.0" ||
            s == "::" || s == "::1" || s == "0:0:0:0:0:0:0:1"
    }

    fun isAuto(link: String) = link.startsWith(SCHEME)

    /** Адрес JSON-режима: путь подписки + /json, запрос сохраняется, фрагмент (#...) не отправляется. */
    fun jsonUrl(subUrl: String): String? = runCatching {
        val u = URI(subUrl.trim())
        if (u.scheme != "https" || u.host == null) return null
        val path = (u.rawPath ?: "").trimEnd('/') + "/json"
        "https://" + u.rawAuthority + path + (u.rawQuery?.let { "?$it" } ?: "")
    }.getOrNull()

    /** Стабильная ссылка авто-сервера: по ней работают избранное, выбор и история. Зависит от подписки и названия конфига. */
    fun linkOf(subId: String, name: String, index: Int = 0) =
        SCHEME + subId + "/" + URLEncoder.encode(name, "UTF-8") + (if (index > 0) "~$index" else "")

    class Parsed(val configs: List<AutoRef>, val skipped: Int)

    /** Разбирает ответ `/json`. Берёт конфиги, в которых есть балансировщик и хотя бы один сервер. Остальные считаются обычными серверами и пропускаются. */
    fun parse(body: String, subId: String): Parsed {
        require(maxDepth(body) <= MAX_DEPTH) { "ответ слишком глубоко вложен" }
        val arr = JSONArray(body)
        val out = ArrayList<AutoRef>(); val used = HashMap<String, Int>(); var skipped = 0
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val bal = c.optJSONObject("routing")?.optJSONArray("balancers")
            if (bal == null || bal.length() == 0) continue
            val plan = runCatching { plan(c) }.getOrNull()
            if (plan == null) { skipped++; continue }
            val n = used.merge(plan.name, 1, Int::plus)!! - 1
            out += AutoRef(linkOf(subId, plan.name, n), plan.name, plan)
        }
        return Parsed(out, skipped)
    }

    private fun plan(c: JSONObject): AutoPlan {
        val name = c.optString("remarks").trim().ifEmpty { "Авто-сервер" }
        val obs = c.getJSONArray("outbounds")
        val src = c.getJSONObject("routing").getJSONArray("balancers")
        // Xray читает ключи без учёта регистра, org.json различает: «Protocol» рядом с «protocol» увёл бы трафик мимо проверок приложения
        require(!ambiguous(obs) && !ambiguous(src) && !ambiguous(c.optJSONObject("burstObservatory"))) { "ключи конфига неоднозначны" }
        val servers = ArrayList<JSONObject>()
        for (i in 0 until obs.length()) {
            val o = obs.getJSONObject(i)
            // регистр не важен: «Freedom» не должен пройти как сервер и увести трафик мимо VPN
            if (o.optString("protocol").lowercase() in SERVICE) continue
            if (addressNodes(o).any { isLocalAddr(it.optString("address")) }) continue
            servers += cleanOutbound(o)
        }
        require(servers.isNotEmpty()) { "нет серверов" }
        val tags = servers.map { it.optString("tag") }
        require(tags.none { it.isEmpty() } && tags.toSet().size == tags.size) { "теги выходов пусты или повторяются" }
        require(tags.none { it in RESERVED_TAGS }) { "тег сервера совпадает со служебным" }
        // выход, который ссылается на другой выход (цепочка), без самого выхода работать не будет
        servers.forEach { o ->
            val ref = o.optJSONObject("proxySettings")?.optString("tag")
                ?: o.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.optString("dialerProxy")
            require(ref.isNullOrEmpty() || ref in tags) { "выход ссылается на отсутствующий выход" }
        }
        val bals = (0 until src.length()).map { cleanBalancer(src.getJSONObject(it)) }
        require(bals.map { it.getString("tag") }.toSet().size == bals.size) { "теги балансировщиков повторяются" }
        for (b in bals) {
            val sel = b.getJSONArray("selector")
            val picked = tags.filter { t -> (0 until sel.length()).any { t.startsWith(sel.getString(it)) } }
            require(picked.isNotEmpty()) { "селектор балансировщика не находит серверов" }
            // Запасной выход панели — direct: пока нет замеров или все серверы лежат, трафик ушёл бы мимо VPN. Берём первый выбранный сервер.
            b.put("fallbackTag", picked.first())
        }
        val burst = c.optJSONObject("burstObservatory")?.let { cleanBurst(it) }
        return AutoPlan(name, servers, bals, burst)
    }

    // ---------- очистка данных панели: берём только известные поля ----------

    /** Служебные теги приложения: сервер панели с таким тегом (или селектор, под который он подходит) увёл бы трафик в direct или block. */
    private val RESERVED_TAGS = setOf("direct", "block", "dns-out", "fragment", "tun-in", "probe-in", "dns-internal")
    /** Ключи, которые приложение читает или чистит: их вариант в другом регистре считается подменой. */
    private val GUARDED_KEYS = listOf("protocol", "settings", "vnext", "servers", "address", "tag", "streamSettings", "sockopt", "dialerProxy",
        "proxySettings", "selector", "fallbackTag", "strategy", "type", "tlsSettings", "certificates", "masterKeyLog", "interface", "mark", "tproxy",
        "customSockopt", "sendThrough", "pingConfig", "connectivity", "subjectSelector", "destination", "interval", "sampling", "timeout")
    private val GUARDED_LC = GUARDED_KEYS.associateBy { it.lowercase() }
    private val OUT_KEYS = listOf("tag", "protocol", "settings", "streamSettings", "mux", "proxySettings", "targetStrategy", "domainStrategy")
    private val STRATEGIES = setOf("random", "roundRobin", "leastPing", "leastLoad")
    private val DURATION = Regex("""^(\d{1,4})([smh])$""")
    private val DEST = Regex("""^https?://[A-Za-z0-9.\-]{1,253}(:\d{1,5})?(/[^\s]*)?$""")
    const val MAX_DEPTH = 40
    /** Замер серверов не чаще, чем раз в 30 секунд: панель не должна делать из телефона источник частых запросов. */
    const val MIN_INTERVAL_SEC = 30

    /** Наибольшая вложенность скобок в тексте (строки пропускаются): глубокий JSON роняет разбор до того, как его можно проверить. */
    internal fun maxDepth(body: String): Int {
        var d = 0; var m = 0; var str = false; var esc = false
        for (ch in body) {
            if (str) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') str = false; continue }
            when (ch) { '"' -> str = true; '[', '{' -> { d++; if (d > m) m = d }; ']', '}' -> d-- }
        }
        return m
    }

    /** Есть ли объект с двумя ключами, отличающимися регистром, или с охраняемым ключом в нестандартном регистре. */
    internal fun ambiguous(v: Any?, depth: Int = 0): Boolean {
        if (depth > MAX_DEPTH) return true
        when (v) {
            is JSONObject -> {
                val keys = v.keys().asSequence().toList()
                if (keys.map { it.lowercase() }.toSet().size != keys.size) return true
                if (keys.any { k -> GUARDED_LC[k.lowercase()]?.let { it != k } == true }) return true
                return keys.any { ambiguous(v.opt(it), depth + 1) }
            }
            is JSONArray -> return (0 until v.length()).any { ambiguous(v.opt(it), depth + 1) }
        }
        return false
    }

    /** Выход панели без лишнего: интерфейс, метка, исходящий адрес, сертификаты и журнал ключей TLS не нужны серверу и не берутся. */
    internal fun cleanOutbound(o: JSONObject): JSONObject {
        val r = JSONObject()
        for (k in OUT_KEYS) if (o.has(k)) r.put(k, o.get(k).let { v -> if (v is JSONObject || v is JSONArray) copyOf(v) else v })
        val ss = r.optJSONObject("streamSettings")
        ss?.optJSONObject("sockopt")?.let { so -> listOf("interface", "mark", "tproxy", "customSockopt").forEach { so.remove(it) } }
        ss?.optJSONObject("tlsSettings")?.let { t -> listOf("certificates", "masterKeyLog").forEach { t.remove(it) } }
        ss?.optJSONObject("realitySettings")?.remove("masterKeyLog")
        return r
    }

    private fun copyOf(v: Any): Any = if (v is JSONObject) JSONObject(v.toString()) else JSONArray(v.toString())

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun checkSelector(sel: List<String>) {
        require(sel.isNotEmpty() && sel.none { it.isBlank() }) { "селектор пуст" }
        require(sel.none { s -> RESERVED_TAGS.any { it.startsWith(s) } }) { "селектор подходит под служебный выход" }
    }

    /** Балансировщик только из известных полей: tag, selector, strategy (известный тип), fallbackTag ставится позже. */
    internal fun cleanBalancer(b: JSONObject): JSONObject {
        val tag = b.optString("tag"); require(tag.isNotEmpty()) { "у балансировщика нет тега" }
        val sel = strings(b.optJSONArray("selector")); checkSelector(sel)
        val r = JSONObject().put("tag", tag).put("selector", JSONArray(sel))
        b.optJSONObject("strategy")?.let { if (it.optString("type") in STRATEGIES) r.put("strategy", JSONObject(it.toString())) }
        return r
    }

    /** Замер серверов: свой список меток, адрес проверки http(s) без лишнего, интервал не меньше 30 с; connectivity (прямой запрос) не берём. */
    internal fun cleanBurst(o: JSONObject): JSONObject? {
        val sel = strings(o.optJSONArray("subjectSelector"))
        runCatching { checkSelector(sel) }.onFailure { return null }
        val pc = o.optJSONObject("pingConfig") ?: return null
        val dest = pc.optString("destination"); if (!DEST.matches(dest)) return null
        fun secs(v: String) = DURATION.matchEntire(v)?.let { m -> m.groupValues[1].toInt() * when (m.groupValues[2]) { "m" -> 60; "h" -> 3600; else -> 1 } }
        val iv = pc.optString("interval").let { v -> if ((secs(v) ?: 0) >= MIN_INTERVAL_SEC) v else "1m" }
        val ping = JSONObject().put("destination", dest).put("interval", iv)
        pc.optInt("sampling", 0).takeIf { it in 1..100 }?.let { ping.put("sampling", it) }
        pc.optString("timeout").takeIf { secs(it) != null }?.let { ping.put("timeout", it) }
        return JSONObject().put("subjectSelector", JSONArray(sel)).put("pingConfig", ping)
    }

    // ---------- адреса серверов ----------

    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private fun isIp(s: String) = ipv4.matches(s) || ':' in s

    /** Все адреса-домены в выходах плана (то, что надо заранее превратить в IP). */
    fun domains(plan: AutoPlan): Set<String> = plan.outbounds.flatMap { addressesOf(it) }.filter { !isIp(it) }.toSet()

    private fun addressNodes(o: JSONObject): List<JSONObject> {
        val st = o.optJSONObject("settings") ?: return emptyList()
        val l = ArrayList<JSONObject>()
        st.optJSONArray("vnext")?.let { for (i in 0 until it.length()) l += it.getJSONObject(i) }
        st.optJSONArray("servers")?.let { for (i in 0 until it.length()) l += it.getJSONObject(i) }
        if (st.has("address")) l += st
        return l
    }

    private fun addressesOf(o: JSONObject) = addressNodes(o).map { it.optString("address") }.filter { it.isNotEmpty() }

    /**
     * Подставляет IP вместо домена (Xray в приложении сам имена серверов не резолвит). Чтобы не сломать SNI и заголовок Host,
     * имя сервера переносится в serverName (tls, reality) и host (xhttp, ws, httpupgrade), если там пусто.
     * Выход с неразрешённым доменом выбрасывается; null, если не осталось ни одного сервера.
     */
    fun pinAddresses(plan: AutoPlan, resolve: (String) -> String?): AutoPlan? {
        val kept = ArrayList<JSONObject>()
        for (src in plan.outbounds) {
            val o = JSONObject(src.toString()); var ok = true
            for (n in addressNodes(o)) {
                val a = n.optString("address"); if (a.isEmpty() || isIp(a)) continue
                // домен, который разрешился в адрес самого телефона, тоже не сервер
                val ip = resolve(a); if (ip == null || isLocalAddr(ip)) { ok = false; break }
                n.put("address", ip); keepName(o, a)
            }
            if (ok) kept += o
        }
        if (kept.isEmpty()) return null
        val tags = kept.map { it.getString("tag") }
        // балансировщик, у которого не осталось серверов, не нужен; запасной выход должен остаться в списке
        val bals = ArrayList<JSONObject>()
        for (b0 in plan.balancers) {
            val b = JSONObject(b0.toString())
            val sel = b.optJSONArray("selector") ?: JSONArray()
            val picked = tags.filter { t -> (0 until sel.length()).any { t.startsWith(sel.getString(it)) } }
            if (picked.isEmpty()) continue
            if (b.optString("fallbackTag") !in tags) b.put("fallbackTag", picked.first())
            bals += b
        }
        if (bals.isEmpty()) return null
        return AutoPlan(plan.name, kept, bals, plan.burst)
    }

    private fun keepName(o: JSONObject, domain: String) {
        val ss = o.optJSONObject("streamSettings") ?: return
        ss.optJSONObject("tlsSettings")?.let { if (it.optString("serverName").isEmpty()) it.put("serverName", domain) }
        ss.optJSONObject("realitySettings")?.let { if (it.optString("serverName").isEmpty()) it.put("serverName", domain) }
        ss.optJSONObject("xhttpSettings")?.let { if (it.optString("host").isEmpty()) it.put("host", domain) }
        ss.optJSONObject("httpupgradeSettings")?.let { if (it.optString("host").isEmpty()) it.put("host", domain) }
        ss.optJSONObject("wsSettings")?.let { w ->
            val h = w.optJSONObject("headers") ?: JSONObject().also { w.put("headers", it) }
            if (w.optString("host").isEmpty() && h.optString("Host").isEmpty()) h.put("Host", domain)
        }
    }

    // ---------- хранение и разрешение имён ----------

    fun toJson(plan: AutoPlan): JSONObject = JSONObject().put("name", plan.name)
        .put("outbounds", JSONArray(plan.outbounds.map { JSONObject(it.toString()) }))
        .put("balancers", JSONArray(plan.balancers.map { JSONObject(it.toString()) }))
        .apply { plan.burst?.let { put("burst", JSONObject(it.toString())) } }

    fun fromJson(o: JSONObject): AutoPlan = AutoPlan(o.getString("name"),
        o.getJSONArray("outbounds").let { a -> (0 until a.length()).map { a.getJSONObject(it) } },
        o.getJSONArray("balancers").let { a -> (0 until a.length()).map { a.getJSONObject(it) } },
        o.optJSONObject("burst"))

    /** Резолвит имена параллельно; не уложившиеся в срок или не найденные в результат не попадают. */
    fun resolveAll(names: Set<String>, timeoutMs: Long, lookup: (String) -> String?): Map<String, String> {
        val res = java.util.concurrent.ConcurrentHashMap<String, String>()
        val ts = names.map { n -> Thread { runCatching { lookup(n) }.getOrNull()?.let { res[n] = it } }.apply { isDaemon = true; start() } }
        val end = System.currentTimeMillis() + timeoutMs
        ts.forEach { t -> t.join((end - System.currentTimeMillis()).coerceAtLeast(1)) }
        return HashMap(res)
    }
}
