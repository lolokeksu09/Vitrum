package app.rayclient.lite;

import java.util.Locale;

/** Displays only a short name, never a server URI, token or password. */
final class LiteServerLabel {
    private LiteServerLabel() {}

    static String format(String type, String remark, String host) {
        String name = (remark == null || remark.trim().isEmpty()) ? host : remark;
        if (name == null) name = "";
        name = name.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (name.isEmpty()) name = "Сервер";
        if (name.length() > 50) name = name.substring(0, 50) + "…";
        String protocol = type == null ? "VPN" : type.toUpperCase(Locale.ROOT);
        return protocol + " · " + name;
    }
}
