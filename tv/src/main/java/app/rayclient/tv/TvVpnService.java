package app.rayclient.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Android VPN lifecycle for the independent TV product.
 *
 * A live TUN + Xray process is NEVER reported as proof of real proxy traffic.
 * Permission, a locally encrypted profile and packaged Android ARM32 runtime
 * are all mandatory before changing any Android routes.
 */
public final class TvVpnService extends VpnService {
    static final String ACTION_START = "app.rayclient.tv.vpn.START";
    static final String ACTION_STOP = "app.rayclient.tv.vpn.STOP";
    private static final String CHANNEL = "vitrum_tv_vpn";
    private static final int NOTIFICATION_ID = 114;

    enum State {
        OFFLINE, PREPARING, RUNNING_UNVERIFIED, FAILED
    }

    private static volatile State state = State.OFFLINE;
    private static volatile String failure = "";
    private final Object lock = new Object();
    private ParcelFileDescriptor tun;
    private int childPid;
    private int epoch;

    static State state() { return state; }
    static String failure() { return failure; }

    private void recordFailure() {
        state = State.FAILED;
        failure = getString(R.string.vpn_error_generic);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRequested();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            // Android may instantiate the service for Always-on. Without an
            // explicit local request and loaded profile, do not hijack traffic.
            return START_NOT_STICKY;
        }
        // This permission is explicitly granted by the physical TV user.
        if (VpnService.prepare(this) != null) {
            recordFailure();
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            // Verify prerequisites before asking Android to route ANY traffic.
            TvProfileStore.load(this);
            File executable = new File(
                getApplicationInfo().nativeLibraryDir, "libxray.so"
            );
            if (!executable.isFile() || !executable.canExecute()) {
                recordFailure();
                stopSelf();
                return START_NOT_STICKY;
            }
            enterForeground(getString(R.string.vpn_status_preparing));
        } catch (Exception | LinkageError e) {
            recordFailure();
            stopSelf();
            return START_NOT_STICKY;
        }

        final int generation;
        synchronized (lock) {
            generation = ++epoch;
            state = State.PREPARING;
            failure = "";
        }
        new Thread(() -> connect(generation), "VitrumTV-VPN-Start").start();
        return START_NOT_STICKY;
    }

    private void enterForeground(String detail) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW
        ));
        PendingIntent open = PendingIntent.getActivity(
            this, 0, new Intent(this, TvActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent stop = PendingIntent.getService(
            this, 1, new Intent(this, TvVpnService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(detail)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_disconnect), stop)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void connect(int expectedEpoch) {
        ParcelFileDescriptor candidateTun = null;
        int candidatePid = 0;
        File config = null;
        try {
            TvServerProfile profile = TvProfileStore.load(this);
            String body = TvXrayConfig.build(profile);
            // Only private no-backup storage. Config is deleted on stop.
            config = new File(getNoBackupFilesDir(), "tv-xray.json");
            File staging = new File(getNoBackupFilesDir(), "tv-xray-pending.json");
            try {
                Files.write(staging.toPath(), body.getBytes(StandardCharsets.UTF_8));
                staging.setReadable(false, false);
                staging.setReadable(true, true);
                staging.setWritable(false, false);
                staging.setWritable(true, true);
                Files.move(staging.toPath(), config.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                staging.delete();
            }
            if (!fresh(expectedEpoch)) return;
            // Include the entire IPv4/IPv6 route; the VPN app itself is exempt
            // so Xray's upstream connections don't loop into their own TUN.
            Builder builder = new Builder()
                .setSession("Vitrum TV")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDisallowedApplication(getPackageName());
            candidateTun = builder.establish();
            if (candidateTun == null) throw new IllegalStateException("TUN not granted");
            if (!fresh(expectedEpoch)) return;

            File executable = new File(
                getApplicationInfo().nativeLibraryDir, "libxray.so"
            );
            File logs = new File(getNoBackupFilesDir(), "tv-xray.log");
            candidatePid = TvNativeProcess.spawn(
                executable.getAbsolutePath(), config.getAbsolutePath(),
                getFilesDir().getAbsolutePath(), logs.getAbsolutePath(), candidateTun.getFd()
            );
            if (candidatePid <= 0) throw new IllegalStateException("Xray spawn failed");
            Thread.sleep(1000);
            if (!TvNativeProcess.alive(candidatePid)) {
                throw new IllegalStateException("Xray terminated immediately");
            }
            synchronized (lock) {
                if (expectedEpoch != epoch) return;
                tun = candidateTun;
                candidateTun = null;
                childPid = candidatePid;
                candidatePid = 0;
                // This reports process+TUN only, NEVER verified network traffic.
                state = State.RUNNING_UNVERIFIED;
                failure = "";
            }
            enterForeground(getString(R.string.vpn_status_unverified));
            monitor(expectedEpoch);
        } catch (Exception | LinkageError error) {
            if (fresh(expectedEpoch)) {
                stopRequested();
                recordFailure();
                stopSelf();
            }
        } finally {
            if (candidatePid > 0) TvNativeProcess.terminate(candidatePid);
            if (candidateTun != null) {
                try { candidateTun.close(); } catch (Exception ignored) {}
            }
            // An active Xray instance reads the config at startup, not continuously.
            // Remove the credential-bearing config after the process has started.
            if (config != null) config.delete();
        }
    }

    private boolean fresh(int generation) {
        synchronized (lock) { return generation == epoch; }
    }

    private void monitor(int expectedEpoch) {
        while (fresh(expectedEpoch)) {
            try { Thread.sleep(1500); } catch (InterruptedException e) { break; }
            synchronized (lock) {
                if (expectedEpoch != epoch) return;
                if (childPid <= 0 || !TvNativeProcess.alive(childPid)) {
                    stopRequested();
                    recordFailure();
                    stopSelf();
                    return;
                }
            }
        }
    }

    private void stopRequested() {
        final ParcelFileDescriptor currentTun;
        final int currentPid;
        synchronized (lock) {
            ++epoch;
            currentTun = tun;
            currentPid = childPid;
            tun = null;
            childPid = 0;
            state = State.OFFLINE;
            failure = "";
        }
        if (currentPid > 0) TvNativeProcess.terminate(currentPid);
        if (currentTun != null) {
            try { currentTun.close(); } catch (Exception ignored) {}
        }
        new File(getNoBackupFilesDir(), "tv-xray.json").delete();
        new File(getNoBackupFilesDir(), "tv-xray-pending.json").delete();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override public void onRevoke() {
        stopRequested();
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        stopRequested();
        super.onDestroy();
    }
}
