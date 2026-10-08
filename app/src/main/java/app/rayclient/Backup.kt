package app.rayclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Файл не от Vitrum, повреждён или версии, которую это приложение не знает. Текст показывается пользователю. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Зашифрованная резервная копия настроек и серверов. Файл: JSON-конверт {magic, v, kdf, it, salt, iv, ct}; ключ из пароля через PBKDF2-HMAC-SHA256,
 * шифрование AES-256-GCM (подмена файла ломает расшифровку), AAD привязывает данные к формату. Внутри: все значения хранилища «ray» (настройки и
 * списки), причём зашифрованные ключом Keystore поля (servers, subs, favs) положены открытыми и защищены уже паролем: Keystore-ключ с телефона не уходит.
 * Не входят: история стабильности, кэш баз, журнал, JSON-режим подписки (восстановится обновлением подписки).
 */
object Backup {
    private const val MAGIC = "vitrum-backup"
    private const val AAD = "vitrum-backup-v1"
    const val ITER = 210_000
    const val MIN_PASSWORD = 8
    private val SEALED = setOf("servers", "subs", "favs", "ssids")
    private val enc = Base64.getEncoder(); private val dec = Base64.getDecoder()

    private fun key(pw: String, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(pw.toCharArray(), salt, iter, 256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }

    /** Значения хранилища в JSON с типами: s строка, b флаг, i число, l длинное, f дробное. Прочие типы пропускаются. */
    internal fun toPayload(values: Map<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in values) {
            val e = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                else -> null
            }
            if (e != null) o.put(k, e)
        }
        return JSONObject().put("app", "vitrum").put("fmt", 1).put("prefs", o).toString()
    }

    internal fun fromPayload(json: String): Map<String, Any> {
        val root = runCatching { JSONObject(json) }.getOrNull()
        if (root == null || root.optString("app") != "vitrum" || root.optInt("fmt") != 1) throw BackupFormatException("Это не резервная копия Vitrum или версия формата неизвестна")
        val p = root.optJSONObject("prefs") ?: throw BackupFormatException("В копии нет данных")
        val out = LinkedHashMap<String, Any>()
        for (k in p.keys()) {
            val e = p.getJSONObject(k); val v = e.get("v")
            out[k] = when (e.getString("t")) {
                "s" -> v as? String ?: throw BackupFormatException("Копия повреждена")
                "b" -> v as? Boolean ?: throw BackupFormatException("Копия повреждена")
                "i" -> (v as? Number)?.toInt() ?: throw BackupFormatException("Копия повреждена")
                "l" -> (v as? Number)?.toLong() ?: throw BackupFormatException("Копия повреждена")
                "f" -> (v as? Number)?.toFloat() ?: throw BackupFormatException("Копия повреждена")
                else -> throw BackupFormatException("Копия повреждена")
            }
        }
        return out
    }

    /** Файл копии из значений. Медленно (PBKDF2): вызывать не на главном потоке. */
    fun seal(values: Map<String, Any?>, password: String, iter: Int = ITER): String {
        val rnd = SecureRandom(); val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(password, salt, iter)); c.updateAAD(AAD.toByteArray())
        val ct = c.doFinal(toPayload(values).toByteArray(Charsets.UTF_8))
        return JSONObject().put("magic", MAGIC).put("v", 1).put("kdf", "pbkdf2-sha256").put("it", iter)
            .put("salt", enc.encodeToString(salt)).put("iv", enc.encodeToString(c.iv)).put("ct", enc.encodeToString(ct)).toString()
    }

    /** Значения из файла. null — неверный пароль или файл изменён. BackupFormatException — это не копия Vitrum. */
    fun open(file: String, password: String): Map<String, Any>? {
        val o = runCatching { JSONObject(file) }.getOrNull()
        if (o == null || o.optString("magic") != MAGIC) throw BackupFormatException("Это не резервная копия Vitrum")
        if (o.optInt("v") != 1 || o.optString("kdf") != "pbkdf2-sha256") throw BackupFormatException("Версия копии не поддерживается этим приложением")
        val iter = o.optInt("it"); if (iter !in 100_000..2_000_000) throw BackupFormatException("Копия повреждена")
        val plain = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, dec.decode(o.getString("salt")), iter), GCMParameterSpec(128, dec.decode(o.getString("iv")))); c.updateAAD(AAD.toByteArray())
            String(c.doFinal(dec.decode(o.getString("ct"))), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return fromPayload(plain)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ray", Context.MODE_PRIVATE)

    /** Ключи, которые копия не восстанавливает: переключатель root-функций после восстановления всегда выключен. */
    private val NEVER_RESTORED = setOf("rootOn")

    fun restorable(key: String): Boolean = key !in NEVER_RESTORED

    /** Текущие значения хранилища; зашифрованные Keystore поля расшифрованы. null — не удалось расшифровать, копию не делаем. */
    fun collect(ctx: Context): Map<String, Any?>? {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in prefs(ctx).all) {
            out[k] = if (k in SEALED && v is String) Vault.open(v) ?: return null else v
        }
        return out
    }

    /** Заменяет хранилище значениями из копии (поля servers, subs, favs шифруются Keystore этого телефона). false — не записалось, старое не тронуто. */
    fun restore(ctx: Context, values: Map<String, Any>): Boolean {
        val e = prefs(ctx).edit().clear()
        for ((k, v) in values) {
            if (!restorable(k)) continue
            val x = if (k in SEALED && v is String) Vault.seal(v) ?: return false else v
            when (x) { is String -> e.putString(k, x); is Boolean -> e.putBoolean(k, x); is Int -> e.putInt(k, x); is Long -> e.putLong(k, x); is Float -> e.putFloat(k, x) }
        }
        return e.commit()
    }

    /** Сколько серверов и подписок в копии (для окна подтверждения). */
    fun counts(values: Map<String, Any>): Pair<Int, Int> =
        (values["servers"] as? String)?.let { runCatching { JSONArray(it).length() }.getOrNull() }.let { it ?: 0 } to
            (values["subs"] as? String)?.let { runCatching { JSONArray(it).length() }.getOrNull() }.let { it ?: 0 }
}
