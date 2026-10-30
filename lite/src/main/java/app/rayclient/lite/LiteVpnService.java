package app.rayclient.lite;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class LiteVpnService extends VpnService {
    static final String START = "app.rayclient.lite.START";
    static final String STOP = "app.rayclient.lite.STOP";
    static volatile String status = "\u041e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e";
    static volatile boolean connected = false;
    static volatile boolean busy = false;

    private volatile boolean enabled = false;
    private volatile int generation = 0;
    private volatile int pid = 0;
    private ParcelFileDescriptor tunnel;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) {
            ++generation; enabled = false;
            new Thread(this::shutdown, "LiteStop").start();
            return START_NOT_STICKY;
        }
        if (!START.equals(action)) return START_NOT_STICKY;
        if (enabled || busy || connected) {
            status = connected ? "VPN \u0443\u0436\u0435 \u0437\u0430\u043f\u0443\u0449\u0435\u043d" : "\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435 \u0443\u0436\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f\u2026";
            return START_NOT_STICKY;
        }
        final String link = intent.getStringExtra("server_link");
        if (link == null || link.length() > 8192) {
            status = "\u041d\u0435\u0442 \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0439 \u0441\u0441\u044b\u043b\u043a\u0438 \u0441\u0435\u0440\u0432\u0435\u0440\u0430"; stopSelf(); return START_NOT_STICKY;
        }
        final int token = ++generation; enabled = true; busy = true;
        status = "\u041f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043a\u0430 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f\u2026";
        startForeground(42, notification("\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435\u2026"));
        new Thread(() -> connect(link, token), "LiteConnect").start();
        return START_NOT_STICKY;
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, LiteActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent launch = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, LiteVpnService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Vitrum Lite 32").setContentText(text).setContentIntent(launch)
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "\u041e\u0442\u043a\u043b\u044e\u0447\u0438\u0442\u044c", stop)
            .build();
    }

    private void connect(String link, int token) {
        try {
            XrayConfig.Profile profile = XrayConfig.parse(link);
            String config = XrayConfig.generate(profile);
            if (!enabled || token != generation) return;
            File conf = new File(getFilesDir(), "lite.json");
            // Atomic write: Xray never sees a half-written config.
            File tmp = new File(getFilesDir(), "lite.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp, false)) {
                out.write(config.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(conf)) {
                tmp.delete();
                throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u043a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044e Xray");
            }
            if (!enabled || token != generation) { conf.delete(); return; }
            Builder b = new Builder().setSession("Vitrum Lite").setMtu(1500)
                .addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0)
                .addDnsServer("1.1.1.1");
            // CRITICAL: Xray itself must not be routed back through its TUN.
            b.addDisallowedApplication(getPackageName());
            ParcelFileDescriptor established = b.establish();
            if (established == null) throw new IllegalStateException("Android \u043d\u0435 \u0440\u0430\u0437\u0440\u0435\u0448\u0438\u043b \u0441\u043e\u0437\u0434\u0430\u043d\u0438\u0435 VPN");
            synchronized (this) {
                if (!enabled || token != generation) { established.close(); return; }
                tunnel = established;
            }
            String exe = new File(getApplicationInfo().nativeLibraryDir, "libxray.so").getAbsolutePath();
            if (!new File(exe).canExecute()) throw new IllegalStateException("\u041d\u0435\u0442 \u0438\u0441\u043f\u043e\u043b\u043d\u044f\u0435\u043c\u043e\u0433\u043e ARM32 \u044f\u0434\u0440\u0430 Xray");
            File log = new File(getFilesDir(), "lite.log");
            if (log.exists()) log.delete();
            pid = Native.spawn(exe, conf.getAbsolutePath(), getFilesDir().getAbsolutePath(), log.getAbsolutePath(), established.getFd());
            if (pid <= 0) throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u044c Xray ARM32");
            if (!enabled || token != generation) {
                // STOP arrived during startup: never leave an orphaned Xray behind.
                Native.kill(pid); pid = 0; return;
            }
            Thread.sleep(2000);
            if (!enabled || token != generation) return;
            if (!Native.alive(pid)) {
                pid = 0;
                throw new IllegalStateException("\u042f\u0434\u0440\u043e Xray \u0437\u0430\u0432\u0435\u0440\u0448\u0438\u043b\u043e\u0441\u044c \u043f\u0440\u0438 \u0437\u0430\u043f\u0443\u0441\u043a\u0435 (\u0441\u043c. \u0434\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0443)");
            }
            busy = false; connected = true;
            status = "VPN \u0437\u0430\u043f\u0443\u0449\u0435\u043d. \u041f\u0440\u043e\u0432\u0435\u0440\u044c \u0438\u043d\u0442\u0435\u0440\u043d\u0435\u0442 \u0438 DNS.";
            startForeground(42, notification(status));
            while (enabled && token == generation) {
                Thread.sleep(2000);
                if (!Native.alive(pid)) {
                    pid = 0;
                    throw new IllegalStateException("Xray \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u043b\u0441\u044f \u2014 VPN \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0437\u0430\u0449\u0438\u0449\u0430\u0435\u0442 \u0442\u0440\u0430\u0444\u0438\u043a");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            status = "\u041e\u0448\u0438\u0431\u043a\u0430: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " \u2014 " + e.getMessage());
        } finally {
            if (token == generation) {
                enabled = false;
                shutdown();
            }
        }
    }

    private synchronized void shutdown() {
        enabled = false;
        int running = pid; pid = 0;
        if (running > 0) try { Native.kill(running); } catch (Throwable ignored) {}
        if (tunnel != null) { try { tunnel.close(); } catch (Exception ignored) {} tunnel = null; }
        connected = false; busy = false;
        if (!status.startsWith("\u041e\u0448\u0438\u0431\u043a\u0430:")) status = "\u041e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e \u2014 VPN \u043d\u0435 \u0430\u043a\u0442\u0438\u0432\u0435\u043d";
        new File(getFilesDir(), "lite.json").delete();
        new File(getFilesDir(), "lite.json.tmp").delete();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onRevoke() {
        ++generation; enabled = false;
        new Thread(this::shutdown, "LiteRevoke").start();
        super.onRevoke();
    }
    @Override public void onDestroy() {
        ++generation; enabled = false;
        shutdown();
        super.onDestroy();
    }
}
