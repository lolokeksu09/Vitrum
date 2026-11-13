package app.rayclient.tv;

/**
 * Process boundary for a pinned Android ARM32 Xray binary. Its input path
 * and TUN descriptor come only from the application's own service.
 */
final class TvNativeProcess {
    static {
        System.loadLibrary("tvspawn");
    }

    private TvNativeProcess() {}

    static native int spawn(
        String executable, String configPath, String assetsDirectory,
        String logPath, int tunFd
    );

    static native void terminate(int pid);
    static native boolean alive(int pid);
}
