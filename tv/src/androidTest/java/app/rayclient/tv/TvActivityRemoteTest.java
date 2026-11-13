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

            // Home now opens genuine read-only device information, never a VPN tunnel.
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(activity -> assertEquals(
                TvNavigationModel.Page.SETTINGS, activity.currentPageForTest()));

            // Left from main content returns the active Settings navigation item.
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            scenario.onActivity(activity -> assertTrue(
                "Settings must be reachable without a pointer",
                activity.findViewById(R.id.tv_nav_settings).hasFocus()));

            // Move up to Connection and activate with OK.
            key(KeyEvent.KEYCODE_DPAD_UP);
            scenario.onActivity(activity -> assertTrue(
                activity.findViewById(R.id.tv_nav_setup).hasFocus()));
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            scenario.onActivity(activity -> assertEquals(
                TvNavigationModel.Page.SETUP, activity.currentPageForTest()));

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


    @Test public void actualDeviceDetailsAndAppsAreVisibleWithoutVpnPrivileges() {
        try (ActivityScenario<TvActivity> scenario = ActivityScenario.launch(TvActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.tv_nav_apps).performClick();
                assertEquals(TvNavigationModel.Page.APPS, activity.currentPageForTest());
                android.widget.TextView installed =
                    activity.findViewById(R.id.tv_apps_snapshot);
                assertNotNull("Read-only TV-app list should be present", installed);
                assertTrue(installed.getText().length() > 8);
                android.widget.TextView refresh =
                    activity.findViewById(R.id.tv_primary_action);
                assertEquals(activity.getString(R.string.action_refresh_apps),
                             refresh.getText().toString());
                assertFalse(new TvNavigationModel().canConnect());

                activity.findViewById(R.id.tv_nav_settings).performClick();
                assertEquals(TvNavigationModel.Page.SETTINGS, activity.currentPageForTest());
                android.widget.TextView details =
                    activity.findViewById(R.id.tv_device_details);
                assertNotNull("Device facts should be visible", details);
                assertTrue(details.getText().toString().contains(
                    android.os.Build.VERSION.RELEASE));
                assertEquals(activity.getString(R.string.action_refresh_details),
                    ((android.widget.TextView) activity.findViewById(
                        R.id.tv_primary_action)).getText().toString());
            });
        }
    }

    @Test public void distinctEmptyStatesDoNotRepeatTheirHeaderDescription() {
        try (ActivityScenario<TvActivity> scenario = ActivityScenario.launch(TvActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.tv_nav_servers).performClick();
                android.widget.TextView details = activity.findViewById(R.id.tv_empty_detail);
                assertNotNull(details);
                assertFalse(details.getText().toString().contains(
                    activity.getString(R.string.servers_description)));
                assertEquals(activity.getString(R.string.action_setup),
                    ((android.widget.TextView) activity.findViewById(
                        R.id.tv_primary_action)).getText().toString());
                activity.findViewById(R.id.tv_nav_setup).performClick();
                assertEquals(activity.getString(R.string.nav_setup),
                    ((android.widget.TextView) activity.findViewById(
                        R.id.tv_page_title)).getText().toString());
            });
        }
    }

}
