package app.rayclient.lite;

final class Native {
    static { System.loadLibrary("spawn"); }
    static native int spawn(String exe, String config, String assetDir, String logfile, int tunFd);
    static native void kill(int pid);
    static native boolean alive(int pid);
    private Native() {}
}