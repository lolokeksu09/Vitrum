package app.rayclient

import androidx.compose.runtime.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// ---------- добавление / подписки ----------
internal fun AppState.addInput(text: String, name: String = "") {
    val t = text.trim()
    if (t.isEmpty()) return
    if (t.startsWith("http://")) { message = "Адрес без шифрования (http) не поддерживается. Нужна ссылка https://"; return }
    if (t.startsWith("https://")) {
        val sub = Sub(UUID.randomUUID().toString(), t, name.ifBlank { runCatching { URL(t).host }.getOrDefault("подписка") }, 0)
        subs += sub; save(); refreshSub(sub)
    } else {
        var ok = 0; var bad = 0
        t.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
            runCatching { val p = Links.parse(l)
                if (servers.any { it.link == l }) { message = "Такие серверы уже есть"; return@forEach }
                servers += Server(UUID.randomUUID().toString(), name.ifBlank { p.name }, l, null); ok++ }
                .onFailure { bad++; message = "Не разобрал ссылку: ${it.message}" }
        }
        if (selectedId == null) servers.firstOrNull()?.let { selectedId = it.id }
        save(); if (ok > 0 && bad == 0) message = "Добавлено: $ok"
    }
}

internal fun AppState.refreshSub(s: Sub, onDone: () -> Unit = {}) {
    Thread { synchronized(AppState) { subBusy++ }; try { fetchSub(s) } finally { synchronized(AppState) { subBusy-- } }; onDone() }.start()
}

/** User-Agent запросов к панели: по нему администратор заводит отдельное правило для Vitrum. Идентификатор устройства панели не передаётся. */
internal fun AppState.userAgent(): String = "Vitrum/" + (appVersion(ctx!!) ?: "0")

/** Обновления подписок идут по одному: параллельные потоки одновременно меняли общий список серверов и выбранный сервер. */
private val subLock = Any()

/** Общий срок на чтение ответа: таймаут чтения действует на каждый кусок, и медленная отдача по байту держала бы замок почти бесконечно. */
private const val RESPONSE_MS = 60_000L

internal fun AppState.fetchSub(s: Sub, urlOverride: String? = null) {
    synchronized(subLock) {
        try {
            val conn = URL(urlOverride ?: s.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", userAgent())
            val ctype = conn.contentType ?: "?"
            val headers = { n: String -> conn.getHeaderField(n)?.trim().orEmpty() }
            fun decB64(v: String) = if (v.startsWith("base64:")) runCatching { String(java.util.Base64.getDecoder().decode(v.removePrefix("base64:"))) }.getOrDefault("") else v
            val body = conn.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0; val until = System.currentTimeMillis() + RESPONSE_MS
                while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > 2_000_000) error("ответ подписки слишком большой"); if (System.currentTimeMillis() > until) error("ответ приходит слишком долго"); out.write(buf, 0, n) }
                out.toString("UTF-8").trim()
            }
            val text = if ("://" in body) body else runCatching {
                val x = body.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/'); String(java.util.Base64.getDecoder().decode(x + "=".repeat((4 - x.length % 4) % 4)))
            }.getOrDefault(body)
            // метаданные: заголовок ответа, а если его нет, комментарий `#ключ: значение` в теле (тот же https-ответ панели, те же проверки)
            val cm = SubMetaParser.comments(text)
            fun hdr(n: String) = headers(n).ifEmpty { cm[n].orEmpty() }
            val info = hdr("subscription-userinfo").split(';').mapNotNull { p -> p.trim().split('=').takeIf { it.size == 2 }?.let { it[0].trim() to (it[1].trim().toLongOrNull() ?: 0L) } }.toMap()
            // адреса поддержки и запасной берём только из заголовков ответа: комментарий в тексте подписки может написать любой автор текста
            val meta = SubMetaParser.parse({ n -> if (n == "fallback-url") headers(n) else hdr(n) }, s.url)
            val title = decB64(hdr("profile-title")); val announce = decB64(hdr("announce")); val support = SubMetaParser.httpsUrl(headers("support-url")) ?: ""; val routingHdr = headers("routing")
            // сервер с той же ссылкой сохраняет id: по нему лежат замеры, а выбранный сервер не теряется
            val known = servers.filter { it.subId == s.id && !it.isAuto }.associate { it.link to it.id }
            val fresh = text.lines().map { it.trim() }.filter { Links.isSupported(it) }.mapNotNull { l ->
                runCatching { Server(known[l] ?: UUID.randomUUID().toString(), Links.parse(l).name, l, s.id) }.getOrNull() }.distinctBy { it.id }
            if (fresh.isEmpty()) { message = "В подписке нет поддерживаемых серверов (тип ответа: $ctype, начало: ${body.take(40).replace('\n', ' ')})"; return@synchronized }
            val keepSel = servers.firstOrNull { it.id == selectedId }?.link
            servers.removeAll { it.subId == s.id && !it.isAuto }; servers += fresh
            // правки пользователя за время загрузки (название, отказ от переезда, JSON-режим) не затираются: берём актуальную запись
            subs.indexOfFirst { it.id == s.id }.let { if (it >= 0) subs[it] = subs[it].let { cur -> cur.copy(
                name = if (title.isNotBlank() && cur.name == runCatching { URL(cur.url).host }.getOrNull()) title else cur.name,
                updated = System.currentTimeMillis(),
                used = (info["upload"] ?: 0) + (info["download"] ?: 0), total = info["total"] ?: 0, expire = info["expire"] ?: 0,
                support = support, announce = announce, routing = routingHdr,
                intervalH = meta.intervalH, refill = meta.refill, page = meta.page, fallback = meta.fallback ?: cur.fallback) } }
            meta.newUrl?.let { if (it != s.declinedUrl) pendingMove = s.id to it }
            if (activeProfile == "sub") subProfile()?.let { loadProfile(it) }
            selectedId = fresh.firstOrNull { it.link == keepSel }?.id ?: selectedId?.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id
            save(); message = "Подписка обновлена: ${fresh.size}"
            HappRouting.parse(routingHdr)?.dropped?.size?.takeIf { it > 0 }?.let { n -> message = "Подписка обновлена: ${fresh.size}. Пропущено слишком широких правил «напрямую»: $n" }
            if (s.json) fetchAuto(s, urlOverride ?: s.url)?.let { n -> message = "Подписка обновлена: ${fresh.size}, авто-серверов: $n" }
        } catch (e: Exception) {
            val fb = s.fallback
            if (urlOverride == null && fb.startsWith("https://")) { message = "Основной адрес недоступен, пробую запасной"; fetchSub(s, fb) } else message = "Ошибка обновления: ${e.message}"
        }
    }
}

/** Читает ответ не больше limit байт (чужой файл не должен забить память и диск). */
private fun readLimited(u: URL, limit: Int): String = (u.openConnection() as HttpURLConnection).run {
    connectTimeout = 15000; readTimeout = 20000
    inputStream.use { ins ->
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0; val until = System.currentTimeMillis() + RESPONSE_MS
        while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > limit) error("файл слишком большой"); if (System.currentTimeMillis() > until) error("ответ приходит слишком долго"); out.write(buf, 0, n) }
        out.toString("UTF-8")
    }
}

internal fun AppState.refreshAllSubs() = subs.toList().forEach { refreshSub(it) }

internal fun AppState.autoUpdate() {
    val day = 24 * 3600 * 1000L
    // сервис может сам попросить обновляться чаще или реже (profile-update-interval, в часах)
    fun periodOf(s: Sub) = (if (s.intervalH > 0) s.intervalH * 60 else updMinutes).coerceAtLeast(15) * 60_000L
    if (autoUpd) subs.toList().filter { System.currentTimeMillis() - it.updated > periodOf(it) }.forEach { refreshSub(it) }
    val wl = File(ctx!!.filesDir, "whitelist.txt")
    if (useWhitelist && System.currentTimeMillis() - wl.lastModified() > day) Thread {
        runCatching { Whitelist.clean(readLimited(URL(WHITELIST_URL), 2_000_000))?.let { wl.writeText(it) } }
    }.start()
    if (presets.any { p -> PRESETS.any { it.id == p && it.asns.isNotEmpty() } }) Thread { AsnPrefixes.refresh(ctx!!, presets.toSet(), force = false) }.start()
    val cm = ctx!!.getSystemService(android.net.ConnectivityManager::class.java)
    if (ruGeo && System.currentTimeMillis() - ruGeoAt > day && !cm.isActiveNetworkMetered) GeoData.update(ctx!!)
}

internal fun AppState.delete(s: Server) { servers.remove(s); if (selectedId == s.id) selectedId = servers.firstOrNull()?.id; save() }

internal fun AppState.deleteSub(s: Sub) { servers.removeAll { it.subId == s.id }; AutoStore.removeSub(context(), s.id); subs.remove(s); if (servers.none { it.id == selectedId }) selectedId = servers.firstOrNull()?.id; save() }


/** Пользователь согласился на переезд подписки: меняем адрес и сразу обновляем. */
internal fun AppState.acceptMove(subId: String, newUrl: String) {
    val i = subs.indexOfFirst { it.id == subId }
    if (i >= 0) { subs[i] = subs[i].copy(url = newUrl, declinedUrl = ""); save(); val s = subs[i]; Thread { fetchSub(s) }.start() }
    pendingMove = null
}

/** Отказ запоминается, чтобы не спрашивать про тот же адрес при каждом обновлении. */
internal fun AppState.declineMove(subId: String, newUrl: String) {
    val i = subs.indexOfFirst { it.id == subId }
    if (i >= 0) { subs[i] = subs[i].copy(declinedUrl = newUrl); save() }
    pendingMove = null
}

// ---------- JSON-подписка (авто-серверы) ----------

/**
 * Читает `<адрес подписки>/json` и добавляет конфиги с балансировщиком как авто-серверы. Возвращает их число
 * или null, если не вышло: тогда прежние авто-серверы остаются как были, а причина показывается сообщением.
 */
internal fun AppState.fetchAuto(s: Sub, baseUrl: String): Int? {
    val url = AutoSub.jsonUrl(baseUrl) ?: run { message = "JSON-режим: нужен адрес https://"; return null }
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", userAgent())
        if (conn.responseCode != 200) { message = "JSON-режим: сервис ответил ${conn.responseCode}"; return null }
        val body = conn.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0; val until = System.currentTimeMillis() + RESPONSE_MS
            while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > 6_000_000) error("ответ слишком большой"); if (System.currentTimeMillis() > until) error("ответ приходит слишком долго"); out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
        val parsed = AutoSub.parse(body, s.id)
        val keepSel = servers.firstOrNull { it.id == selectedId }?.link
        servers.removeAll { it.subId == s.id && it.isAuto }
        AutoStore.replace(ctx!!, s.id, parsed.configs)
        servers.addAll(0, parsed.configs.map { Server(UUID.randomUUID().toString(), it.name, it.key, s.id) }.filter { a -> servers.none { it.link == a.link } })
        selectedId = servers.firstOrNull { it.link == keepSel }?.id ?: selectedId?.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id
        save()
        if (parsed.configs.isEmpty()) message = "В ответе /json нет конфигов с балансировщиком"
        else if (parsed.skipped > 0) message = "Пропущено конфигов, которые приложение не поддерживает: ${parsed.skipped}"
        parsed.configs.size
    } catch (e: Exception) { message = "JSON-режим: ${e.message ?: e.javaClass.simpleName}"; null }
}

/** Включает или выключает JSON-режим подписки. При выключении авто-серверы удаляются вместе с конфигурациями. */
internal fun AppState.setJson(s: Sub, on: Boolean) {
    val i = subs.indexOfFirst { it.id == s.id }; if (i < 0) return
    subs[i] = subs[i].copy(json = on)
    if (!on) {
        servers.removeAll { it.subId == s.id && it.isAuto }; AutoStore.removeSub(context(), s.id)
        if (servers.none { it.id == selectedId }) selectedId = servers.firstOrNull()?.id
    }
    save()
    if (on) refreshSub(subs[i])
}

