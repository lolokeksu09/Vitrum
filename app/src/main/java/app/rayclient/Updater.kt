package app.rayclient

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val version: String, val code: Long, val url: String, val sha256: String, val notes: String)

/** Проверка новой версии по JSON на вашем сервере: {"version":"0.9.0","code":14,"url":"https://…/${BRAND}.apk","sha256":"…","notes":"…"}. */
object Updater {
    /** Result.success(null) — установлена последняя версия. */
    fun check(ctx: Context, url: String): Result<UpdateInfo?> = runCatching {
        require(url.startsWith("https://")) { "нужен адрес https://" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000; c.setRequestProperty("User-Agent", AppState.userAgent())
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val code = j.getLong("code")
        val cur = PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
        val apk = j.getString("url")
        require(apk.startsWith("https://")) { "ссылка на файл в update.json должна начинаться с https://" }   // откроется в браузере: только https
        if (code > cur) UpdateInfo(j.optString("version"), code, apk, j.optString("sha256"), j.optString("notes")) else null
    }

    // ---------- проверка по релизам проекта на GitHub (когда свой адрес update.json не указан) ----------
    const val RELEASES_API = "https://api.github.com/repos/lolokeksu09/Vitrum/releases?per_page=10"
    private const val DOWNLOAD_PREFIX = "https://github.com/lolokeksu09/Vitrum/releases/download/"

    /** "0.25.19" или "v0.25.19" → [0, 25, 19]; null, если это не номер версии. */
    fun parseVersion(s: String): List<Int>? {
        val t = s.trim().removePrefix("v")
        if (!Regex("""^\d{1,4}(\.\d{1,4}){1,3}$""").matches(t)) return null
        return t.split('.').map { it.toInt() }
    }

    fun isNewer(candidate: List<Int>, current: List<Int>): Boolean {
        for (k in 0 until maxOf(candidate.size, current.size)) {
            val a = candidate.getOrElse(k) { 0 }; val b = current.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Выбирает самый новый релиз из ответа GitHub. Файл берётся под подпись установленной сборки: у сборки с ключом автора это `Vitrum-X.Y.Z.apk`,
     * у отладочной `Vitrum-X.Y.Z-debug-fallback.apk` (APK с другой подписью система поверх не поставит). Только файлы из релизов этого репозитория по https.
     * Черновики пропускаются, предварительные выпуски (pre-release) входят.
     */
    fun pick(json: String, currentVersion: String, releaseSigned: Boolean): UpdateInfo? {
        val cur = parseVersion(currentVersion) ?: return null
        val arr = JSONArray(json)
        var best: UpdateInfo? = null; var bestV = cur
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name").removePrefix("v")
            val v = parseVersion(tag) ?: continue
            if (!isNewer(v, bestV)) continue
            val wanted = if (releaseSigned) "$BRAND-$tag.apk" else "$BRAND-$tag-debug-fallback.apk"
            val assets = r.optJSONArray("assets") ?: continue
            for (k in 0 until assets.length()) {
                val a = assets.getJSONObject(k)
                if (a.optString("name") != wanted) continue
                val url = a.optString("browser_download_url")
                if (!url.startsWith(DOWNLOAD_PREFIX)) continue
                best = UpdateInfo(tag, 0L, url, a.optString("digest").removePrefix("sha256:"), r.optString("body")); bestV = v
            }
        }
        return best
    }

    /** Проверка без своего адреса: релизы проекта на GitHub. Запрос идёт только при нажатии «Проверить сейчас» или при включённой автопроверке. */
    fun checkDefault(ctx: Context): Result<UpdateInfo?> = runCatching {
        val c = URL(RELEASES_API).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", AppState.userAgent()); c.setRequestProperty("Accept", "application/vnd.github+json")
        val sig = Integrity.signer(ctx)
        pick(c.inputStream.bufferedReader().readText(), appVersion(ctx) ?: "0", sig != null && Integrity.matches(sig))
    }
}
