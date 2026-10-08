package app.rayclient

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.InputStream
import java.io.OutputStream

/** Соединение с unix-сокетом в приватной папке приложения (другие приложения туда не попадают). */
class LocalConn(path: String, timeoutMs: Int) : Conn {
    private val s = LocalSocket()
    init {
        try { s.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM)); s.soTimeout = timeoutMs }
        catch (e: Exception) { runCatching { s.close() }; throw e }
    }
    override val input: InputStream get() = s.inputStream
    override val output: OutputStream get() = s.outputStream
    override fun close() { runCatching { s.close() } }
}
