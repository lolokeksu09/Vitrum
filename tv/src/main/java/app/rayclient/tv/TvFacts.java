package app.rayclient.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Local, read-only TV device diagnostics. Never reads credentials or VPN profiles. */
final class TvFacts {
    private TvFacts() {}

    static String device(Context context) {
        StringBuilder facts = new StringBuilder();
        facts.append(context.getString(R.string.fact_model,
            Build.MODEL == null ? "?" : Build.MODEL)).append('\n');
        facts.append(context.getString(R.string.fact_android,
            Build.VERSION.RELEASE, Build.VERSION.SDK_INT)).append('\n');
        String abi = Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
        facts.append(context.getString(R.string.fact_abi, abi)).append('\n');
        ActivityManager manager =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            long mib = 1024L * 1024L;
            facts.append(context.getString(R.string.fact_memory,
                memory.totalMem / mib, memory.availMem / mib)).append('\n');
        }
        String version = "?";
        try {
            PackageInfo pkg = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            if (pkg.versionName != null) version = pkg.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep unknown instead of inventing the version.
        }
        facts.append(context.getString(R.string.fact_version, version));
        return facts.toString();
    }

    /** Only launcher-visible TV apps; this intentionally has no QUERY_ALL_PACKAGES. */
    static String apps(Context context) {
        PackageManager manager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        final List<ResolveInfo> launchers;
        try {
            launchers = manager.queryIntentActivities(intent, 0);
        } catch (SecurityException e) {
            return context.getString(R.string.apps_visibility_unavailable);
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ResolveInfo launcher : launchers) {
            if (launcher.activityInfo == null) continue;
            String packageName = launcher.activityInfo.packageName;
            if (context.getPackageName().equals(packageName)) continue;
            CharSequence displayName = launcher.loadLabel(manager);
            if (displayName == null) continue;
            String label = displayName.toString().replace('\n', ' ').trim();
            if (!label.isEmpty() && label.length() < 120) names.add(label);
        }
        if (names.isEmpty()) return context.getString(R.string.apps_not_found);
        StringBuilder result = new StringBuilder();
        result.append(context.getString(R.string.apps_found, names.size()));
        int shown = 0;
        for (String name : names) {
            if (shown == 7) break;
            result.append("\n\u2022 ").append(name);
            shown++;
        }
        if (names.size() > shown) {
            result.append('\n').append(context.getString(
                R.string.apps_more, names.size() - shown));
        }
        return result.toString();
    }
}
