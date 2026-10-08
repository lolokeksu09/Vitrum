package app.rayclient

import java.net.URI

/** Метаданные подписки из заголовков ответа панели. */
class SubMeta(val intervalH: Int, val refill: Long, val page: String, val newUrl: String?, val fallback: String?)

object SubMetaParser {
    /** Метаданные, которые принимаются из комментариев тела подписки (`#ключ: значение`), когда заголовка нет. Маршрутизация (routing) сюда не входит. */
    val COMMENT_KEYS = setOf("subscription-userinfo", "profile-title", "announce", "support-url", "profile-update-interval", "subscription-refill-date",
        "profile-web-page-url", "new-url", "new-domain", "fallback-url")

    /**
     * Строки `#ключ: значение` из текста подписки (ключ без пробелов, из [COMMENT_KEYS]). Заголовок ответа важнее: комментарии читают только как запасной источник.
     * JSON-тело не разбирается. Просматриваются первые 2000 строк, длинные значения отбрасываются.
     */
    fun comments(text: String): Map<String, String> {
        val t = text.trimStart('\uFEFF').trim()
        if (t.startsWith("[") || t.startsWith("{")) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (raw in t.lineSequence().take(2000)) {
            val l = raw.trim()
            if (!l.startsWith("#")) continue
            val i = l.indexOf(':')
            if (i < 2) continue
            val k = l.substring(1, i).trim().lowercase(); val v = l.substring(i + 1).trim()
            if (k.isEmpty() || ' ' in k || v.isEmpty() || v.length > 4000 || k !in COMMENT_KEYS) continue
            out.putIfAbsent(k, v)
        }
        return out
    }

    private val hostRe = Regex("^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$")

    /** Принимается только https-адрес с нормальным именем хоста, без логина и пароля в адресе. */
    fun httpsUrl(s: String): String? = runCatching {
        val u = URI(s.trim())
        val h = u.host?.lowercase()
        if (u.scheme == "https" && h != null && u.userInfo == null && hostRe.matches(h)) u.toString() else null
    }.getOrNull()

    /** Запасной адрес должен вести в интернет: адрес устройства, локальной сети и IP-литерал не принимаются. */
    fun publicHost(url: String): Boolean {
        val h = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return '.' in h && !h.all { it.isDigit() || it == '.' } && h != "localhost" && !h.endsWith(".localhost") && !h.endsWith(".local") && !h.endsWith(".internal") && !h.endsWith(".lan")
    }

    fun sameUrl(a: String, b: String) = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

    /**
     * get — чтение заголовка по имени (пусто, если его нет). Переезд подписки (new-url или new-domain) только предлагается пользователю,
     * сам он ничего не меняет. Запасной адрес (fallback-url) используется, только если основной не отвечает.
     */
    fun parse(get: (String) -> String, currentUrl: String): SubMeta {
        val interval = get("profile-update-interval").trim().toIntOrNull()?.takeIf { it in 1..168 } ?: 0
        val refill = get("subscription-refill-date").trim().toLongOrNull()?.takeIf { it in 1_500_000_000L..5_000_000_000L } ?: 0L
        val page = httpsUrl(get("profile-web-page-url")) ?: ""
        val cur = runCatching { URI(currentUrl) }.getOrNull()
        var newUrl = httpsUrl(get("new-url"))
        if (newUrl == null && cur != null) {
            val d = get("new-domain").trim().lowercase()
            if (hostRe.matches(d) && '.' in d && d != cur.host?.lowercase())
                newUrl = httpsUrl("https://" + d + (if (cur.port > 0) ":${cur.port}" else "") + (cur.rawPath ?: "") + (cur.rawQuery?.let { "?$it" } ?: ""))
        }
        if (newUrl != null && sameUrl(newUrl, currentUrl)) newUrl = null
        val fallback = httpsUrl(get("fallback-url"))?.takeIf { !sameUrl(it, currentUrl) && publicHost(it) }
        return SubMeta(interval, refill, page, newUrl, fallback)
    }
}
