package app.rayclient.tv;

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

/**
 * Independent Android Keystore encrypted profile storage.
 * No plaintext VLESS links are written to disk, CI or GitHub Issues.
 */
final class TvProfileStore {
    private static final String PREFS = "tv_profiles_private_v1";
    private static final String ITEM = "profile";
    private static final String KEY = "app.rayclient.tv.profile.aes.v1";

    private TvProfileStore() {}

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY)) {
            return (SecretKey) store.getKey(KEY, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasProfile(Context ctx) {
        return prefs(ctx).contains(ITEM);
    }

    static void save(Context ctx, String raw) throws Exception {
        // Strict validation before writing ensures prior valid settings survive errors.
        TvServerProfile.parse(raw);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        String serialized = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
            + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!prefs(ctx).edit().putString(ITEM, serialized).commit()) {
            throw new IllegalStateException("Secure storage write failed");
        }
    }

    static TvServerProfile load(Context ctx) throws Exception {
        String serialized = prefs(ctx).getString(ITEM, null);
        if (serialized == null) {
            throw new IllegalStateException("No VPN profile configured");
        }
        String[] fields = serialized.split(":", -1);
        if (fields.length != 2) {
            throw new IllegalStateException("Invalid encrypted profile record");
        }
        byte[] iv = Base64.decode(fields[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(fields[1], Base64.NO_WRAP);
        if (iv.length != 12 || encrypted.length < 16 || encrypted.length > 8192) {
            throw new IllegalStateException("Invalid encrypted profile");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return TvServerProfile.parse(new String(
            cipher.doFinal(encrypted), StandardCharsets.UTF_8));
    }
}
