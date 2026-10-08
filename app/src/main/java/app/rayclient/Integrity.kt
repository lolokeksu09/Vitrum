package app.rayclient

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Подпись самого приложения: отпечаток сертификата и источник установки. Android всё равно не даст поставить обновление с другим ключом, но отпечаток позволяет сверить копию. */
object Integrity {
    /** SHA-256 сертификата ключа выпуска Vitrum (vitrum-release.jks). */
    const val RELEASE_CERT = "2c6afebf27fbbd2b288f8fb51ca6c9a7c8f4c7ce66793aa67cc04350a6f9a9a1"

    @Volatile private var cached: String? = null

    fun fmt(hex: String) = hex.uppercase().chunked(2).joinToString(":")
    fun matches(hex: String?) = hex != null && hex.lowercase() == RELEASE_CERT

    fun signer(ctx: Context): String? = cached ?: signerSha256(ctx).also { cached = it }

    private fun signerSha256(ctx: Context): String? = runCatching {
        val cert = if (Build.VERSION.SDK_INT >= 28) {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return null
            if (info.hasMultipleSigners()) info.apkContentsSigners.firstOrNull() else info.signingCertificateHistory?.lastOrNull()   // последний в истории — текущий
        } else {
            // Android 8–8.1: signingInfo ещё нет, раньше здесь всегда выходило «другая подпись»
            @Suppress("DEPRECATION") val legacy = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            legacy
        }
        cert?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
    }.getOrNull()

    fun installer(ctx: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
    }.getOrNull()
}
