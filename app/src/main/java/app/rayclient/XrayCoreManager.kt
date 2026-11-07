package app.rayclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

internal data class XrayRelease(
    val tag: String,
    val prerelease: Boolean,
    val publishedAt: String,
    val archiveUrl: String,
    val digestUrl: String
)

internal data class XrayInstall(
    val tag: String,
    val binaryPath: String,
    val binarySha256: String,
    val archiveSha256: String,
    val sizeBytes: Long
)

internal data class XrayLaunch(
    val tag: String,
    val version: String,
    val executablePath: String,
    val linkerPath: String?
) {
    val bundled: Boolean get() = linkerPath == null
}

internal object XrayCoreManager {
    const val API_URL = "https://api.github.com/repos/XTLS/Xray-core/releases?per_page=30"
    const val ARCHIVE_NAME = "Xray-android-arm64-v8a.zip"
    const val DIGEST_NAME = "$ARCHIVE_NAME.dgst"
    const val LINKER64 = "/system/bin/linker64"
    const val MAX_RELEASES_JSON = 2 * 1024 * 1024
    const val MAX_DIGEST_BYTES = 32 * 1024
    const val MAX_ARCHIVE_BYTES = 80L * 1024 * 1024
    const val MAX_BINARY_BYTES = 120L * 1024 * 1024
    const val MIN_BINARY_BYTES = 1024L

    private val tagPattern = Regex("""^v?\d{1,3}\.\d{1,3}\.\d{1,3}(?:-[0-9A-Za-z.-]+)?$""")
    private val shaPattern = Regex("""(?i)\b[0-9a-f]{64}\b""")

    fun bundled(ctx: Context): XrayLaunch = XrayLaunch(
        tag = "bundled",
        version = XRAY_VERSION,
        executablePath = xrayPath(ctx),
        linkerPath = null
    )

    fun resolve(ctx: Context, selectedTag: String?): XrayLaunch {
        if (selectedTag.isNullOrBlank()) return bundled(ctx)
        val installed = findInstalled(ctx, selectedTag)
        require(installed != null) { "Выбранная версия Xray не установлена или не прошла проверку; используется встроенное ядро" }
        return XrayLaunch(
            tag = installed.tag,
            version = installed.tag.removePrefix("v"),
            executablePath = installed.binaryPath,
            linkerPath = LINKER64
        )
    }

    fun knownExecutablePaths(ctx: Context): List<String> =
        (listOf(xrayPath(ctx)) + installed(ctx).map { it.binaryPath }).distinct()

    fun installed(ctx: Context): List<XrayInstall> {
        val root = coreRoot(ctx)
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir ->
            runCatching {
                val meta = File(dir, "meta.json")
                require(meta.isFile)
                val json = JSONObject(meta.readText(Charsets.UTF_8))
                val tag = json.getString("tag")
                require(validTag(tag))
                val binary = File(dir, "xray")
                val binaryHash = json.getString("binarySha256").lowercase()
                val archiveHash = json.getString("archiveSha256").lowercase()
                require(shaPattern.matches(binaryHash) && shaPattern.matches(archiveHash))
                require(binary.isFile && binary.length() in MIN_BINARY_BYTES..MAX_BINARY_BYTES)
                require(sha256(binary) == binaryHash)
                require(isArm64Elf(binary))
                XrayInstall(tag, binary.absolutePath, binaryHash, archiveHash, binary.length())
            }.getOrNull()
        }.sortedWith { a, b ->
            compareVersionParts(versionParts(b.tag), versionParts(a.tag)).takeIf { it != 0 }
                ?: a.tag.compareTo(b.tag)
        }
    }

    fun fetchReleases(): List<XrayRelease> {
        val body = httpGetBytes(API_URL, MAX_RELEASES_JSON, "application/vnd.github+json")
        val array = JSONArray(String(body, Charsets.UTF_8))
        val results = ArrayList<XrayRelease>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false)) continue
            val tag = item.optString("tag_name")
            if (!validTag(tag)) continue
            val assets = item.optJSONArray("assets") ?: continue
            var archive: String? = null
            var digest: String? = null
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                when (asset.optString("name")) {
                    ARCHIVE_NAME -> archive = safeAssetUrl(asset.optString("browser_download_url"))
                    DIGEST_NAME -> digest = safeAssetUrl(asset.optString("browser_download_url"))
                }
            }
            if (archive == null || digest == null) continue
            results += XrayRelease(
                tag = tag,
                prerelease = item.optBoolean("prerelease", false),
                publishedAt = item.optString("published_at"),
                archiveUrl = archive!!,
                digestUrl = digest!!
            )
        }
        return results.sortedWith { a, b ->
            compareVersionParts(versionParts(b.tag), versionParts(a.tag)).takeIf { it != 0 }
                ?: a.tag.compareTo(b.tag)
        }
    }

    fun install(ctx: Context, release: XrayRelease): XrayInstall {
        require(validTag(release.tag)) { "Некорректная версия Xray" }
        require(safeAssetUrl(release.archiveUrl) != null && safeAssetUrl(release.digestUrl) != null) {
            "Разрешены только HTTPS assets официального GitHub XTLS/Xray-core"
        }

        val digestBytes = httpGetBytes(release.digestUrl, MAX_DIGEST_BYTES)
        val expectedArchiveHash = parseDigest(String(digestBytes, Charsets.UTF_8))
        val root = coreRoot(ctx).apply { mkdirs() }
        val archive = File(root, "download-${release.tag}.zip.tmp")
        try {
            val actualArchiveHash = downloadToFile(release.archiveUrl, archive, MAX_ARCHIVE_BYTES)
            require(actualArchiveHash == expectedArchiveHash) {
                "SHA-256 архива Xray не совпал с официальным .dgst"
            }

            val tempBinary = File(root, "xray-${release.tag}.tmp")
            try {
                extractBinary(archive, tempBinary)
                require(isArm64Elf(tempBinary)) { "Архив не содержит совместимый ELF ARM64 (AArch64)" }
                require(tempBinary.setExecutable(true, true) || tempBinary.canExecute()) {
                    "Не удалось выставить исполняемый флаг Xray"
                }
                val binaryHash = sha256(tempBinary)
                val finalDir = File(root, "${release.tag}-${actualArchiveHash.take(12)}")
                if (!finalDir.exists()) require(finalDir.mkdirs()) { "Не удалось создать папку версии Xray" }
                val finalBinary = File(finalDir, "xray")
                val staged = File(finalDir, "xray.tmp")
                tempBinary.copyTo(staged, overwrite = true)
                require(staged.setExecutable(true, true) || staged.canExecute())
                syncFile(staged)
                if (finalBinary.exists()) require(finalBinary.delete()) { "Не удалось заменить временный Xray" }
                require(staged.renameTo(finalBinary)) { "Не удалось атомарно установить Xray" }

                val meta = JSONObject()
                    .put("v", 1)
                    .put("tag", release.tag)
                    .put("archiveSha256", actualArchiveHash)
                    .put("binarySha256", binaryHash)
                    .put("sizeBytes", finalBinary.length())
                    .put("abi", "arm64-v8a")
                    .put("installedAt", System.currentTimeMillis())
                val metaTmp = File(finalDir, "meta.json.tmp")
                FileOutputStream(metaTmp).use { out ->
                    out.write(meta.toString().toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                val metaFile = File(finalDir, "meta.json")
                if (metaFile.exists()) require(metaFile.delete())
                require(metaTmp.renameTo(metaFile)) { "Не удалось сохранить метаданные Xray" }

                // Verify the system linker can load and run this core before exposing it to the selector.
                verifyExecutable(finalBinary, release.tag)
                return XrayInstall(
                    release.tag, finalBinary.absolutePath, binaryHash, actualArchiveHash, finalBinary.length()
                )
            } finally {
                tempBinary.delete()
            }
        } finally {
            archive.delete()
        }
    }

    fun delete(ctx: Context, tag: String): Boolean {
        if (!validTag(tag) || tag == XRAY_VERSION) return false
        val matches = coreRoot(ctx).listFiles()?.filter { dir ->
            dir.isDirectory && runCatching {
                JSONObject(File(dir, "meta.json").readText(Charsets.UTF_8)).optString("tag") == tag
            }.getOrDefault(false)
        }.orEmpty()
        return matches.all { it.deleteRecursively() }
    }

    internal fun validTag(tag: String): Boolean = tagPattern.matches(tag)

    internal fun parseDigest(text: String): String {
        val line = text.lineSequence().firstOrNull {
            it.contains("sha256", ignoreCase = true) || it.contains("sha2-256", ignoreCase = true)
        } ?: error("В .dgst нет SHA-256")
        return shaPattern.find(line)?.value?.lowercase() ?: error("Некорректная SHA-256 в .dgst")
    }

    internal fun isArm64Elf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(20)
            var offset = 0
            while (offset < head.size) {
                val n = input.read(head, offset, head.size - offset)
                if (n < 0) return false
                offset += n
            }
            head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte() &&
                head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte() &&
                head[4] == 2.toByte() && head[5] == 1.toByte() &&
                (head[18].toInt() and 0xff) == 183 && (head[19].toInt() and 0xff) == 0
        }
    }.getOrDefault(false)

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyExecutable(binary: File, tag: String) {
        require(File(LINKER64).canExecute()) { "На устройстве нет системного linker64" }
        val process = ProcessBuilder(LINKER64, binary.absolutePath, "version")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(8, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("Xray не ответил на проверку версии за 8 секунд")
        }
        val output = process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.take(4096)
        require(process.exitValue() == 0 && output.contains(tag.removePrefix("v"))) {
            "Проверка версии Xray не подтвердила ${tag}: ${output.take(220)}"
        }
    }

    private fun extractBinary(archive: File, output: File) {
        var found = false
        var written = 0L
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xray" && !entry.isDirectory) {
                    require(!found) { "В архиве больше одного файла xray" }
                    FileOutputStream(output).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            written += n
                            require(written <= MAX_BINARY_BYTES) { "Бинарник Xray превышает лимит размера" }
                            out.write(buffer, 0, n)
                        }
                        out.fd.sync()
                    }
                    found = true
                }
                zip.closeEntry()
            }
        }
        require(found && written in MIN_BINARY_BYTES..MAX_BINARY_BYTES) {
            "В архиве нет допустимого бинарника xray"
        }
    }

    private fun downloadToFile(url: String, file: File, maxBytes: Long): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", AppState.userAgent())
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            require(connection.responseCode == 200) { "GitHub не отдал архив Xray: HTTP ${connection.responseCode}" }
            val length = connection.contentLengthLong
            require(length == -1L || length in 1..maxBytes) { "Архив Xray больше допустимого размера" }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= maxBytes) { "Архив Xray больше допустимого размера" }
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                    out.fd.sync()
                }
            }
            require(total > 0L) { "Пустой архив Xray" }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetBytes(url: String, limit: Int, accept: String = "application/octet-stream"): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", AppState.userAgent())
        connection.setRequestProperty("Accept", accept)
        try {
            require(connection.responseCode == 200) { "GitHub ответил HTTP ${connection.responseCode}" }
            val length = connection.contentLength
            require(length == -1 || length in 1..limit) { "Ответ GitHub превышает допустимый размер" }
            return connection.inputStream.use { input -> readLimited(input, limit) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size() + n <= limit) { "Ответ GitHub превышает допустимый размер" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    private fun safeAssetUrl(raw: String): String? = runCatching {
        val url = URL(raw)
        if (url.protocol != "https" || url.host.lowercase() != "github.com") null else url.toString()
    }.getOrNull()

    private fun coreRoot(ctx: Context) = File(ctx.filesDir, "xray-cores")

    private fun findInstalled(ctx: Context, tag: String): XrayInstall? =
        installed(ctx).firstOrNull { it.tag == tag }

    private fun versionParts(tag: String): List<Int> =
        tag.removePrefix("v").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    internal fun compareVersionParts(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun syncFile(file: File) {
        FileOutputStream(file, true).use { it.fd.sync() }
    }
}
