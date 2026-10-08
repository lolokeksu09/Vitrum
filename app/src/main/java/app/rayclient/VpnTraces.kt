package app.rayclient

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.io.File
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** SEEN — признак VPN виден, CLEAN — не виден, UNKNOWN — измерить не удалось (это не «чисто»). */
enum class TraceState { SEEN, CLEAN, UNKNOWN }

class TraceItem(val key: String, val title: String, val state: TraceState, val detail: String)

/** Итог проверки; session нужен, чтобы пометить результат прошлого подключения. */
class TraceResult(val items: List<TraceItem>, val at: Long, val session: Long)

/** Сырые наблюдения; null — проба не отработала. Без типов Android, чтобы логику проверяли обычные тесты. */
class TraceSnapshot(
    val activeVpnTransport: Boolean? = null,        // у активной сети транспорт VPN
    val activeNotVpn: Boolean? = null,              // у активной сети есть признак «не VPN» (true — обычная сеть)
    val transportInfoVpn: Boolean? = null,          // в данных транспорта есть VpnTransportInfo
    val vpnNetworks: Int? = null,                   // сколько сетей с транспортом VPN в общем списке
    val activeIfaces: List<String>? = null,         // интерфейс и маршруты активной сети
    val legacyVpnConnected: Boolean? = null,        // устаревший тип сети VPN подключён
    val ifaces: List<String>? = null,               // NetworkInterface.getNetworkInterfaces()
    val callbackVpn: Boolean? = null,               // в данных, присланных системой приложению, есть признак VPN
    val procRoute: List<String>? = null,            // интерфейсы из /proc/net/route (null — файл закрыт)
    val procDev: List<String>? = null,              // интерфейсы из /proc/net/dev
    val sysNet: List<String>? = null,               // каталоги /sys/class/net
)

/**
 * «Что видят другие приложения»: проверки, которые делает обычное приложение без root (ConnectivityManager, список интерфейсов, файлы /proc и /sys).
 * Измеряется из процесса Vitrum. Он сам идёт мимо туннеля, поэтому это вид приложения вне VPN (например банка, исключённого из туннеля).
 * Результат у конкретного приложения может отличаться (SELinux, прошивка). В оценку «Щита» не входит.
 */
object VpnTraces {
    /** Имена VPN-интерфейсов. Узкий шаблон: tunl0, ip6tnl0, sit0 и ipsec/xfrm (их заводят операторы для IMS) VPN не считаем. */
    private val vpnIfaceRe = Regex("^(tun|tap|wg|awg|ppp|utun)\\d+$|^l2tp.*$|.*vpn.*", RegexOption.IGNORE_CASE)

    fun isVpnIface(name: String): Boolean = vpnIfaceRe.matches(name.trim())

    /** Интерфейсы из текста /proc/net/route: первая колонка, без заголовка. */
    fun routeNames(text: String): List<String> =
        text.lineSequence().drop(1).map { it.trim().split(Regex("\\s+")).firstOrNull().orEmpty() }.filter { it.isNotEmpty() }.distinct().toList()

    /** Интерфейсы из текста /proc/net/dev: имя до двоеточия, две строки заголовка пропускаются. */
    fun devNames(text: String): List<String> =
        text.lineSequence().drop(2).map { it.substringBefore(':', "").trim() }.filter { it.isNotEmpty() }.distinct().toList()

    private fun names(key: String, title: String, found: List<String>?, closed: String): TraceItem {
        if (found == null) return TraceItem(key, title, TraceState.UNKNOWN, closed)
        val vpn = found.filter(::isVpnIface)
        return if (vpn.isNotEmpty()) TraceItem(key, title, TraceState.SEEN, "Виден интерфейс: ${vpn.joinToString()}.")
        else TraceItem(key, title, TraceState.CLEAN, "Интерфейсов, похожих на VPN, не видно.")
    }

    fun evaluate(s: TraceSnapshot): List<TraceItem> {
        val l = mutableListOf<TraceItem>()
        val capsSeen = if (s.activeVpnTransport == null && s.activeNotVpn == null) null else (s.activeVpnTransport == true || s.activeNotVpn == false)
        l += when (capsSeen) {
            true -> TraceItem("caps", "Признак VPN у активной сети", TraceState.SEEN, "Активная сеть помечена как VPN.")
            false -> TraceItem("caps", "Признак VPN у активной сети", TraceState.CLEAN, "Активная сеть не помечена как VPN.")
            null -> TraceItem("caps", "Признак VPN у активной сети", TraceState.UNKNOWN, "Не удалось получить данные активной сети.")
        }
        l += when (s.transportInfoVpn) {
            true -> TraceItem("transport_info", "Данные транспорта сети", TraceState.SEEN, "В данных сети есть VpnTransportInfo.")
            false -> TraceItem("transport_info", "Данные транспорта сети", TraceState.CLEAN, "Данных VPN в транспорте сети нет.")
            null -> TraceItem("transport_info", "Данные транспорта сети", TraceState.UNKNOWN, "Недоступно (Android ниже 10) или не удалось получить.")
        }
        l += when {
            s.vpnNetworks == null -> TraceItem("all_networks", "Список всех сетей", TraceState.UNKNOWN, "Не удалось получить список сетей.")
            s.vpnNetworks > 0 -> TraceItem("all_networks", "Список всех сетей", TraceState.SEEN, "В списке сетей есть VPN: ${s.vpnNetworks}.")
            else -> TraceItem("all_networks", "Список всех сетей", TraceState.CLEAN, "В списке сетей VPN нет.")
        }
        l += names("link", "Интерфейс и маршруты активной сети", s.activeIfaces, "Не удалось получить свойства активной сети.")
        l += when (s.legacyVpnConnected) {
            true -> TraceItem("legacy", "Устаревший тип сети VPN", TraceState.SEEN, "Система сообщает о подключённой сети типа VPN.")
            false -> TraceItem("legacy", "Устаревший тип сети VPN", TraceState.CLEAN, "Сеть типа VPN не подключена.")
            null -> TraceItem("legacy", "Устаревший тип сети VPN", TraceState.UNKNOWN, "Не удалось получить ответ системы.")
        }
        l += names("iface_list", "Список сетевых интерфейсов", s.ifaces, "Не удалось получить список интерфейсов.")
        l += when (s.callbackVpn) {
            true -> TraceItem("callback", "Данные, которые система присылает приложению", TraceState.SEEN, "Система присылает данные сети с признаком VPN.")
            false -> TraceItem("callback", "Данные, которые система присылает приложению", TraceState.CLEAN, "Присланные данные сети без признака VPN.")
            null -> TraceItem("callback", "Данные, которые система присылает приложению", TraceState.UNKNOWN, "Данные не пришли за 3 секунды.")
        }
        val closed = "Файл закрыт системой: приложение его прочитать не может. Это не значит, что VPN скрыт."
        l += names("proc_route", "/proc/net/route", s.procRoute, closed)
        l += names("proc_dev", "/proc/net/dev", s.procDev, closed)
        l += names("sys_net", "/sys/class/net", s.sysNet, closed)
        return l
    }

    /** (сколько проверок показали признак VPN, сколько проверок удалось измерить). */
    fun summary(items: List<TraceItem>): Pair<Int, Int> =
        items.count { it.state == TraceState.SEEN } to items.count { it.state != TraceState.UNKNOWN }

    /** Блокирующий опрос системы (до 3 с на ожидание данных от системы): вызывать не из главного потока. */
    @Suppress("DEPRECATION")
    fun probe(ctx: Context): TraceSnapshot {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return TraceSnapshot()
        val active = runCatching { cm.activeNetwork }.getOrNull()
        val caps = active?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val lp = active?.let { runCatching { cm.getLinkProperties(it) }.getOrNull() }
        return TraceSnapshot(
            activeVpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            activeNotVpn = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            transportInfoVpn = if (Build.VERSION.SDK_INT >= 29) caps?.let { runCatching { it.transportInfo?.javaClass?.name?.contains("VpnTransportInfo") == true }.getOrNull() } else null,
            vpnNetworks = runCatching { cm.allNetworks.count { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true } }.getOrNull(),
            activeIfaces = lp?.let { p -> listOfNotNull(p.interfaceName) + p.routes.mapNotNull { it.`interface` } },
            legacyVpnConnected = runCatching { cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected }.getOrNull(),
            ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList()?.map { it.name } }.getOrNull(),
            callbackVpn = callbackVpn(cm),
            procRoute = runCatching { routeNames(File("/proc/net/route").readText()) }.getOrNull(),
            procDev = runCatching { devNames(File("/proc/net/dev").readText()) }.getOrNull(),
            sysNet = runCatching { File("/sys/class/net").list()?.toList() }.getOrNull())
    }

    private fun callbackVpn(cm: ConnectivityManager): Boolean? {
        val latch = CountDownLatch(1)
        val seen = AtomicReference<Boolean?>(null)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) {
                seen.set(c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                latch.countDown()
            }
        }
        return try {
            cm.registerDefaultNetworkCallback(cb)
            latch.await(3, TimeUnit.SECONDS)
            seen.get()
        } catch (_: Exception) { null } finally { runCatching { cm.unregisterNetworkCallback(cb) } }
    }

    /** Запуск проверки в фоне; результат кладётся в AppState.traceResult. Без VPN проверять нечего: все признаки были бы «не видны». */
    fun run(ctx: Context) {
        if (AppState.traceBusy) return
        if (!RayVpnService.connected) { AppState.message = "Сначала подключитесь к серверу: без VPN проверять нечего."; return }
        AppState.traceBusy = true
        val app = ctx.applicationContext; val session = RayVpnService.since
        Thread {
            try { AppState.traceResult = TraceResult(evaluate(probe(app)), System.currentTimeMillis(), session) }
            finally { AppState.traceBusy = false }
        }.start()
    }
}
