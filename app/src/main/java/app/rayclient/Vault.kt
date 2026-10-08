package app.rayclient

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Шифрование AES-GCM. Формат: "v1:<iv>:<шифртекст>" (base64). Любая подмена данных ломает расшифровку. */
object Crypt {
    private val enc = Base64.getEncoder(); private val dec = Base64.getDecoder()

    fun encrypt(key: SecretKey, plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
        return "v1:" + enc.encodeToString(c.iv) + ":" + enc.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** null — строка не в нашем формате, ключ не подходит или данные изменены. */
    fun decrypt(key: SecretKey, s: String): String? = runCatching {
        val p = s.split(':'); if (p.size != 3 || p[0] != "v1") return null
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, dec.decode(p[1])))
        String(c.doFinal(dec.decode(p[2])), Charsets.UTF_8)
    }.getOrNull()
}

/** Ключ хранится в Android Keystore и не покидает его: ссылки серверов и подписок лежат на диске только в зашифрованном виде. */
object Vault {
    private const val ALIAS = "vitrum_data"

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }.getOrNull()

    /** null — Keystore недоступен: открытым текстом не храним, вызывающий оставляет прежние данные на диске. */
    fun seal(plain: String): String? = key()?.let { runCatching { Crypt.encrypt(it, plain) }.getOrNull() }

    /** Расшифровывает; старые данные без шифрования читает как есть (перешифруются при ближайшем сохранении).
     *  null — расшифровать не удалось: данные на диске трогать нельзя, иначе пустой список затрёт их. */
    fun open(stored: String): String? = openWith(stored) { s -> key()?.let { Crypt.decrypt(it, s) } }

    internal fun openWith(stored: String, decrypt: (String) -> String?): String? = if (!stored.startsWith("v1:")) stored else decrypt(stored)
}
