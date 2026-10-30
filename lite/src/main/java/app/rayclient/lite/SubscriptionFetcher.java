package app.rayclient.lite;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.Context;
import javax.net.ssl.HttpsURLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** HTTPS only; bounded response; no redirects to HTTP; no credentials or URL in logs. */
final class SubscriptionFetcher {
    private static final int MAX_BYTES = 1_048_576;
    private SubscriptionFetcher() {}

    static URL requireHttps(URL address) {
        if (!"https".equalsIgnoreCase(address.getProtocol()) || address.getUserInfo() != null)
            throw new IllegalArgumentException("Подписка должна использовать HTTPS");
        return address;
    }

    static List<String> load(Context context, String address) throws Exception {
        if (address == null || address.length() > 8192) throw new IllegalArgumentException("Неверный адрес подписки");
        URL current = requireHttps(new URL(address.trim()));
        for (int hop = 0; hop < 4; hop++) {
            requireHttps(current);
            HttpURLConnection conn = (HttpURLConnection) current.openConnection();
            // Only HTTPS reaches this point. Keep the system hostname verifier intact.
            LegacyTls.configure((HttpsURLConnection) conn, context);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "text/plain, */*");
            try {
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IllegalArgumentException("Переадресация без адреса");
                    current = requireHttps(new URL(current, location));
                    continue;
                }
                if (code != 200) throw new IllegalArgumentException("Сервер подписки ответил HTTP " + code);
                int declared = conn.getContentLength();
                if (declared > MAX_BYTES) throw new IllegalArgumentException("Подписка слишком большая");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream input = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int total = 0, count;
                    while ((count = input.read(buf)) != -1) {
                        total += count;
                        if (total > MAX_BYTES) throw new IllegalArgumentException("Подписка превышает 1 МБ");
                        out.write(buf, 0, count);
                    }
                }
                return SubscriptionParser.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } finally { conn.disconnect(); }
        }
        throw new IllegalArgumentException("Слишком много перенаправлений");
    }
}
