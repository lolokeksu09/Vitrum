package app.rayclient

import java.io.BufferedReader
import java.io.Closeable
import java.net.URL

class GameLive(val last: Int, val avg: Int, val jitter: Int, val loss: Int, val samples: List<Int>)
class GameRow(val id: String, val name: String, val avg: Int, val min: Int, val max: Int, val jitter: Int, val loss: Int, val connectMs: Int, val note: String) {
    /** Меньше — лучше: средняя задержка + штраф за джиттер и потери. Сервер без ответов — в конец. */
    val score: Double get() = if (avg < 0) 1e9 else avg + 2.0 * jitter + 8.0 * loss
}
class Target(val host: String, val port: Int, val path: String)

object GameStats {
    /** rtts: задержки в мс, -1 = потеря. Джиттер — среднее изменение между соседними удачными замерами. */
    fun live(rtts: List<Int>): GameLive {
        val oks = rtts.filter { it >= 0 }
        val avg = if (oks.isEmpty()) -1 else Math.round(oks.average()).toInt()
        var j = 0.0; if (oks.size >= 2) j = oks.zipWithNext { a, b -> Math.abs(b - a).toDouble() }.average()
        val loss = if (rtts.isEmpty()) 0 else Math.round(100.0 * (rtts.size - oks.size) / rtts.size).toInt()
        return GameLive(rtts.lastOrNull() ?: -1, avg, Math.round(j).toInt(), loss, rtts)
    }

    fun row(id: String, name: String, s: Series): GameRow {
        val l = live(s.rtts); val oks = s.rtts.filter { it >= 0 }
        return GameRow(id, name, l.avg, oks.minOrNull() ?: -1, oks.maxOrNull() ?: -1, l.jitter, l.loss, s.connectMs, s.note)
    }

    /** "http://адрес/путь" или "хост[:порт]" (тогда HEAD на "/"). Замер всегда по HTTP: Xray отвечает на CONNECT сразу, не дожидаясь сервера, поэтому время самого соединения так не измерить. */
    fun parseTarget(s: String): Target? {
        val t = s.trim(); if (t.isEmpty()) return null
        return runCatching {
            if (t.startsWith("http://") || t.startsWith("https://")) {
                val u = URL(HeadProbe.plain(t)); Target(u.host, if (u.port > 0) u.port else 80, u.file.ifEmpty { "/" })
            } else {
                val i = t.lastIndexOf(':')
                val host = if (i > 0) t.substring(0, i) else t
                if (!Regex("^[A-Za-z0-9._-]+$").matches(host)) return null
                Target(host, if (i > 0) t.substring(i + 1).toInt() else 80, "/")
            }
        }.getOrNull()
    }
}

class Series(val connectMs: Int, val rtts: List<Int>, val note: String)

/**
 * Постоянный туннель CONNECT через HTTP-прокси Xray: по нему идут HEAD-запросы с keep-alive,
 * поэтому измеряется «чистая» задержка пути без повторных рукопожатий.
 */
class Tunnel(private val c: Connector, private val host: String, private val port: Int, private val path: String, private val timeout: Int = 3000) : Closeable {
    private var conn: Conn? = null
    private var rd: BufferedReader? = null
    var connectMs = -1; private set
    var note = ""; private set

    fun open(): Int {
        close()
        val t0 = System.nanoTime()
        return try {
            val cn = c.open(timeout)
            cn.output.write("CONNECT $host:$port HTTP/1.1\r\nHost: $host:$port\r\n\r\n".toByteArray()); cn.output.flush()
            val r = cn.input.bufferedReader()
            val status = r.readLine()
            if (status == null) { cn.close(); note = "сервер закрыл соединение"; -1 }
            else {
                while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }
                if (!status.contains(" 200")) { cn.close(); note = "сервер ответил: ${status.take(40)}"; -1 }
                else { conn = cn; rd = r; connectMs = ((System.nanoTime() - t0) / 1_000_000).toInt(); connectMs }
            }
        } catch (e: Exception) { note = "локальный прокси недоступен или таймаут"; -1 }
    }

    /** Один замер в мс или -1 (потеря). */
    fun ping(): Int {
        val r = rd ?: return -1; val cn = conn ?: return -1
        val t0 = System.nanoTime()
        return try {
            cn.output.write("HEAD $path HTTP/1.1\r\nHost: $host\r\nConnection: keep-alive\r\nUser-Agent: RayClient\r\n\r\n".toByteArray()); cn.output.flush()
            val line = r.readLine()
            if (line == null || !line.startsWith("HTTP/")) { note = "туннель закрыт"; close(); -1 }
            else { while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }; ((System.nanoTime() - t0) / 1_000_000).toInt() }
        } catch (e: Exception) { note = "таймаут"; close(); -1 }
    }

    override fun close() { runCatching { conn?.close() }; conn = null; rd = null }
}

object Series_ {
    fun run(connector: Connector, t: Target, n: Int, timeout: Int = 3000, gapMs: Long = 150): Series {
        val tun = Tunnel(connector, t.host, t.port, t.path, timeout)
        val c0 = tun.open()
        if (c0 < 0) return Series(-1, List(n) { -1 }, tun.note)
        val rtts = ArrayList<Int>()
        try {
            tun.ping()                                       // прогрев: первый запрос включает установку соединения сервера с целью, его не считаем
            for (i in 0 until n) {
                val r = tun.ping()
                if (r < 0 && tun.open() >= 0) tun.ping()     // туннель порвался: переоткрываем и прогреваем для следующего замера
                rtts += r; Thread.sleep(gapMs)
            }
        } finally { tun.close() }
        return Series(c0, rtts, tun.note)
    }
}
