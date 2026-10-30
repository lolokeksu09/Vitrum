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
    static volatile String status = "Отключено";
    static volatile boolean connected = false;
    static volatile boolean busy = false;

    private volatile boolean enabled = false;
    private final LiteGeneration generation = new LiteGeneration();
    private final Object lifecycleLock = new Object();
    private volatile int pid = 0;
    private ParcelFileDescriptor tunnel;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (STOP.equals(action)) {
            generation.cancel(); enabled = false;
            busy = true; status = "Остановка VPN…";
            new Thread(this::shutdown, "LiteStop").start();
            return START_NOT_STICKY;
        }
        if (!START.equals(action)) return START_NOT_STICKY;
        if (enabled || busy || connected) {
            status = connected ? "VPN уже запущен" : enabled ? "Подключение уже выполняется…" : "Остановка VPN — повторите подключение после завершения";
            return START_NOT_STICKY;
        }
        final String link = intent.getStringExtra("server_link");
        if (link == null || link.length() > 8192) {
            status = "Нет корректной ссылки сервера"; stopSelf(); return START_NOT_STICKY;
        }
        final int token = generation.start(); enabled = true; busy = true;
        status = "Подготовка подключения…";
        startForeground(42, notification("Подключение…"));
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
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stop)
            .build();
    }

    private void connect(String link, int token) {
        try {
            XrayConfig.Profile profile = XrayConfig.parse(link);
            String config = XrayConfig.generate(profile); // DNS may block; cancellation is checked after it.
            synchronized (lifecycleLock) {
                if (!enabled || !generation.current(token)) return;
                File conf = new File(getFilesDir(), "lite.json");
                File tmp = new File(getFilesDir(), "lite.json.tmp");
                try {
                    try (FileOutputStream out = new FileOutputStream(tmp, false)) {
                        out.write(config.getBytes(StandardCharsets.UTF_8));
                        out.getFD().sync();
                    }
                    if (!tmp.renameTo(conf))
                        throw new IllegalStateException("Не удалось сохранить конфигурацию Xray");
                } finally {
                    tmp.delete();
                }
                if (!enabled || !generation.current(token)) return;
                Builder b = new Builder().setSession("Vitrum Lite").setMtu(1500)
                    .addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0)
                    .addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0)
                    .addDnsServer("1.1.1.1");
                b.addDisallowedApplication(getPackageName()); // prevent an Xray routing loop
                ParcelFileDescriptor established = b.establish();
                if (established == null) throw new IllegalStateException("Android не разрешил создание VPN");
                if (!enabled || !generation.current(token)) {
                    established.close();
                    return;
                }
                tunnel = established;
                String exe = new File(getApplicationInfo().nativeLibraryDir, "libxray.so").getAbsolutePath();
                if (!new File(exe).canExecute()) throw new IllegalStateException("Нет исполняемого ARM32 ядра Xray");
                File log = new File(getFilesDir(), "lite.log");
                if (log.exists()) log.delete();
                // STOP can cancel the generation at any moment. The same lock serializes
                // assigning pid and shutdown, so no Xray is orphaned after a late spawn.
                int spawned = Native.spawn(exe, conf.getAbsolutePath(), getFilesDir().getAbsolutePath(), log.getAbsolutePath(), established.getFd());
                if (spawned <= 0) throw new IllegalStateException("Не удалось запустить Xray ARM32");
                pid = spawned;
                if (!enabled || !generation.current(token)) return;
            }
            Thread.sleep(2000);
            synchronized (lifecycleLock) {
                if (!enabled || !generation.current(token)) return;
                if (!Native.alive(pid)) {
                    pid = 0;
                    throw new IllegalStateException("Ядро Xray завершилось при запуске (см. диагностику)");
                }
                busy = false; connected = true;
                status = "VPN запущен. Проверь интернет и DNS.";
                startForeground(42, notification(status));
            }
            while (enabled && generation.current(token)) {
                Thread.sleep(2000);
                synchronized (lifecycleLock) {
                    if (!enabled || !generation.current(token)) return;
                    if (!Native.alive(pid)) {
                        pid = 0;
                        throw new IllegalStateException("Xray остановился — VPN больше не защищает трафик");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            if (generation.current(token)) {
                status = "Ошибка: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " — " + e.getMessage());
            }
        } finally {
            if (generation.current(token)) {
                enabled = false;
                shutdown();
            }
        }
    }

    private void shutdown() {
        synchronized (lifecycleLock) {
            enabled = false;
            int running = pid; pid = 0;
            if (running > 0) try { Native.kill(running); } catch (Throwable ignored) {}
            if (tunnel != null) { try { tunnel.close(); } catch (Exception ignored) {} tunnel = null; }
            connected = false; busy = false;
            if (!status.startsWith("Ошибка:")) status = "Отключено — VPN не активен";
            new File(getFilesDir(), "lite.json").delete();
            // Temporary config is removed by its writer under the same lifecycle lock.
            stopForeground(true);
            stopSelf();
        }
    }

    @Override public void onRevoke() {
        generation.cancel(); enabled = false;
        new Thread(this::shutdown, "LiteRevoke").start();
        super.onRevoke();
    }
    @Override public void onDestroy() {
        generation.cancel(); enabled = false;
        shutdown();
        super.onDestroy();
    }
}
