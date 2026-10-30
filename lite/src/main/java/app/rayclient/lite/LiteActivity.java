package app.rayclient.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import java.util.List;
import java.util.ArrayList;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ArrayAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class LiteActivity extends Activity {
    private static final int VPN_REQUEST = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText link;
    private TextView status;
    private TextView connectedServer;
    private TextView connectionDetail;
    private Button connect;
    private Button disconnect;
    private TextView splitStatus;
    private TextView serverSummary;
    private TextView serverHint;
    private LinearLayout serverPreview;
    private Button showServersButton;
    private Button probeButton;
    private final List<String> serverLinks = new ArrayList<>();
    private final List<String> serverNames = new ArrayList<>();
    private final List<XrayConfig.Profile> serverProfiles = new ArrayList<>();
    private final List<Integer> serverLatencies = new ArrayList<>();
    private final List<Button> previewButtons = new ArrayList<>();
    private final ExecutorService probePool = Executors.newFixedThreadPool(3);
    private ArrayAdapter<String> selectionAdapter;
    private int probeSerial = 0;
    private int probesPending = 0;
    private boolean allProbed = false;
    private boolean fetching = false;
    private int fetchSerial = 0;
    private String pendingServer;
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
        status=text("Отключено",22,ink,true); card.addView(status,block(10));
        connectedServer=text("",17,ink,true); card.addView(connectedServer,block(10));
        connectionDetail=text("",14,sub,false); card.addView(connectionDetail,block(8));
        main.addView(text("Ссылка сервера или HTTPS-подписка",16,ink,true),block(24));
        link = new EditText(this);
        link.setSingleLine(false); link.setMaxLines(3); link.setTextSize(15);
        link.setTextColor(ink); link.setHintTextColor(sub);
        link.setHint("HTTPS, VLESS, VMess, Trojan, SS, Hysteria2");
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        link.setBackground(surface(Color.rgb(30,28,44),14));
        link.setPadding(dp(16),dp(14),dp(16),dp(14));
        main.addView(link,block(10));
        Button paste=button("Вставить из буфера",Color.rgb(44,40,60),ink);
        main.addView(paste,block(10)); paste.setOnClickListener(v -> pasteClipboard());
        main.addView(text("HTTPS-подписка: выбор сервера VLESS, VMess, Trojan, Shadowsocks или Hysteria2. Ссылки не сохраняются.",13,sub,false),block(10));
        serverSummary = text("Серверы подписки",16,ink,true);
        serverSummary.setVisibility(View.GONE);
        main.addView(serverSummary,block(24));
        serverHint = text("TCP-проверка показывает доступность порта, не работу VPN.",13,sub,false);
        serverHint.setVisibility(View.GONE);
        main.addView(serverHint,block(8));
        serverPreview = new LinearLayout(this);
        serverPreview.setOrientation(LinearLayout.VERTICAL);
        main.addView(serverPreview,block(4));
        showServersButton = button("Все серверы",Color.rgb(44,40,60),ink);
        showServersButton.setVisibility(View.GONE);
        main.addView(showServersButton,block(8));
        showServersButton.setOnClickListener(v -> showAllServers());
        probeButton = button("Проверить задержку",Color.rgb(44,40,60),ink);
        probeButton.setVisibility(View.GONE);
        main.addView(probeButton,block(8));
        probeButton.setOnClickListener(v -> startProbes(true, allProbed));
        main.addView(text("Раздельное туннелирование",16,ink,true),block(25));
        splitStatus=text(LiteSplitStore.label(this),14,sub,false);
        main.addView(splitStatus,block(9));
        Button splitButton=button("Выбрать приложения",Color.rgb(44,40,60),ink);
        main.addView(splitButton,block(10));
        splitButton.setOnClickListener(v -> chooseSplitMode());
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
        boolean active = LiteVpnService.connected;
        status.setText(active ? "VPN подключён" : LiteVpnService.status);
        status.setTextColor(active ? Color.rgb(61,219,180) :
            LiteVpnService.status.startsWith("Ошибка:") ? Color.rgb(255,107,107) : Color.WHITE);
        connectedServer.setVisibility(active ? View.VISIBLE : View.GONE);
        connectionDetail.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            connectedServer.setText("Сервер: " + LiteVpnService.activeServer);
            long elapsed = Math.max(0L, (SystemClock.elapsedRealtime() - LiteVpnService.connectedAtElapsedMs) / 1000L);
            connectionDetail.setText(String.format(java.util.Locale.getDefault(),
                "Активен %02d:%02d:%02d · Xray работает\\nИнтернет и DNS ещё не проверены",
                elapsed / 3600, (elapsed / 60) % 60, elapsed % 60));
        }
        connect.setText(active ? "Подключено" : "Подключить");
        connect.setEnabled(!active && !LiteVpnService.busy && !fetching);
        disconnect.setEnabled(active || LiteVpnService.busy);
    }
    /** Split tunneling uses Android's per-UID VPN allow/disallow lists (API 21+). */
    private void chooseSplitMode() {
        LiteSplitPolicy original=LiteSplitStore.read(this);
        String[] modes={"Все приложения через VPN",
            "Только выбранные — остальные напрямую",
            "Все, кроме выбранных — исключения напрямую"};
        new AlertDialog.Builder(this).setTitle("Как направлять трафик?")
            .setSingleChoiceItems(modes,original.mode,(dialog,mode)->{
                dialog.dismiss();
                if (mode == LiteSplitPolicy.ALL) saveSplit(new LiteSplitPolicy(mode,Collections.<String>emptySet()));
                else chooseSplitApps(mode);
            }).setNegativeButton("Отмена",null).show();
    }

    private void chooseSplitApps(int mode) {
        PackageManager pm=getPackageManager();
        java.util.ArrayList<ApplicationInfo> apps=new java.util.ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            if (getPackageName().equals(app.packageName)) continue;
            if (pm.getLaunchIntentForPackage(app.packageName) != null) apps.add(app);
        }
        Collections.sort(apps,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(
            String.valueOf(pm.getApplicationLabel(a)),String.valueOf(pm.getApplicationLabel(b))));
        if (apps.isEmpty()) { showError("Нет доступных приложений для выбора"); return; }
        String[] names=new String[apps.size()];
        boolean[] checked=new boolean[apps.size()];
        Set<String> selected=new LinkedHashSet<>(LiteSplitStore.read(this).packages);
        for (int i=0;i<apps.size();i++) {
            ApplicationInfo app=apps.get(i);
            names[i]=String.valueOf(pm.getApplicationLabel(app))+" · "+app.packageName;
            checked[i]=selected.contains(app.packageName);
        }
        new AlertDialog.Builder(this)
            .setTitle(mode==LiteSplitPolicy.ONLY ? "Через VPN только выбранные" : "Без VPN выбранные приложения")
            .setMultiChoiceItems(names,checked,(dialog,index,value)->{
                String id=apps.get(index).packageName;
                if (value) selected.add(id); else selected.remove(id);
            })
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(dialog,which)->saveSplit(new LiteSplitPolicy(mode,selected)))
            .show();
    }

    private void saveSplit(LiteSplitPolicy policy) {
        try {
            LiteSplitStore.save(this,policy);
            splitStatus.setText(LiteSplitStore.label(this));
            if (LiteVpnService.connected || LiteVpnService.busy)
                LiteVpnService.status="Настройки приложений сохранены. Переподключи VPN для применения.";
            else LiteVpnService.status="Режим приложений сохранён. Подключайся.";
            renderStatus();
        } catch (Exception e) {
            showError(e.getMessage()==null?"Не удалось сохранить правила":e.getMessage());
        }
    }

    private void pasteClipboard() {
        try {
            ClipboardManager manager = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) throw new IllegalArgumentException("Буфер обмена пуст");
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            if (value == null || value.length() > 8192) throw new IllegalArgumentException("Нет подходящей ссылки в буфере");
            link.setText(value);
            link.setSelection(link.length());
        } catch (Exception e) { showError("Не удалось вставить ссылку"); }
    }

    private void showError(String message) {
        LiteVpnService.status = message.startsWith("Ошибка:") ? message : "Ошибка: " + message;
        renderStatus();
    }

    private void connect() {
        String input = link.getText().toString().trim();
        if (input.regionMatches(true, 0, "https://", 0, 8)) {
            loadSubscription(input);
        } else if (input.regionMatches(true, 0, "http://", 0, 7)) {
            showError("Подписка должна использовать HTTPS");
        } else {
            connectServer(input);
        }
    }

    private void loadSubscription(String input) {
        if (fetching) return;
        fetching = true;
        final int serial = ++fetchSerial;
        LiteVpnService.status = "Загрузка подписки…";
        renderStatus();
        new Thread(() -> {
            List<String> servers = null;
            String error = null;
            try { servers = SubscriptionFetcher.load(getApplicationContext(), input); }
            catch (Exception e) {
                error = SubscriptionFailure.describe(e);
            }
            final List<String> result = servers;
            final String failure = error;
            runOnUiThread(() -> {
                if (serial != fetchSerial || isFinishing() || isDestroyed()) return;
                fetching = false;
                if (!input.equals(link.getText().toString().trim())) {
                    showError("Ссылка изменена — повтори загрузку");
                } else if (failure != null) {
                    showError(failure);
                } else {
                    chooseServer(result);
                }
                renderStatus();
            });
        }, "LiteSubscription").start();
    }

    private void chooseServer(List<String> entries) {
        ++probeSerial;
        serverLinks.clear();
        serverNames.clear();
        serverProfiles.clear();
        serverLatencies.clear();
        previewButtons.clear();
        serverPreview.removeAllViews();
        selectionAdapter = null;
        allProbed = false;
        probesPending = 0;
        for (String entry : entries) {
            try {
                XrayConfig.Profile profile = XrayConfig.parse(entry);
                Uri uri = Uri.parse(entry);
                String remark = "vmess".equals(profile.type)
                    ? profile.vmess.optString("ps", "") : Uri.decode(uri.getFragment());
                serverLinks.add(entry);
                serverProfiles.add(profile);
                serverNames.add(LiteServerLabel.format(profile.type, remark, profile.host));
                serverLatencies.add(LiteServerProbe.UNCHECKED);
            } catch (Exception ignored) { /* Unsupported entries never appear as selectable. */ }
        }
        if (serverLinks.isEmpty()) {
            showError("В подписке нет совместимых серверов: проверь протоколы и транспорты");
            return;
        }
        LiteVpnService.status = "Найдено серверов: " + serverLinks.size() + ". Выбери один.";
        renderStatus();
        serverSummary.setText("Серверы подписки · " + serverLinks.size());
        serverSummary.setVisibility(View.VISIBLE);
        serverHint.setText("TCP-проверка идёт напрямую до VPN. Это не пинг туннеля или проверка интернета.");
        serverHint.setVisibility(View.VISIBLE);
        for (int i = 0; i < Math.min(4, serverLinks.size()); i++) {
            final int index = i;
            Button row = button("", Color.rgb(30,28,44), Color.rgb(241,238,249));
            row.setTextSize(14);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(64));
            row.setPadding(dp(16), dp(7), dp(14), dp(7));
            row.setOnClickListener(v -> connectServer(serverLinks.get(index)));
            serverPreview.addView(row,block(8));
            previewButtons.add(row);
        }
        showServersButton.setVisibility(serverLinks.size() > 4 ? View.VISIBLE : View.GONE);
        showServersButton.setText("Все серверы (" + serverLinks.size() + ")");
        probeButton.setVisibility(View.VISIBLE);
        repaintServers();
        startProbes(false, false);
        showAllServers();
    }

    private String serverRow(int index) {
        return serverNames.get(index) + "\\n" +
            LiteServerProbe.describe(serverProfiles.get(index).type, serverLatencies.get(index));
    }

    private void repaintServers() {
        for (int i=0; i<previewButtons.size(); i++) {
            previewButtons.get(i).setText(serverRow(i));
        }
        if (selectionAdapter != null) {
            selectionAdapter.clear();
            for (int i=0; i<serverLinks.size(); i++) selectionAdapter.add(serverRow(i));
            selectionAdapter.notifyDataSetChanged();
        }
        probeButton.setEnabled(probesPending == 0);
        if (serverLinks.size() > 24 && !allProbed)
            probeButton.setText("Проверить остальные серверы");
        else
            probeButton.setText("Повторить TCP-проверку");
    }

    private void showAllServers() {
        if (serverLinks.isEmpty()) return;
        ArrayList<String> display = new ArrayList<>();
        for (int i=0; i<serverLinks.size(); i++) display.add(serverRow(i));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, display);
        selectionAdapter = adapter;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Выбери сервер")
            .setAdapter(adapter, (d,index) -> connectServer(serverLinks.get(index)))
            .setNegativeButton("Закрыть", null).create();
        dialog.setOnDismissListener(d -> {
            if (selectionAdapter == adapter) selectionAdapter = null;
        });
        dialog.show();
    }

    private void startProbes(boolean includeAll, boolean force) {
        if (serverLinks.isEmpty() || probesPending > 0) return;
        if (LiteVpnService.connected || LiteVpnService.busy) {
            serverHint.setText("Отключи VPN для прямой TCP-проверки серверов. Ранее измеренные значения сохранены.");
            return;
        }
        if (includeAll) allProbed = true;
        final int serial = probeSerial;
        final int limit = includeAll ? serverLinks.size() : Math.min(24, serverLinks.size());
        for (int i=0; i<limit; i++) {
            XrayConfig.Profile profile = serverProfiles.get(i);
            if (!LiteServerProbe.supportsTcp(profile.type)) continue;
            if (!force && serverLatencies.get(i) != LiteServerProbe.UNCHECKED) continue;
            serverLatencies.set(i,LiteServerProbe.CHECKING);
            ++probesPending;
            final int index=i;
            probePool.execute(() -> {
                int result=LiteServerProbe.measureTcp(profile.host,profile.port,1200);
                runOnUiThread(() -> {
                    if (serial != probeSerial || isDestroyed() || isFinishing()) return;
                    serverLatencies.set(index,result);
                    probesPending=Math.max(0,probesPending-1);
                    repaintServers();
                });
            });
        }
        repaintServers();
    }

    private void connectServer(String candidate) {
        try {
            XrayConfig.parse(candidate);
            pendingServer = candidate;
            Intent approve = VpnService.prepare(this);
            if (approve != null) startActivityForResult(approve,VPN_REQUEST);
            else startVpn();
        } catch (Exception e) { showError(e.getMessage() == null ? "Неверная ссылка" : e.getMessage()); }
    }

    private void startVpn() {
        if (pendingServer == null) {
            showError("Сервер не выбран. Повтори подключение");
            return;
        }
        startService(new Intent(this, LiteVpnService.class)
            .setAction(LiteVpnService.START).putExtra("server_link", pendingServer));
        pendingServer = null;
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
    @Override protected void onDestroy() {
        fetchSerial++;
        probeSerial++;
        pendingServer = null;
        probePool.shutdownNow();
        serverLinks.clear();
        super.onDestroy();
    }
}
