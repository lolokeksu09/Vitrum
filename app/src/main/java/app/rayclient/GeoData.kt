package app.rayclient

import android.content.Context
import android.system.Os
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Свежая база IP для России (geoip.dat проекта runetfreedom, ≈18 МБ). Она добавляет метку geoip:ru-whitelist («белые» IP мобильных сетей)
 * и точнее определяет российские адреса. Скачивается по желанию, проверяется по контрольной сумме из релиза и пробным запуском Xray.
 * Базу доменов (geosite) берём встроенную: версия runetfreedom весит 73 МБ, а нужных меток в ней не больше.
 */
object GeoData {
    const val BASE = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/"
    private const val MIN = 1_000_000L
    private const val MAX = 120_000_000L

    fun dir(ctx: Context) = File(ctx.filesDir, "geo-ru")
    fun ready(ctx: Context) = File(dir(ctx), "geoip.dat").let { it.exists() && it.length() > MIN } && File(dir(ctx), "geosite.dat").exists()

    /** Каталог баз для Xray: скачанный, если включён и исправен, иначе встроенный. */
    fun assetDir(ctx: Context): String = if (AppState.ruGeo && ready(ctx)) dir(ctx).absolutePath else ctx.filesDir.absolutePath

    fun parseSha(text: String): String? = Regex("""\b[0-9a-fA-F]{64}\b""").find(text)?.value?.lowercase()

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val buf = ByteArray(65536); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "нужен https" }
        return (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 60000 }
    }

    /** Скачивает url в dest через временный файл и сверяет SHA-256 с файлом shaUrl. Возвращает текст ошибки или null. */
    fun fetchVerified(url: String, shaUrl: String, dest: File): String? = try {
        val expected = parseSha(open(shaUrl).inputStream.use { it.readNBytesCompat(1024).toString(Charsets.UTF_8) }) ?: return "нет контрольной суммы"
        val tmp = File(dest.path + ".part")
        var total = 0L
        open(url).inputStream.use { ins -> tmp.outputStream().use { out ->
            val buf = ByteArray(65536)
            while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > MAX) error("файл слишком большой"); out.write(buf, 0, n) }
        } }
        when {
            total < MIN -> { tmp.delete(); "файл слишком маленький" }
            sha256(tmp) != expected -> { tmp.delete(); "контрольная сумма не совпала" }
            else -> { dest.delete(); if (tmp.renameTo(dest)) null else "не удалось сохранить файл" }
        }
    } catch (e: Exception) { "${e.javaClass.simpleName}: ${e.message ?: ""}".trim() }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray { val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(256); var t = 0; while (t < max) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n); t += n }; return out.toByteArray() }

    /** Пробный запуск Xray с этой базой: если файл битый или без нужных меток, процесс сразу завершится. */
    private fun validate(ctx: Context): Boolean {
        val cfg = File(ctx.filesDir, "geo-test.json").apply { writeText("""{"routing":{"rules":[{"type":"field","ip":["geoip:ru-whitelist","geoip:ru"],"outboundTag":"direct"},{"type":"field","domain":["geosite:category-ru"],"outboundTag":"direct"}]},"outbounds":[{"tag":"direct","protocol":"freedom"}]}""") }
        val log = File(ctx.filesDir, "geo-test.log").apply { writeText("") }
        var pid = 0
        return try {
            pid = Native.spawn(xrayPath(ctx), cfg.absolutePath, dir(ctx).absolutePath, log.absolutePath, -1)
            var alive = pid > 0; repeat(12) { if (alive) { Thread.sleep(250); alive = Native.alive(pid) } }
            if (!alive) pid = 0   // завершившийся процесс уже убран alive(): его PID трогать нельзя
            alive
        } finally { Native.stop(pid); runCatching { cfg.delete(); log.delete() } }
    }

    /** Скачать, проверить и включить базу. Работает в фоне, итог в AppState.message. */
    fun update(ctx: Context, enableAfter: Boolean = false) {
        if (AppState.ruGeoBusy) return
        AppState.ruGeoBusy = true
        Thread {
            try {
                val d = dir(ctx).apply { mkdirs() }
                val target = File(d, "geoip.dat"); val backup = File(d, "geoip.dat.bak")
                val tmp = File(d, "geoip.dat.new")
                val err = fetchVerified(BASE + "geoip.dat", BASE + "geoip.dat.sha256sum", tmp)
                if (err != null) { AppState.message = "Не удалось скачать базу: $err"; if (enableAfter) AppState.ruGeo = false; return@Thread }
                // сумма из того же релиза не доказывает подлинность: смотрим содержимое меток, которыми пользуется приложение
                val bad = GeoSanity.check(runCatching { tmp.readBytes() }.getOrDefault(ByteArray(0)))
                if (bad != null) { tmp.delete(); AppState.message = "База отклонена: $bad"; if (enableAfter) AppState.ruGeo = false; return@Thread }
                val gs = File(d, "geosite.dat")
                if (!gs.exists()) runCatching { Os.symlink(File(ctx.filesDir, "geosite.dat").absolutePath, gs.absolutePath) }.onFailure { File(ctx.filesDir, "geosite.dat").copyTo(gs, overwrite = true) }
                backup.delete(); if (target.exists()) target.renameTo(backup)
                tmp.renameTo(target)
                if (!validate(ctx)) {
                    target.delete(); if (backup.exists()) backup.renameTo(target)
                    AppState.message = "Xray не принял скачанную базу, оставлена прежняя"; if (enableAfter) AppState.ruGeo = false; return@Thread
                }
                backup.delete()
                AppState.ruGeoAt = System.currentTimeMillis(); if (enableAfter) AppState.ruGeo = true
                AppState.message = if (RayVpnService.connected) "Базы для России обновлены. Они применятся при следующем подключении" else "Базы для России обновлены"
            } catch (e: Exception) { AppState.message = "Не удалось обновить базу: ${e.message ?: e.javaClass.simpleName}"; if (enableAfter) AppState.ruGeo = false }
            finally { AppState.save(); AppState.ruGeoBusy = false }
        }.start()
    }

    fun delete(ctx: Context) { runCatching { dir(ctx).deleteRecursively() }; AppState.ruGeoAt = 0L }
}
