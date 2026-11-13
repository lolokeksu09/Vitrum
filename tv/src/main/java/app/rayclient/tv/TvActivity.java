package app.rayclient.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * Stage 1: native TV shell only. Deliberately has no VpnService, Internet,
 * profile editor or background service; all connection labels say the truth.
 */
public final class TvActivity extends Activity {
    private static final String SAVED_PAGE = "tv.page";
    private static final int BG = Color.rgb(11, 11, 17);
    private static final int RAIL = Color.rgb(18, 18, 27);
    private static final int SURFACE = Color.rgb(25, 25, 38);
    private static final int RAISED = Color.rgb(34, 34, 50);
    private static final int TEXT = Color.rgb(244, 242, 251);
    private static final int MUTED = Color.rgb(185, 181, 202);
    private static final int ACCENT = Color.rgb(167, 139, 250);
    private static final int FOCUS = Color.WHITE;
    private static final int WARNING = Color.rgb(240, 196, 116);

    private static final int[] NAV_IDS = {
        R.id.tv_nav_home, R.id.tv_nav_servers, R.id.tv_nav_apps,
        R.id.tv_nav_setup, R.id.tv_nav_settings
    };
    private static final int[] NAV_LABELS = {
        R.string.nav_home, R.string.nav_servers, R.string.nav_apps,
        R.string.nav_setup, R.string.nav_settings
    };

    private final TvNavigationModel navigation = new TvNavigationModel();
    private final ArrayList<TextView> navViews = new ArrayList<>();
    private LinearLayout pageBody;
    private TextView primaryAction;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int fill, int border, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke > 0) drawable.setStroke(dp(stroke), border);
        return drawable;
    }

    private StateListDrawable buttonBackground(boolean primary, boolean selected) {
        StateListDrawable states = new StateListDrawable();
        int[] focused = { android.R.attr.state_focused };
        int[] pressed = { android.R.attr.state_pressed };
        states.addState(pressed, rounded(ACCENT, FOCUS, 2, 12));
        states.addState(focused, rounded(primary ? ACCENT : RAISED, FOCUS, 2, 12));
        states.addState(new int[]{}, rounded(
            primary ? ACCENT : selected ? RAISED : RAIL,
            selected ? ACCENT : RAIL, selected ? 1 : 0, 12));
        return states;
    }

    private TextView label(int textId, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(textId);
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setIncludeFontPadding(true);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private TextView action(int labelId, int viewId, boolean primary, boolean selected,
                            View.OnClickListener onClick) {
        TextView button = label(labelId, 20, TEXT, primary || selected);
        button.setId(viewId);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(58));
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        button.setBackground(buttonBackground(primary, selected));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setOnClickListener(onClick);
        return button;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout.LayoutParams spacing(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private void addText(int labelId, int size, int color, boolean bold, int top, int bottom) {
        TextView text = label(labelId, size, color, bold);
        pageBody.addView(text, spacing(top, bottom));
    }

    private LinearLayout panel() {
        LinearLayout card = vertical();
        card.setPadding(dp(30), dp(26), dp(30), dp(26));
        card.setBackground(rounded(SURFACE, SURFACE, 0, 20));
        pageBody.addView(card, spacing(20, 0));
        return card;
    }

    private void panelText(LinearLayout parent, int id, int size, int color, boolean bold, int top) {
        TextView text = label(id, size, color, bold);
        LinearLayout.LayoutParams params = spacing(top, 0);
        parent.addView(text, params);
    }

    private void insertPrimary(int textId, TvNavigationModel.Page destination) {
        insertPrimary(textId, v -> showPage(destination, true));
    }

    private void insertPrimary(int textId, View.OnClickListener onClick) {
        primaryAction = action(textId, R.id.tv_primary_action, true, false, onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(22);
        params.bottomMargin = dp(12);
        pageBody.addView(primaryAction, params);
    }

    private TextView panelDetail(LinearLayout parent, int id, int viewId) {
        TextView detail = label(id, 20, TEXT, false);
        detail.setId(viewId);
        parent.addView(detail, spacing(14, 0));
        return detail;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) navigation.restore(state.getString(SAVED_PAGE));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(48), dp(30), dp(48), dp(28));

        LinearLayout rail = vertical();
        rail.setPadding(dp(14), dp(20), dp(14), dp(20));
        rail.setBackground(rounded(RAIL, RAIL, 0, 20));
        root.addView(rail, new LinearLayout.LayoutParams(dp(232), -1));

        TextView brand = label(R.string.product_name, 29, TEXT, true);
        rail.addView(brand, spacing(0, 0));
        TextView edition = label(R.string.build_label, 12, ACCENT, true);
        rail.addView(edition, spacing(2, 30));

        TvNavigationModel.Page[] pages = TvNavigationModel.Page.values();
        for (int i = 0; i < pages.length; i++) {
            final TvNavigationModel.Page destination = pages[i];
            TextView button = action(NAV_LABELS[i], NAV_IDS[i], false,
                navigation.current() == destination, v -> showPage(destination, true));
            LinearLayout.LayoutParams row = spacing(0, 10);
            rail.addView(button, row);
            navViews.add(button);
        }
        for (int i = 0; i < navViews.size(); i++) {
            navViews.get(i).setNextFocusUpId(NAV_IDS[Math.max(0, i - 1)]);
            navViews.get(i).setNextFocusDownId(NAV_IDS[Math.min(i + 1, NAV_IDS.length - 1)]);
        }

        View flexibleSpace = new View(this);
        rail.addView(flexibleSpace, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView railFooter = label(R.string.preview_only, 14, MUTED, false);
        rail.addView(railFooter);

        LinearLayout main = vertical();
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(0, -1, 1f);
        mainParams.leftMargin = dp(32);
        root.addView(main, mainParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        pageBody = vertical();
        pageBody.setPadding(dp(10), dp(10), dp(10), dp(18));
        scroll.addView(pageBody);

        TextView footer = label(R.string.footer_hint, 15, MUTED, false);
        LinearLayout.LayoutParams footerParams = spacing(8, 0);
        main.addView(footer, footerParams);

        setContentView(root);
        renderPage();
        // Give the first actionable element a deterministic D-pad focus.
        root.post(() -> {
            if (!isFinishing() && primaryAction != null) primaryAction.requestFocus();
        });
    }

    private void renderPage() {
        pageBody.removeAllViews();
        primaryAction = null;
        TvNavigationModel.Page current = navigation.current();

        int title;
        int description;
        switch (current) {
            case SERVERS:
                title = R.string.servers_title; description = R.string.servers_description; break;
            case APPS:
                title = R.string.apps_title; description = R.string.apps_description; break;
            case SETUP:
                title = R.string.setup_title; description = R.string.setup_description; break;
            case SETTINGS:
                title = R.string.settings_title; description = R.string.settings_description; break;
            default:
                title = R.string.home_title; description = R.string.home_subtitle; break;
        }

        TextView header = label(title, 31, TEXT, true);
        header.setId(R.id.tv_page_title);
        pageBody.addView(header, spacing(8, 6));
        addText(description, 19, MUTED, false, 0, 8);

        if (current == TvNavigationModel.Page.HOME) {
            LinearLayout statusPanel = panel();
            TextView status = label(R.string.status_offline, 30, TEXT, true);
            status.setId(R.id.tv_status);
            statusPanel.addView(status);
            panelText(statusPanel, R.string.status_explanation, 18, MUTED, false, 12);
            panelText(statusPanel, R.string.never_connected, 15, WARNING, true, 18);
            insertPrimary(R.string.main_action, TvNavigationModel.Page.SETTINGS);

            LinearLayout next = panel();
            panelText(next, R.string.home_supported_title, 21, TEXT, true, 0);
            panelText(next, R.string.home_supported_description, 17, MUTED, false, 8);

        } else if (current == TvNavigationModel.Page.SERVERS) {
            LinearLayout empty = panel();
            panelText(empty, R.string.servers_empty_title, 24, TEXT, true, 0);
            panelDetail(empty, R.string.servers_empty_detail, R.id.tv_empty_detail);
            panelText(empty, R.string.preview_only, 15, WARNING, true, 18);
            insertPrimary(R.string.action_setup, TvNavigationModel.Page.SETUP);

        } else if (current == TvNavigationModel.Page.APPS) {
            LinearLayout installed = panel();
            panelText(installed, R.string.apps_list_title, 24, TEXT, true, 0);
            TextView appList = panelDetail(
                installed, R.string.apps_loading, R.id.tv_apps_snapshot);
            appList.setText(TvFacts.apps(this));
            panelText(installed, R.string.apps_list_hint, 16, WARNING, false, 18);
            insertPrimary(R.string.action_refresh_apps,
                v -> showPage(TvNavigationModel.Page.APPS, true));

        } else if (current == TvNavigationModel.Page.SETUP) {
            LinearLayout steps = panel();
            panelText(steps, R.string.setup_steps_title, 24, TEXT, true, 0);
            panelDetail(steps, R.string.setup_steps_detail, R.id.tv_empty_detail);
            panelText(steps, R.string.preview_only, 15, WARNING, true, 18);
            insertPrimary(R.string.action_check_device, TvNavigationModel.Page.SETTINGS);

        } else if (current == TvNavigationModel.Page.SETTINGS) {
            LinearLayout info = panel();
            panelText(info, R.string.settings_device_title, 24, TEXT, true, 0);
            TextView details = panelDetail(
                info, R.string.details_loading, R.id.tv_device_details);
            details.setText(TvFacts.device(this));
            LinearLayout protection = panel();
            panelText(protection, R.string.settings_status_title, 20, TEXT, true, 0);
            panelText(protection, R.string.settings_status_detail, 17, MUTED, false, 8);
            insertPrimary(R.string.action_refresh_details,
                v -> showPage(TvNavigationModel.Page.SETTINGS, true));
        }

        for (int i = 0; i < navViews.size(); i++) {
            TextView item = navViews.get(i);
            boolean selected = i == current.ordinal();
            item.setBackground(buttonBackground(false, selected));
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            if (primaryAction != null) item.setNextFocusRightId(primaryAction.getId());
        }
        if (primaryAction != null) {
            primaryAction.setNextFocusLeftId(NAV_IDS[current.ordinal()]);
        }
    }

    private void showPage(TvNavigationModel.Page page, boolean takeFocus) {
        navigation.open(page);
        renderPage();
        if (takeFocus && primaryAction != null) {
            TextView target = primaryAction;
            target.post(() -> {
                if (!isFinishing() && target == primaryAction) target.requestFocus();
            });
        }
    }

    @Override public void onBackPressed() {
        if (navigation.backToHome()) {
            renderPage();
            if (primaryAction != null) primaryAction.post(() -> primaryAction.requestFocus());
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(SAVED_PAGE, navigation.current().name());
        super.onSaveInstanceState(out);
    }

    // Package-private seam for actual Android UI/instrumentation tests.
    TvNavigationModel.Page currentPageForTest() {
        return navigation.current();
    }
}
