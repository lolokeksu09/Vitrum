package app.rayclient.lite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class LiteActivity extends Activity {
    private static final int VPN_REQUEST = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText link;
    private TextView status;
    private Button connect;
    private Button disconnect;
    private final Runnable update = new Runnable() {
        @Override public void run() { renderStatus(); handler.postDelayed(this, 1000); }
    };
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable surface(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout.LayoutParams block(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top); return p;
    }
    private Button button(String label, int fill, int foreground) {
        Button b = new Button(this); b.setText(label); b.setTextColor(foreground);
        b.setAllCaps(false); b.setTextSize(17);
        b.setBackground(surface(fill, 14)); b.setMinHeight(dp(54));
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,10,18));
        getWindow().setNavigationBarColor(Color.rgb(10,10,18));
        final int bg = Color.rgb(10,10,18), ink=Color.rgb(241,238,249), sub=Color.rgb(180,175,197);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL); holder.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(holder);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL); main.setPadding(dp(20),dp(30),dp(20),dp(26));
        holder.addView(main,new LinearLayout.LayoutParams(-1,-2));
        // A tablet gets a comfortable content width; no stretched phone-only layout.
        main.post(() -> {
            int limit = dp(680), w = holder.getWidth();
            if (w > limit) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(limit, -2);
                main.setLayoutParams(p);
            }
        });
        main.addView(text("VITRUM LITE",15,Color.rgb(167,139,250),true));
        main.addView(text("VPN \u0431\u0435\u0437 \u043b\u0438\u0448\u043d\u0435\u0433\u043e",28,ink,true), block(10));
        main.addView(text("Galaxy Tab S 10.5 LTE \u00b7 Android 6 \u00b7 ARM32\n\u0422\u0435\u0441\u0442\u043e\u0432\u0430\u044f \u0441\u0431\u043e\u0440\u043a\u0430. \u0414\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0432\u0441\u0442\u0430\u0432\u0438\u0442\u044c \u0441\u0441\u044b\u043b\u043a\u0443 \u0438 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u044c\u0441\u044f.",15,sub,false),block(12));
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(surface(Color.rgb(27,25,39),18));
        main.addView(card,block(24));
        card.addView(text("\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 VPN",15,sub,false));
        status=text("\u041e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e",20,ink,true); card.addView(status,block(10));
        main.addView(text("\u0421\u0441\u044b\u043b\u043a\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0430 (VLESS \u0438\u043b\u0438 Trojan)",16,ink,true),block(24));
        link = new EditText(this);
        link.setSingleLine(false); link.setMaxLines(3); link.setTextSize(15);
        link.setTextColor(ink); link.setHintTextColor(sub);
        link.setHint("\u0412\u0441\u0442\u0430\u0432\u044c\u0442\u0435 vless://\u2026 \u0438\u043b\u0438 trojan://\u2026");
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        link.setBackground(surface(Color.rgb(30,28,44),14));
        link.setPadding(dp(16),dp(14),dp(16),dp(14));
        main.addView(link,block(10));
        main.addView(text("\u0421\u0441\u044b\u043b\u043a\u0430 \u0445\u0440\u0430\u043d\u0438\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0432 \u0442\u0435\u043a\u0443\u0449\u0435\u043c \u043e\u043a\u043d\u0435. \u041d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u043f\u043e\u0434\u043f\u0438\u0441\u043a\u0438, VMess, \u0438\u0433\u0440\u044b \u0438 \u0434\u043e\u043f\u043e\u043b\u043d\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u0435 \u043c\u0430\u0440\u0448\u0440\u0443\u0442\u044b.",13,sub,false),block(10));
        connect=button("\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u044c",Color.rgb(117,89,229),Color.WHITE);
        main.addView(connect,block(26)); connect.setOnClickListener(v -> connect());
        disconnect=button("\u041e\u0442\u043a\u043b\u044e\u0447\u0438\u0442\u044c",Color.rgb(44,40,60),ink);
        main.addView(disconnect,block(10));
        disconnect.setOnClickListener(v -> startService(new Intent(this, LiteVpnService.class).setAction(LiteVpnService.STOP)));
        main.addView(text("\u0412\u043d\u0438\u043c\u0430\u043d\u0438\u0435: Android 6 \u0434\u0430\u0432\u043d\u043e \u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 \u0438\u0441\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0439 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e\u0441\u0442\u0438. \u041f\u0440\u043e\u0432\u0435\u0440\u044f\u0439 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435 \u0438 \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 DNS/IPv6-\u0443\u0442\u0435\u0447\u0435\u043a. \u0423\u0441\u043f\u0435\u0448\u043d\u044b\u0439 \u0437\u0430\u043f\u0443\u0441\u043a Xray \u043d\u0435 \u0434\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0437\u0430\u0449\u0438\u0442\u0443 \u0442\u0440\u0430\u0444\u0438\u043a\u0430.",13,sub,false),block(24));
        setContentView(scroll);
    }
    private void renderStatus() {
        if (status == null) return;
        status.setText(LiteVpnService.status);
        status.setTextColor(LiteVpnService.connected?Color.rgb(61,219,180):
            LiteVpnService.status.startsWith("\u041e\u0448\u0438\u0431\u043a\u0430:")?Color.rgb(255,107,107):Color.WHITE);
        connect.setEnabled(!LiteVpnService.connected && !LiteVpnService.busy);
        disconnect.setEnabled(LiteVpnService.connected || LiteVpnService.busy);
    }
    private void connect() {
        try {
            XrayConfig.parse(link.getText().toString());
            Intent approve=VpnService.prepare(this);
            if (approve != null) startActivityForResult(approve,VPN_REQUEST);
            else startVpn();
        } catch (Exception e) {
            // Keep the message in the shared status: the 1s UI ticker must not erase it.
            LiteVpnService.status = "\u041e\u0448\u0438\u0431\u043a\u0430: "+e.getMessage();
            renderStatus();
        }
    }
    private void startVpn() {
        startService(new Intent(this, LiteVpnService.class)
            .setAction(LiteVpnService.START).putExtra("server_link", link.getText().toString().trim()));
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==VPN_REQUEST) {
            if (result==RESULT_OK) startVpn();
            else {
                LiteVpnService.status = "\u0420\u0430\u0437\u0440\u0435\u0448\u0435\u043d\u0438\u0435 VPN \u043d\u0435 \u0432\u044b\u0434\u0430\u043d\u043e";
                renderStatus();
            }
        }
    }
    @Override protected void onResume() {super.onResume();handler.post(update);}
    @Override protected void onPause() {handler.removeCallbacks(update);super.onPause();}
}
