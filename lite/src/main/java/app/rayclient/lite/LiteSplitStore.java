package app.rayclient.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** App selection only; no server credentials are saved. */
final class LiteSplitStore {
    private static final String PREF = "lite_split_v1";
    private static final String MODE = "mode";
    private static final String APPS = "apps";
    private LiteSplitStore() {}

    static LiteSplitPolicy read(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return new LiteSplitPolicy(sp.getInt(MODE, LiteSplitPolicy.ALL),
            sp.getStringSet(APPS, Collections.<String>emptySet()));
    }

    static void save(Context context, LiteSplitPolicy policy) {
        policy.validate(context.getPackageName());
        boolean saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(MODE, policy.mode)
            .putStringSet(APPS, new HashSet<>(policy.packages)).commit();
        if (!saved) throw new IllegalStateException("Не удалось сохранить выбор приложений");
    }

    /** Called before establish. Only mode uses allowlist, otherwise a disallowlist. */
    static void apply(Context context, android.net.VpnService.Builder builder) throws Exception {
        LiteSplitPolicy policy = read(context);
        policy.validate(context.getPackageName());
        if (policy.mode == LiteSplitPolicy.ONLY) {
            int allowed = 0;
            for (String pkg : policy.packages) {
                if (pkg.equals(context.getPackageName())) continue;
                try {
                    context.getPackageManager().getApplicationInfo(pkg, 0);
                    builder.addAllowedApplication(pkg);
                    allowed++;
                } catch (PackageManager.NameNotFoundException gone) {
                    // Removed applications must never cause fall-back to all apps.
                }
            }
            if (allowed == 0) throw new IllegalArgumentException("Выбранные приложения не установлены: выбери приложения заново");
        } else {
            // Exclude the VPN process itself, otherwise its Xray outbound recurses into TUN.
            builder.addDisallowedApplication(context.getPackageName());
            if (policy.mode == LiteSplitPolicy.EXCLUDE) for (String pkg : policy.packages) {
                if (pkg.equals(context.getPackageName())) continue;
                try {
                    context.getPackageManager().getApplicationInfo(pkg, 0);
                    builder.addDisallowedApplication(pkg);
                } catch (PackageManager.NameNotFoundException gone) {
                    // Stale selections are harmless.
                }
            }
        }
    }

    static String label(Context context) {
        LiteSplitPolicy policy = read(context);
        if (policy.mode == LiteSplitPolicy.ALL) return "Весь трафик приложений через VPN";
        if (policy.mode == LiteSplitPolicy.ONLY)
            return "Только выбранные приложения (" + policy.packages.size() + "). Остальные идут напрямую.";
        return "Все приложения, кроме выбранных (" + policy.packages.size() + "). Исключения идут напрямую.";
    }
}
