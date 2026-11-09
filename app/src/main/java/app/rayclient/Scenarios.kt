package app.rayclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Сценарий для одного типа сети. vpn: 0 не менять, 1 включить, 2 выключить. profile: id профиля маршрутизации или "" (не менять). ks: 0 как в настройках, 1 включить, 2 выключить. */
data class Scen(val vpn: Int = 0, val profile: String = "", val ks: Int = 0)

object Scenarios {
    const val WIFI = 0; const val MOBILE = 1; const val ROAMING = 2; const val OPEN = 3
    val names = listOf("Wi-Fi", "Мобильная сеть", "Роуминг", "Открытая Wi-Fi")

    /** Wi-Fi без пароля: тип защиты сети отдаёт система с Android 12 (SDK 31, WifiInfo.getCurrentSecurityType); SECURITY_TYPE_OPEN = 0. На старых версиях узнать нельзя, там сеть считается обычной. */
    fun isOpenType(securityType: Int?, sdk: Int) = sdk >= 31 && securityType == 0

    @Suppress("NewApi")
    fun isOpenWifi(caps: NetworkCapabilities?): Boolean =
        isOpenType(runCatching { (caps?.transportInfo as? WifiInfo)?.currentSecurityType }.getOrNull(), Build.VERSION.SDK_INT)

    /** Настроен ли сценарий (хоть что-то отличается от «не менять»). */
    fun configured(s: Scen?) = s != null && (s.vpn != 0 || s.profile.isNotEmpty() || s.ks != 0)

    /** Открытая Wi-Fi считается отдельным типом сети, только если для неё настроен сценарий: иначе всё работает как раньше (обычная Wi-Fi). */
    fun effectiveClass(base: Int, open: Boolean, scen: List<Scen>): Int =
        if (base == WIFI && open && configured(scen.getOrNull(OPEN))) OPEN else base

    /** «Доверенная сеть» работает только для обычной Wi-Fi: открытую сеть с таким же названием может поднять кто угодно (открытость видна с Android 12, на старых версиях проверить нечем). */
    fun trustApplies(cls: Int, ssid: String?, list: List<String>, open: Boolean = false) = cls == WIFI && !open && isTrusted(ssid, list)

    fun classify(cm: ConnectivityManager, n: Network?): Int? {
        val caps = (n ?: return null).let { cm.getNetworkCapabilities(it) } ?: return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) ROAMING else MOBILE
            else -> null
        }
    }

    /** Сеть из списка доверенных (точное совпадение названия, регистр важен: так различает Wi-Fi). */
    fun isTrusted(ssid: String?, list: List<String>): Boolean = ssid != null && ssid.isNotEmpty() && list.any { it == ssid }

    /** Название сети без кавычек; null — система название не отдала («<unknown ssid>» без разрешения или при выключенном определении местоположения). */
    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        return if (s.isEmpty() || s == "<unknown ssid>" || s == "0x") null else s
    }

    fun hasLocationPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Название текущей Wi-Fi сети. На Android 12+ его даёт NetworkCapabilities (обратный вызов должен быть создан с FLAG_INCLUDE_LOCATION_INFO), раньше WifiManager. */
    @Suppress("DEPRECATION")
    fun ssidOf(ctx: Context, caps: NetworkCapabilities?): String? {
        if (!hasLocationPermission(ctx)) return null
        val fromCaps = if (Build.VERSION.SDK_INT >= 29) cleanSsid((caps?.transportInfo as? WifiInfo)?.ssid) else null   // transportInfo есть с Android 10
        return fromCaps ?: cleanSsid(runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid }.getOrNull())
    }

    /** Название Wi-Fi, к которой телефон подключён сейчас (мимо VPN). null — нет Wi-Fi, нет разрешения или система не отдала название. */
    @Suppress("DEPRECATION")
    fun currentWifiSsid(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        ssidOf(ctx, caps)
    }.getOrNull()

    /** Тип сети с учётом открытой Wi-Fi (если для неё настроен сценарий). */
    fun classifyEffective(cm: ConnectivityManager, n: Network?): Int? =
        classify(cm, n)?.let { base -> effectiveClass(base, base == WIFI && isOpenWifi(n?.let { cm.getNetworkCapabilities(it) }), AppState.scen) }

    fun current(ctx: Context): Int? = runCatching { val cm = ctx.getSystemService(ConnectivityManager::class.java); classifyEffective(cm, cm.activeNetwork) }.getOrNull()

    internal fun environment(cls: Int, ssid: String?, open: Boolean): NetworkEnvironment = when {
        open -> NetworkEnvironment(NetworkKind.OPEN_WIFI, ssid)
        cls == WIFI -> NetworkEnvironment(NetworkKind.WIFI, ssid)
        cls == MOBILE -> NetworkEnvironment(NetworkKind.MOBILE)
        cls == ROAMING -> NetworkEnvironment(NetworkKind.ROAMING)
        else -> NetworkEnvironment(NetworkKind.OTHER)
    }

    internal fun currentEnvironment(ctx: Context): NetworkEnvironment? = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return null
        val cls = classify(cm, network) ?: return null
        val caps = cm.getNetworkCapabilities(network)
        val ssid = if (cls == WIFI) ssidOf(ctx, caps) else null
        environment(cls, ssid, isOpenWifi(caps))
    }.getOrNull()

    /** Запускает или останавливает службу слежения за сетью по настройке. */
    fun syncService(ctx: Context, apply: Boolean = false) {
        if (AppState.scenariosOn || AppState.networkServerPolicy.enabled) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, ScenarioService::class.java).putExtra("apply", apply)) }
        } else {
            ctx.stopService(Intent(ctx, ScenarioService::class.java))
        }
    }

    fun template() {
        AppState.scen[WIFI] = Scen(vpn = 2); AppState.scen[MOBILE] = Scen(vpn = 1); AppState.scen[ROAMING] = Scen(vpn = 1, ks = 1); AppState.scen[OPEN] = Scen(vpn = 1, ks = 1); AppState.save()
    }

    enum class VpnStep { NONE, START, STOP, NEED_PERMISSION }
    class Plan(val activateProfile: String?, val killSwitch: Int, val vpn: VpnStep)

    /** Чистое решение: что сделать при входе в сеть с таким сценарием. Не трогает ни систему, ни настройки (проверяется тестами). */
    fun plan(s: Scen, activeProfile: String, profileIds: Set<String>, subAvailable: Boolean,
             connected: Boolean, busy: Boolean, blocked: Boolean, hasSelected: Boolean, vpnPermitted: Boolean): Plan {
        val target = s.profile
        val profileOk = target.isNotEmpty() && target != activeProfile && (if (target == "sub") subAvailable else target in profileIds)
        val vpn = when (s.vpn) {
            1 -> if (!connected && !busy && hasSelected) (if (vpnPermitted) VpnStep.START else VpnStep.NEED_PERMISSION) else VpnStep.NONE
            2 -> if (connected || busy || blocked) VpnStep.STOP else VpnStep.NONE
            else -> VpnStep.NONE
        }
        return Plan(if (profileOk) target else null, s.ks, vpn)
    }

    /** Применяет сценарий при смене типа сети: профиль, kill switch, включение или выключение VPN. */
    fun apply(ctx: Context, cls: Int, ssid: String? = null, open: Boolean = false): Boolean {
        val base = AppState.scen.getOrNull(cls) ?: return true
        // в доверенной Wi-Fi сети VPN выключается, остальное из сценария Wi-Fi (профиль, kill switch) остаётся
        val s = if (trustApplies(cls, ssid, AppState.trustedSsids, open)) base.copy(vpn = 2) else base
        val p = plan(s, AppState.activeProfile, AppState.profiles.map { it.id }.toSet(), AppState.subProfile() != null,
            RayVpnService.connected, RayVpnService.busy, RayVpnService.blocked, AppState.selectedId != null, VpnService.prepare(ctx) == null)
        p.activateProfile?.let { AppState.activateProfile(it); if (RayVpnService.connected) RayVpnService.requestReload() }
        AppState.scenarioKs = p.killSwitch
        val environment = environment(cls, ssid, open)
        // Choose the policy server before a scenario starts the VPN, so only one Xray launch is needed.
        if (p.vpn == VpnStep.START) NetworkServerAutomation.apply(ctx, environment)
        performStep(ctx, p.vpn, "Сценарий «${names[cls]}»: нужно разрешение VPN, откройте приложение")
        // A stop or profile reload must settle before a live Xray switch is requested.
        return p.vpn != VpnStep.STOP && p.activateProfile == null
    }

    private fun performStep(ctx: Context, step: VpnStep, needPermissionMessage: String) {
        when (step) {
            VpnStep.START -> runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_START)) }
            VpnStep.NEED_PERMISSION -> AppState.message = needPermissionMessage
            VpnStep.STOP -> ctx.startService(Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_STOP))
            VpnStep.NONE -> {}
        }
    }

    /** Включить (vpn = 1) или выключить (2) VPN без смены профиля и kill switch: для расписания. */
    fun runVpn(ctx: Context, vpn: Int, needPermissionMessage: String) {
        val p = plan(Scen(vpn = vpn), AppState.activeProfile, emptySet(), false, RayVpnService.connected, RayVpnService.busy, RayVpnService.blocked, AppState.selectedId != null, VpnService.prepare(ctx) == null)
        performStep(ctx, p.vpn, needPermissionMessage)
    }
}

/** Следит за типом сети (в уведомлении) и запускает сценарии. Работает, только пока включены «Сценарии». */
class ScenarioService : Service() {
    private var cm: ConnectivityManager? = null
    private var cb: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var pendingPolicyRetry: Runnable? = null
    @Volatile private var latestPolicyEnvironment: NetworkEnvironment? = null
    @Volatile private var policyForce = false
    @Volatile private var policyRetryCount = 0
    @Volatile private var policySequence = 0
    @Volatile private var last: String? = null   // "тип|название Wi-Fi": смена любой из частей запускает сценарий

    override fun onBind(i: Intent?): IBinder? = null

    /** На Android 12+ название Wi-Fi попадает в NetworkCapabilities только если обратный вызов создан с FLAG_INCLUDE_LOCATION_INFO (и есть разрешение на местоположение). */
    private inner class WatchCb : ConnectivityManager.NetworkCallback {
        constructor() : super()
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.S) constructor(flags: Int) : super(flags)
        override fun onAvailable(network: Network) { schedule(network) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { schedule(network) }
    }
    private fun newCallback(): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= 31) WatchCb(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) else WatchCb()

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("scen", "Сценарии", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "scen").setSmallIcon(R.drawable.ic_tile).setContentTitle(BRAND).setContentText(I18n.tr(text)).setContentIntent(open).setOngoing(true)
        if (AppState.privateNotif) b.setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(this, "scen").setSmallIcon(R.drawable.ic_tile).setContentTitle(BRAND).setContentText(I18n.tr("Сценарии по сети включены")).build())
        return b.build()
    }

    private fun schedule(n: Network) {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            val manager = cm ?: return@Runnable
            val base = Scenarios.classify(manager, n) ?: return@Runnable
            val caps = manager.getNetworkCapabilities(n)
            val open = Scenarios.isOpenWifi(caps)
            val ssid = if (base == Scenarios.WIFI) Scenarios.ssidOf(this, caps) else null
            val c = if (base == Scenarios.WIFI && open && AppState.scenariosOn)
                Scenarios.effectiveClass(base, true, AppState.scen) else base
            val key = "$c|${ssid ?: ""}|$open"
            if (key != last) {
                last = key
                val label = Scenarios.names.getOrElse(c) { "сеть" }
                runCatching { getSystemService(NotificationManager::class.java).notify(7, notification("Сценарии: $label")) }
                val environment = Scenarios.environment(base, ssid, open)
                val safe = if (AppState.scenariosOn) Scenarios.apply(this, c, ssid, open) else true
                scheduleNetworkPolicy(environment, force = false, delayMs = if (safe) 0L else 1500L)
            }
        }
        pending = r; handler.postDelayed(r, 1500)   // даём сети «успокоиться», чтобы не реагировать на мигание
    }

    private fun scheduleNetworkPolicy(environment: NetworkEnvironment, force: Boolean, delayMs: Long = 0L) {
        latestPolicyEnvironment = environment
        policyForce = policyForce || force
        val sequence = ++policySequence
        pendingPolicyRetry?.let { handler.removeCallbacks(it) }
        policyRetryCount = 0
        val task = Runnable {
            if (sequence != policySequence) return@Runnable
            attemptNetworkPolicy(sequence)
        }
        pendingPolicyRetry = task
        if (delayMs > 0L) handler.postDelayed(task, delayMs) else handler.post(task)
    }

    private fun attemptNetworkPolicy(sequence: Int) {
        if (sequence != policySequence) return
        val environment = latestPolicyEnvironment ?: return
        if (!AppState.networkServerPolicy.enabled) return
        if (RayVpnService.blocked) return
        if (!RayVpnService.canAutoSwitch()) {
            if (policyRetryCount++ < 8) {
                val retry = Runnable { attemptNetworkPolicy(sequence) }
                pendingPolicyRetry = retry
                handler.postDelayed(retry, 1000L)
            }
            return
        }
        NetworkServerAutomation.apply(this, environment, force = policyForce)
        policyForce = false
        pendingPolicyRetry = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppState.init(this)
        if (!AppState.scenariosOn && !AppState.networkServerPolicy.enabled) { stopSelf(); return START_NOT_STICKY }
        val cur = Scenarios.current(this)
        runCatching {
            ServiceCompat.startForeground(this, 7, notification("Сценарии: " + (cur?.let { Scenarios.names[it] } ?: "сеть не определена")),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }
        // сценарий мог смениться, пока процесс был остановлен: kill switch берём по текущей сети (VPN при этом не трогаем)
        if (AppState.scenariosOn && cur != null) AppState.scenarioKs = AppState.scen.getOrNull(cur)?.ks ?: 0
        val curSsid = if (cur == Scenarios.WIFI || cur == Scenarios.OPEN) Scenarios.ssidOf(this, null) else null
        val curOpen = runCatching { val c = getSystemService(ConnectivityManager::class.java); Scenarios.isOpenWifi(c.getNetworkCapabilities(c.activeNetwork)) }.getOrDefault(false)
        last = cur?.let { "$it|${curSsid ?: ""}|$curOpen" }
        val currentEnvironment = cur?.let { Scenarios.environment(it, curSsid, curOpen) }
        if (currentEnvironment != null) {
            if (intent?.getBooleanExtra("apply", false) == true && AppState.scenariosOn) {
                val safe = Scenarios.apply(this, cur!!, curSsid, curOpen)
                scheduleNetworkPolicy(currentEnvironment, force = true, delayMs = if (safe) 0L else 1500L)
            } else {
                scheduleNetworkPolicy(currentEnvironment, force = true)
            }
        }
        if (cb == null) runCatching {
            val c = getSystemService(ConnectivityManager::class.java); cm = c
            val callback = newCallback()
            cb = callback; c.registerDefaultNetworkCallback(callback)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pending?.let { handler.removeCallbacks(it) }
        pendingPolicyRetry?.let { handler.removeCallbacks(it) }
        policySequence++
        runCatching { cb?.let { cm?.unregisterNetworkCallback(it) } }; cb = null
        super.onDestroy()
    }
}

internal object NetworkServerAutomation {
    private val runtime = NetworkServerPolicyRuntime()

    fun apply(ctx: Context, environment: NetworkEnvironment, force: Boolean = false): Boolean {
        val policy = AppState.networkServerPolicy
        if (!policy.enabled) return true
        if (!RayVpnService.canAutoSwitch() || RayVpnService.blocked) return false
        val available = AppState.servers.map { it.id }.toSet()
        val target = runtime.onNetworkChanged(policy, environment, AppState.selectedId, available, force)
            ?: return true
        if (RayVpnService.connected) {
            runCatching {
                ctx.startService(Intent(ctx, RayVpnService::class.java)
                    .setAction(RayVpnService.ACTION_SWITCH)
                    .putExtra(RayVpnService.EXTRA_SWITCH_SERVER_ID, target)
                    .putExtra(RayVpnService.EXTRA_SWITCH_AUTOMATIC, true))
            }.onFailure { AppState.message = "Не удалось отправить команду смены сервера" }
        } else {
            AppState.selectedId = target
            AppState.save()
        }
        return true
    }

    fun noteManualSelection(ctx: Context) {
        Scenarios.currentEnvironment(ctx)?.let(runtime::noteManualSelection)
    }
}
