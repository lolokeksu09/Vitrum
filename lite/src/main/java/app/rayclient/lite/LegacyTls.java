package app.rayclient.lite;

import android.content.Context;
import android.os.Build;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Some Android 6 devices predate ISRG Root X1. Add the exact official CA to the
 * platform trust anchors for Lite subscription HTTPS only. Every certificate
 * chain still needs a valid path and HTTPS's normal hostname verification.
 * No trust-all manager, no hostname override, no global TLS settings.
 */
final class LegacyTls {
    private static final String ISRG_X1_SHA256 =
        "96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6";
    private static volatile SSLSocketFactory legacyFactory;

    private LegacyTls() {}

    static void configure(HttpsURLConnection connection, Context context) throws Exception {
        // Android 7.1.1+ generally carries ISRG Root X1; leave its provider untouched.
        if (Build.VERSION.SDK_INT >= 25) return;
        SSLSocketFactory cached = legacyFactory;
        if (cached == null) {
            synchronized (LegacyTls.class) {
                cached = legacyFactory;
                if (cached == null) {
                    try (InputStream input = context.getResources().openRawResource(R.raw.isrgrootx1)) {
                        cached = factory(input);
                    }
                    legacyFactory = cached;
                }
            }
        }
        connection.setSSLSocketFactory(cached);
        // Deliberately do not call setHostnameVerifier().
    }

    static X509Certificate verifiedRoot(InputStream pem) throws Exception {
        X509Certificate root = (X509Certificate) CertificateFactory
            .getInstance("X.509").generateCertificate(pem);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = digest.digest(root.getEncoded());
        StringBuilder value = new StringBuilder(fingerprint.length * 2);
        for (byte b : fingerprint) {
            int v = b & 0xff;
            value.append(Character.forDigit(v >>> 4, 16));
            value.append(Character.forDigit(v & 15, 16));
        }
        if (!ISRG_X1_SHA256.equalsIgnoreCase(value.toString())
            || root.getBasicConstraints() < 0)
            throw new CertificateException("Bundled root CA fingerprint mismatch");
        root.checkValidity();
        root.verify(root.getPublicKey());
        return root;
    }

    static SSLSocketFactory factory(InputStream input) throws Exception {
        X509Certificate root = verifiedRoot(input);
        TrustManagerFactory systemFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        systemFactory.init((KeyStore) null);
        X509TrustManager platform = x509(systemFactory.getTrustManagers());
        KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
        extra.load(null, null);
        extra.setCertificateEntry("isrg-root-x1", root);
        TrustManagerFactory extraFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        extraFactory.init(extra);
        X509TrustManager isrg = x509(extraFactory.getTrustManagers());
        X509TrustManager additive = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { platform.checkClientTrusted(chain, authType); }

            @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
                try { platform.checkServerTrusted(chain, authType); }
                catch (CertificateException systemFailure) {
                    try { isrg.checkServerTrusted(chain, authType); }
                    catch (CertificateException extraFailure) {
                        systemFailure.addSuppressed(extraFailure);
                        throw systemFailure;
                    }
                }
            }

            @Override public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] a = platform.getAcceptedIssuers();
                X509Certificate[] b = isrg.getAcceptedIssuers();
                X509Certificate[] result = Arrays.copyOf(a, a.length + b.length);
                System.arraycopy(b, 0, result, a.length, b.length);
                return result;
            }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{ additive }, new SecureRandom());
        return ssl.getSocketFactory();
    }

    private static X509TrustManager x509(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new IllegalStateException("X509 trust manager unavailable");
    }
}
