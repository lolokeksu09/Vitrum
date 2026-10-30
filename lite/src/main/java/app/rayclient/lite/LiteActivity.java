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
        main.addView(text("VPN без лишнего",28,ink,true), block(10));
        main.addView(text("Galaxy Tab S 10.5 LTE · Android 6 · ARM32\nТестовая сборка. Достаточно вставить ссылку и подключиться.",15,sub,false),block(12));
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(surface(Color.rgb(27,25,39),18));
        main.addView(card,block(24));
        card.addView(text("Состояние VPN",15,sub,false));
        status=text("Отключено",20,ink,true); card.addView(status,block(10));
        main.addView(text("Ссылка сервера (VLESS или Trojan)",16,ink,true),block(24));
        link = new EditText(this);
        link.setSingleLine(false); link.setMaxLines(3); link.setTextSize(15);
        link.setTextColor(ink); link.setHintTextColor(sub);
        link.setHint("Вставьте vless://… или trojan://…");
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        link.setBackground(surface(Color.rgb(30,28,44),14));
        link.setPadding(dp(16),dp(14),dp(16),dp(14));
        main.addView(link,block(10));
        main.addView(text("Ссылка хранится только в текущем окне. Не поддерживаются подписки, VMess, игры и дополнительные маршруты.",13,sub,false),block(10));
        connect=button("Подключить",Color.rgb(117,89,229),Color.WHITE);
        main.addView(connect,block(26)); connect.setOnClickListener(v -> connect());
        disconnect=button("Отключить",Color.rgb(44,40,60),ink);
        main.addView(disconnect,block(10));
        disconnect.setOnClickListener(v -> startService(new Intent(this, LiteVpnService.class).setAction(LiteVpnService.STOP)));
        main.addView(text("Внимание: Android 6 давно не получает исправлений безопасности. Проверяй подключение и отсутствие DNS/IPv6-утечек. Успешный запуск Xray не доказывает защиту трафика.",13,sub,false),block(24));
        setContentView(scroll);
    }
    private void renderStatus() {
        if (status == null) return;
        status.setText(LiteVpnService.status);
        status.setTextColor(LiteVpnService.connected?Color.rgb(61,219,180):
            LiteVpnService.status.startsWith("Ошибка:")?Color.rgb(255,107,107):Color.WHITE);
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
            LiteVpnService.status = "Ошибка: "+e.getMessage();
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
                LiteVpnService.status = "Ошибка: разрешение VPN не выдано";
                renderStatus();
            }
        }
    }
    @Override protected void onResume() {super.onResume();handler.post(update);}
    @Override protected void onPause() {handler.removeCallbacks(update);super.onPause();}
}
