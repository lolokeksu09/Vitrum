package app.rayclient

import android.content.Context

/** Слушающий сокет, доступный по 127.0.0.1: порт и владелец (uid). */
class Listener(val port: Int, val uid: Int)

/** uid == null: слушающего сокета с этим портом не нашли (порт мог закрыться). */
class PortOwner(val port: Int, val uid: Int?, val label: String, val self: Boolean)

class PortOwnersResult(val items: List<PortOwner>, val at: Long)

/**
 * «Чьи это порты» (нужен root): «Щит» находит открытые порты на 127.0.0.1, но без root не может сказать, чьи они, потому что
 * /proc/net/tcp закрыт для приложений. С root читаем эти файлы и список пакетов с их uid. Только чтение, ничего не меняется.
 */
object PortOwners {
    private const val LISTEN = "0A"
    // адрес, по которому порт достижим с 127.0.0.1: loopback или «любой»
    private val V4 = setOf("0100007F", "00000000")
    private val V6 = setOf("00000000000000000000000001000000", "00000000000000000000000000000000", "0000000000000000FFFF00000100007F")

    /** Слушающие сокеты на loopback или на всех адресах из текста /proc/net/tcp и /proc/net/tcp6. */
    fun parseListeners(text: String): List<Listener> = text.lineSequence().mapNotNull { line ->
        val t = line.trim().split(Regex("\\s+"))
        if (t.size < 8 || !t[0].endsWith(":") || t[3] != LISTEN) return@mapNotNull null
        val (addr, port) = t[1].split(':').let { if (it.size == 2) it[0] to it[1] else return@mapNotNull null }
        if (addr.uppercase() !in V4 && addr.uppercase() !in V6) return@mapNotNull null
        val p = port.toIntOrNull(16) ?: return@mapNotNull null
        val uid = t[7].toIntOrNull() ?: return@mapNotNull null
        Listener(p, uid)
    }.distinctBy { it.port to it.uid }.toList()

    /** Из вывода `cmd package list packages -U`: uid → пакеты. Строки вида «package:com.example uid:10123». */
    fun parsePackages(text: String): Map<Int, List<String>> {
        val m = LinkedHashMap<Int, MutableList<String>>()
        for (line in text.lineSequence()) {
            val t = line.trim().split(Regex("\\s+"))
            val pkg = t.firstOrNull { it.startsWith("package:") }?.removePrefix("package:") ?: continue
            val uid = t.firstOrNull { it.startsWith("uid:") }?.removePrefix("uid:")?.toIntOrNull() ?: continue
            m.getOrPut(uid) { mutableListOf() } += pkg
        }
        return m
    }

    fun label(uid: Int, pkgs: Map<Int, List<String>>): String {
        pkgs[uid]?.takeIf { it.isNotEmpty() }?.let { return it.take(3).joinToString(", ") + if (it.size > 3) " +${it.size - 3}" else "" }
        return when {
            uid == 0 -> "root"
            uid < 10000 -> "системная служба (uid $uid)"
            else -> "неизвестное приложение (uid $uid)"
        }
    }

    fun owners(ports: List<Int>, listeners: List<Listener>, pkgs: Map<Int, List<String>>, myUid: Int): List<PortOwner> =
        ports.sorted().flatMap { port ->
            val l = listeners.filter { it.port == port }
            if (l.isEmpty()) listOf(PortOwner(port, null, "владелец не найден: порт мог закрыться", false))
            else l.map { PortOwner(port, it.uid, label(it.uid, pkgs), it.uid == myUid) }
        }

    /** Запуск в фоне. Порты берутся из последней проверки сети «Щита». */
    fun run(ctx: Context) {
        if (AppState.ownersBusy) return
        if (!AppState.rootOn) { AppState.message = "Root-функции выключены. Включите их в «Настройки → Сервис → Root»."; return }
        val ports = AppState.shieldNet?.ports.orEmpty()
        if (ports.isEmpty()) { AppState.message = "Нет открытых портов в последней проверке сети."; return }
        AppState.ownersBusy = true
        val myUid = android.os.Process.myUid()
        Thread {
            try {
                if (!RootShell.available()) { AppState.message = "Нет root-доступа. Выдайте его приложению в менеджере root и повторите."; return@Thread }
                val tcp = RootShell.run("cat /proc/net/tcp /proc/net/tcp6")
                if (!tcp.ok && tcp.out.isBlank()) { AppState.message = "Не удалось прочитать список сокетов."; return@Thread }
                val pk = RootShell.run("cmd package list packages -U").out
                AppState.portOwners = PortOwnersResult(owners(ports, parseListeners(tcp.out), parsePackages(pk), myUid), System.currentTimeMillis())
            } finally { AppState.ownersBusy = false }
        }.start()
    }
}
