package app.rayclient.tv;

import android.view.KeyEvent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TvActivityRemoteTest {
    private void key(int code) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void remoteOnlyNavigationReturnsHomeWithoutFakeVpn() {
        try (ActivityScenario<TvActivity> scenario = ActivityScenario.launch(TvActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(TvNavigationModel.Page.HOME, activity.currentPageForTest());
                View start = activity.findViewById(R.id.tv_primary_action);
                assertNotNull(start);
                assertTrue("Primary action should receive initial D-pad focus", start.hasFocus());
                assertNotNull(activity.findViewById(R.id.tv_status));
            });

            // Home primary action opens Setup, not any VPN tunnel.
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(activity -> assertEquals(
                TvNavigationModel.Page.SETUP, activity.currentPageForTest()));

            // Left from main content returns the active navigation rail item.
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            scenario.onActivity(activity -> assertTrue(
                "Setup item must be reachable without a pointer",
                activity.findViewById(R.id.tv_nav_setup).hasFocus()));

            // Move up on the rail to Apps, then activate with OK.
            key(KeyEvent.KEYCODE_DPAD_UP);
            scenario.onActivity(activity -> assertTrue(
                activity.findViewById(R.id.tv_nav_apps).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(activity -> assertEquals(
                TvNavigationModel.Page.APPS, activity.currentPageForTest()));

            // System Back returns to Home, not an artificial confirmation screen.
            key(KeyEvent.KEYCODE_BACK);
            scenario.onActivity(activity -> {
                assertEquals(TvNavigationModel.Page.HOME, activity.currentPageForTest());
                assertNotNull(activity.findViewById(R.id.tv_status));
                assertTrue(activity.findViewById(R.id.tv_primary_action).hasFocus());
            });
        }
    }

    @Test public void coldLaunchHasNoConnectCommand() {
        try (ActivityScenario<TvActivity> scenario = ActivityScenario.launch(TvActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(new TvNavigationModel().canConnect());
                assertEquals(activity.getString(R.string.status_offline),
                    ((android.widget.TextView) activity.findViewById(R.id.tv_status)).getText().toString());
            });
        }
    }
}
