package app.rayclient.tv;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Emulator-only contract checks; no real VPN or test credentials. */
@RunWith(AndroidJUnit4.class)
public final class TvProfileSecurityTest {
    private static final String LINK =
        "vless://11111111-1111-4111-8111-111111111111@vpn.example.org:443" +
        "?security=reality&type=tcp&sni=www.example.org" +
        "&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "&sid=abcd&fp=chrome&flow=xtls-rprx-vision";

    @Test public void profileIsEncryptedAndInvalidImportNeverOverwritesIt()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            TvProfileStore.save(context, LINK);
            assertTrue(TvProfileStore.hasProfile(context));
            String disk = context.getSharedPreferences(
                "tv_profiles_private_v1", Context.MODE_PRIVATE)
                .getString("profile", "");
            assertFalse(disk.contains("vpn.example.org"));
            assertFalse(disk.contains("11111111-1111"));
            assertEquals("vpn.example.org", TvProfileStore.load(context).server);

            try {
                TvProfileStore.save(context, "vless://invalid");
                fail("Expected rejection of malformed profile");
            } catch (IllegalArgumentException expected) {
                assertEquals("vpn.example.org", TvProfileStore.load(context).server);
            }
        } finally {
            // Isolated emulator test cleanup; never a production migration or action.
            context.getSharedPreferences(
                "tv_profiles_private_v1", Context.MODE_PRIVATE)
                .edit().remove("profile").commit();
        }
    }

    @Test public void generatedXrayHasTunAndNoDirectFallback() throws Exception {
        JSONObject root = new JSONObject(TvXrayConfig.build(TvServerProfile.parse(LINK)));
        assertEquals("tun", root.getJSONArray("inbounds")
            .getJSONObject(0).getString("protocol"));
        JSONObject out = root.getJSONArray("outbounds").getJSONObject(0);
        assertEquals("vless", out.getString("protocol"));
        assertEquals("reality", out.getJSONObject("streamSettings").getString("security"));
        assertEquals(1, root.getJSONArray("outbounds").length());
        assertFalse(root.toString().contains("\"protocol\":\"freedom\""));
        assertFalse(root.toString().contains("\"allowInsecure\":true"));
    }
}
