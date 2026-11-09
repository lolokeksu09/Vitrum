package app.rayclient.lite;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubscriptionFailureTest {
    @Test public void dnsIsNotMisdiagnosedAsCertificate() {
        String message = SubscriptionFailure.describe(new UnknownHostException("secret-sub-token"));
        assertTrue(message.contains("DNS"));
        assertFalse(message.contains("secret-sub-token"));
    }
    @Test public void timeoutIsNotMisdiagnosedAsCertificate() {
        String message = SubscriptionFailure.describe(new SocketTimeoutException("private-url"));
        assertTrue(message.contains("ожидания"));
        assertFalse(message.contains("private-url"));
    }
    @Test public void refusedConnectionHasSpecificMessage() {
        assertTrue(SubscriptionFailure.describe(new ConnectException("private")).contains("не принимает"));
    }
    @Test public void invalidCertificateRetainsValidationAndHidesUrl() {
        SSLHandshakeException handshake = new SSLHandshakeException("private-secret");
        handshake.initCause(new CertificateException("private-secret"));
        String message = SubscriptionFailure.describe(handshake);
        assertTrue(message.contains("сертификат"));
        assertFalse(message.contains("private-secret"));
    }
    @Test public void unclassifiedTlsDoesNotShowPrivateInput() {
        assertFalse(SubscriptionFailure.describe(new SSLHandshakeException("secret-123")).contains("secret-123"));
    }
    @Test public void knownHttpStatusIsRetained() {
        assertEquals("Сервер подписки ответил HTTP 403",
            SubscriptionFailure.describe(new IllegalArgumentException("Сервер подписки ответил HTTP 403")));
    }
}
