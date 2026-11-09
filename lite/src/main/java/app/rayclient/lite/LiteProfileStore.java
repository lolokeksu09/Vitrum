package app.rayclient.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Encrypted single saved subscription/direct-server profile for API 23+.
 * No plaintext server URLs, UUIDs or subscription tokens in preferences.
 * Android backups are disabled in the manifest.
 */
final class LiteProfileStore {
    private static final String PREFS = "lite_profile_v1";
    private static final String VALUE = "encrypted";
    private static final String ALIAS = "app.rayclient.lite.saved_profile.v1";
    private static final int MAX_LINK = 8192;

    private LiteProfileStore() {}

    static final class Profile {
        final String source;
        final String selected;
        final String label;
        Profile(String source, String selected, String label) {
            this.source = source == null ? "" : source;
            this.selected = selected == null ? "" : selected;
            this.label = label == null ? "" : label;
        }
        boolean hasSource() { return !source.isEmpty(); }
        boolean hasSelected() { return !selected.isEmpty(); }
        boolean isSubscription() { return source.startsWith("https://"); }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            java.security.Key existing = store.getKey(ALIAS, null);
            if (!(existing instanceof SecretKey))
                throw new IllegalStateException("Ключ профиля недоступен");
            return (SecretKey)existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    private static String encrypt(String content) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] bytes = c.doFinal(content.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[iv.length + bytes.length + 1];
        packed[0] = (byte)iv.length;
        System.arraycopy(iv, 0, packed, 1, iv.length);
        System.arraycopy(bytes, 0, packed, iv.length + 1, bytes.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
        if (packed.length < 30) throw new IllegalStateException("Данные профиля повреждены");
        int ivSize = packed[0] & 0xff;
        if (ivSize != 12 || packed.length < ivSize + 17)
            throw new IllegalStateException("Формат профиля повреждён");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, packed, 1, ivSize));
        return new String(c.doFinal(packed, ivSize + 1, packed.length - ivSize - 1), StandardCharsets.UTF_8);
    }

    static Profile read(Context context) throws Exception {
        String ciphertext = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null);
        if (ciphertext == null) return new Profile("", "", "");
        JSONObject data = new JSONObject(decrypt(ciphertext));
        String source = data.getString("source");
        String selected = data.optString("selected", "");
        String label = data.optString("label", "");
        validate(source);
        if (!selected.isEmpty()) validateSelected(selected);
        return new Profile(source, selected, label);
    }

    static void validate(String source) {
        if (source == null || source.isEmpty() || source.length() > MAX_LINK ||
            !(source.startsWith("https://") || SubscriptionParser.supportedLink(source)))
            throw new IllegalArgumentException("Недопустимый адрес профиля");
    }

    static void validateSelected(String selected) {
        validate(selected);
        if (selected.startsWith("https://"))
            throw new IllegalArgumentException("Выбранный сервер должен быть одиночной ссылкой");
    }

    private static void write(Context context, Profile profile) throws Exception {
        JSONObject json = new JSONObject().put("source", profile.source)
            .put("selected", profile.selected).put("label", profile.label);
        String ciphertext = encrypt(json.toString());
        boolean committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(VALUE, ciphertext).commit();
        if (!committed) throw new IllegalStateException("Не удалось сохранить профиль");
    }

    static void saveSource(Context context, String source) throws Exception {
        validate(source);
        Profile previous = read(context);
        if (source.equals(previous.source)) return;
        write(context, new Profile(source, "", ""));
    }

    static void saveSelected(Context context, String source, String selected, String label) throws Exception {
        validate(source);
        validateSelected(selected);
        write(context, new Profile(source, selected, label));
    }

    static void clear(Context context) {
        boolean ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(VALUE).commit();
        if (!ok) throw new IllegalStateException("Не удалось удалить профиль");
    }
}
