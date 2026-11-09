package app.rayclient

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Пошаговая проверка одного сервера: где именно обрывается цепочка. */
object Diagnostics {
    fun run(sv: Server): String {
        val out = StringBuilder()
        fun line(s: String) { out.append(s).append('\n') }
        val ctx = AppState.context()
        line("Сервер: ${sv.name}")
        line("Время устройства: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
        if (sv.isAuto) return out.append("Авто-сервер: сервер выбирает балансировщик Xray из конфигурации панели, проверка одной ссылки недоступна. Подключитесь и запустите «Проверка портов и IP».").toString()
        val p = try { Links.parse(sv.link) } catch (e: Exception) { return out.append("Ссылка не разобрана: ${e.message}").toString() }
        val net = p.outbound.optJSONObject("streamSettings")
        val security = net?.optString("security") ?: "none"
        val network = net?.optString("network") ?: "tcp"
        line("Протокол: ${p.proto}, сеть: $network, защита: $security")
        val ip = try { java.net.InetAddress.getAllByName(p.host).let { a -> (a.firstOrNull { it is java.net.Inet4Address } ?: a.first()).hostAddress!! } }
            catch (e: Exception) { line("1. Адрес ${p.host}: не найден (${e.javaClass.simpleName})"); return out.toString() }
        line("1. Адрес ${p.host} → $ip")
        // 2. TCP
        if (p.proto != "hysteria2") {
            val t0 = System.nanoTime()
            line(try { Socket().use { it.connect(InetSocketAddress(ip, p.port), 4000) }; "2. TCP ${p.port}: открыт, ${(System.nanoTime() - t0) / 1_000_000} мс" }
                catch (e: Exception) { "2. TCP ${p.port}: ${e.javaClass.simpleName} ${e.message ?: ""}" })
        } else line("2. TCP пропущен: Hysteria2 работает по UDP")
        // 3. TLS-рукопожатие без отправки данных
        if (security == "tls" || security == "reality") {
            val sni = net?.optJSONObject(if (security == "tls") "tlsSettings" else "realitySettings")?.optString("serverName") ?: p.host
            val t0 = System.nanoTime()
            line(try {
                val sc = SSLContext.getInstance("TLS").apply { init(null, null, null) }   // системные сертификаты: чужие и самоподписанные покажутся ошибкой, это тоже полезно знать
                val raw = Socket(); raw.connect(InetSocketAddress(ip, p.port), 4000); raw.soTimeout = 5000
                val s = sc.socketFactory.createSocket(raw, sni, p.port, true) as SSLSocket
                s.sslParameters = s.sslParameters.apply { serverNames = listOf(SNIHostName(sni)) }
                s.startHandshake()
                val cert = (s.session.peerCertificates.firstOrNull() as? X509Certificate)?.subjectX500Principal?.name?.take(70) ?: "?"
                val r = "3. TLS с SNI $sni: рукопожатие прошло (${s.session.protocol}, ${(System.nanoTime() - t0) / 1_000_000} мс), сертификат: $cert"
                s.close(); r
            } catch (e: Exception) { "3. TLS с SNI $sni: ${e.javaClass.simpleName} ${e.message?.take(80) ?: ""}" })
        }
        // 4. Xray: HEAD строго через этот сервер (через сокет в приватной папке, без портов)
        try {
            val dir = File(ctx.filesDir, "probe-diag").apply { deleteRecursively(); mkdirs() }
            val cfg = File(ctx.filesDir, "diag-config.json").apply { writeText(ConfigBuilder.buildProbe(listOf(p to ip), dir.absolutePath, "info")) }
            val log = File(ctx.filesDir, "diag.log").apply { writeText("") }
            val pid = Native.spawn(xrayPath(ctx), cfg.absolutePath, ctx.filesDir.absolutePath, log.absolutePath, -1)
            val sock = File(dir, "p0.sock")
            try {
                var up = false
                repeat(40) { if (!up) { up = sock.exists(); if (!up) Thread.sleep(150) } }
                if (!up) line("4. Xray не запустился или сокет не создан") else {
                    val r = HeadProbe.head(AppState.pingUrl, Pinger.connector(sock.absolutePath))
                    line("4. HEAD ${HeadProbe.plain(AppState.pingUrl)} через сервер: " + if (r.ms >= 0) "${r.ms} мс" else "нет ответа (${r.note})")
                }
            } finally { Native.stop(pid); runCatching { dir.deleteRecursively() } }
            val tail = runCatching { log.readLines().filter { it.isNotBlank() }.takeLast(8).joinToString("\n") { it.replace(Regex("^\\d{4}/\\d\\d/\\d\\d "), "").take(220) } }.getOrDefault("")
            if (tail.isNotBlank()) { line("Лог Xray:"); line(tail) }
            runCatching { cfg.delete(); log.delete() }
        } catch (e: Exception) { line("4. Ошибка: ${e.message}") }
        return out.toString()
    }
}
