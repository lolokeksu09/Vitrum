package app.rayclient

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Проверка того, что видят детекторы на телефоне: открытые локальные порты и IP с VPN и без. */
object LeakCheck {
    val ipRe = Regex("""[0-9a-fA-F:.]{3,45}""")

    /** Первый настоящий IPv4 или IPv6 в ответе службы; слова вроде «facade» и номера версий адресом не считаются. */
    fun parseIp(body: String): String? = ipRe.findAll(body).firstNotNullOfOrNull { m -> listOf(m.value, m.value.trim('.')).firstOrNull { RuleCheck.isIpv4(it) || RuleCheck.isIpv6(it) } }

    private val ipHosts = listOf("api.ipify.org", "icanhazip.com", "ifconfig.me/ip")   // если одна служба недоступна, пробуем следующую

    /** Реальный IP (запрос идёт мимо VPN) и расхождение часов телефона с сервером по заголовку Date (мс, плюс: телефон спешит). Оба null, если ни одна служба не ответила. */
    fun directProbe(): Pair<String?, Long?> {
        for (h in ipHosts) {
            try {
                val c = URL("https://$h").openConnection() as HttpURLConnection
                c.connectTimeout = 5000; c.readTimeout = 5000
                val ip = ipRe.find(c.inputStream.bufferedReader().readText())?.value
                val now = System.currentTimeMillis()
                val date = c.getHeaderFieldDate("Date", 0L)
                if (ip != null) return ip to (if (date > 0L) now - date else null)
            } catch (e: Exception) { }
        }
        return null to null
    }

    /** Реальный IP (запрос идёт мимо VPN) или null, если ни одна служба не ответила. */
    fun directIpOrNull(): String? = directProbe().first

    fun directIp(): String = directIpOrNull() ?: "не удалось определить"

    /** IP на выходе VPN: запрос идёт через служебный сокет. Возвращает (IP или null, причина сбоя). */
    fun vpnIp(connector: Connector): Pair<String?, String> {
        var note = "нет ответа"
        for (h in ipHosts) {
            val r = HeadProbe.get("http://$h", connector)
            if (r.ms >= 0) { ipRe.find(r.body)?.value?.let { return it to "" }; note = "не разобрал ответ" } else note = r.note
            if (note == HeadProbe.PROXY_UNAVAILABLE) break   // сокет недоступен: другие службы не помогут
        }
        return null to note
    }

    /** Порты, которые чаще всего проверяют детекторы и которые используют клиенты (v2rayNG, Clash, sing-box, Hiddify, Xray API и т.д.). */
    private val common: List<Int> = (1..1023).toList() + listOf(1080, 1081, 1085, 1086, 1087, 1088, 1089, 2080, 2081, 2333, 2334, 2335, 3128, 4444, 5555, 6666, 7777,
        7890, 7891, 7892, 7893, 7897, 7898, 7899, 8000, 8001, 8080, 8081, 8082, 8083, 8085, 8086, 8087, 8088, 8089, 8118, 8123, 8888, 8889, 9000, 9001, 9050, 9051,
        9090, 9091, 9097, 9098, 9999, 10000, 10085, 10086, 10808, 10809, 10810, 10811, 10812, 10813, 10814, 10815, 10818, 10819, 10820, 10853, 10854, 12334, 12345,
        15490, 17078, 19090, 20170, 20171, 20172, 25500, 33210, 34567, 41080, 41081, 41082, 45678, 54321, 55555, 62789) +
        (1090..1100) + (2090..2100) + (7880..7900) + (8090..8100) + (9100..9110) + (10800..10830) + (20000..20010) + (30000..30010) + (50000..50010)

    /** Открытые TCP-порты на 127.0.0.1 (любых программ). */
    fun scan(full: Boolean): List<Int> {
        val open = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val pool = Executors.newFixedThreadPool(64)
        try {
            pool.invokeAll((if (full) (1..65535).toList() else common.distinct()).map { p -> Callable { try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", p), 100); open += p } } catch (_: Exception) {} } })
        } finally { pool.shutdown() }
        return open.sorted()
    }

    fun run(full: Boolean = false): String {
        val sb = StringBuilder()
        val t0 = System.currentTimeMillis()
        val open = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val pool = Executors.newFixedThreadPool(64)
        try {
            pool.invokeAll((if (full) (1..65535).toList() else common.distinct()).map { p -> Callable { try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", p), 100); open += p } } catch (_: Exception) {} } })
        } finally { pool.shutdown() }
        val ports = open.sorted()
        sb.appendLine("1. Открытые TCP-порты на 127.0.0.1 (всех программ, " + (if (full) "полное сканирование" else "быстрое: ${common.distinct().size} типичных портов") + "): " + if (ports.isEmpty()) "нет" else ports.joinToString(", "))
        sb.appendLine("   Просмотр занял ${(System.currentTimeMillis() - t0) / 1000} с. Порты ${BRAND} в этом списке не появляются: служебный вход работает через сокет в закрытой папке.")
        val real = directIp()
        sb.appendLine("2. Ваш реальный IP (запрос идёт мимо VPN): $real")
        if (RayVpnService.connected && Pinger.probePath.isNotEmpty()) {
            val r = HeadProbe.get("http://api.ipify.org", Pinger.connector(Pinger.probePath))
            val vpn = if (r.ms >= 0) ipRe.find(r.body)?.value ?: "не разобрал ответ" else "ошибка: ${r.note}"
            sb.appendLine("3. IP через VPN: $vpn")
            if (vpn == real) sb.appendLine("   IP совпадают: трафик, возможно, идёт мимо VPN.")
            if (r.ms < 0) {
                sb.appendLine("   Подробности: ${r.detail.ifBlank { r.note }}")
                val h = HeadProbe.head("http://cp.cloudflare.com/generate_204", Pinger.connector(Pinger.probePath), 6000, false)
                sb.appendLine("   Для сравнения HEAD cp.cloudflare.com через VPN: " + if (h.ms >= 0) "${h.ms} мс" else "нет ответа (${h.note}; ${h.detail})")
                val sv = AppState.servers.firstOrNull { it.id == AppState.selectedId }
                sb.appendLine("   Сервер: ${sv?.name ?: "?"}; подключено ${(System.currentTimeMillis() - RayVpnService.since) / 1000} с")
                val tail = RayVpnService.logTail.lines().filter { it.isNotBlank() }.takeLast(4).joinToString("\n") { it.take(200) }
                if (tail.isNotBlank()) sb.appendLine("   Лог Xray:\n$tail")
            }
        } else sb.appendLine("3. IP через VPN: подключитесь к серверу, чтобы увидеть.")
        sb.appendLine("Запросы IP идут на api.ipify.org.")
        return sb.toString()
    }
}
