package app.rayclient.lite;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyTlsTest {
    private File cert() { return new File("src/main/res/raw/isrgrootx1.pem"); }

    @Test public void officialRootHasPinnedFingerprintAndIsCa() throws Exception {
        assertTrue("Official root PEM must be shipped", cert().isFile());
        try (FileInputStream input = new FileInputStream(cert())) {
            X509Certificate root = LegacyTls.verifiedRoot(input);
            assertTrue(root.getBasicConstraints() >= 0);
            assertTrue(root.getSubjectX500Principal().getName().contains("ISRG Root X1"));
        }
    }
    @Test public void buildsAdditiveTrustedFactoryWithoutGlobalOverrides() throws Exception {
        try (FileInputStream input = new FileInputStream(cert())) {
            assertNotNull(LegacyTls.factory(input));
        }
    }
    @Test public void malformedCertificateIsRejected() throws Exception {
        try {
            LegacyTls.verifiedRoot(new ByteArrayInputStream("not a certificate".getBytes(StandardCharsets.US_ASCII)));
            fail("Bad certificate accepted");
        } catch (CertificateException expected) {
            assertNotNull(expected);
        }
    }
}
