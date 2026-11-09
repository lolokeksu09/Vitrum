package app.rayclient.lite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure validated per-app VPN policy. No routing option silently widens the tunnel. */
final class LiteSplitPolicy {
    static final int ALL = 0, ONLY = 1, EXCLUDE = 2;
    final int mode;
    final Set<String> packages;
    LiteSplitPolicy(int mode, Set<String> packages) {
        if (mode < ALL || mode > EXCLUDE) throw new IllegalArgumentException("Неизвестный режим туннелирования");
        this.mode = mode;
        LinkedHashSet<String> safe = new LinkedHashSet<>();
        if (packages != null) for (String name : packages) {
            if (name != null && name.length() <= 255 && name.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
                safe.add(name);
                if (safe.size() >= 512) break;
            }
        }
        this.packages = Collections.unmodifiableSet(safe);
    }
    void validate(String ownPackage) {
        if (mode == ONLY) {
            if (packages.isEmpty() || (packages.size() == 1 && packages.contains(ownPackage)))
                throw new IllegalArgumentException("Режим «Только выбранные»: выбери хотя бы одно приложение, кроме Vitrum Lite");
        }
    }
}
