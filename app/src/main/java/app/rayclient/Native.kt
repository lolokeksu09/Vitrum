package app.rayclient

/** Путь к Xray: бинарник лежит среди «библиотек» приложения, только оттуда Android 10+ разрешает его запускать. */
fun xrayPath(ctx: android.content.Context): String = java.io.File(ctx.applicationInfo.nativeLibraryDir, "libxray.so").absolutePath

object Native {
    init { System.loadLibrary("spawn") }
    @JvmStatic external fun spawn(exe: String, cfg: String, assets: String, log: String, fd: Int): Int
    @JvmStatic external fun kill(pid: Int)
    @JvmStatic external fun alive(pid: Int): Boolean

    /**
     * Остановить Xray. kill() шлёт SIGTERM, через 300 мс SIGKILL и один раз проверяет без ожидания: если процесс не успел
     * исчезнуть, он оставался «зомби» до конца работы приложения. Добираем его в фоне (до 2 с). Можно звать из любого потока.
     */
    fun stop(pid: Int) {
        if (pid <= 0) return
        kill(pid)
        Thread { repeat(40) { if (!alive(pid)) return@Thread; Thread.sleep(50) } }.start()
    }
}
