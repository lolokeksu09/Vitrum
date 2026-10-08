@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.pm.ApplicationInfo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private var locked by mutableStateOf(false)

    override fun onStop() { super.onStop(); if (!isChangingConfigurations) AppLock.leftAt = android.os.SystemClock.elapsedRealtime() }
    override fun onStart() {
        super.onStart()
        // время ухода хранится в AppLock, а не в Activity: после поворота или пересоздания окна срок всё равно учитывается, а признак входа сбрасывается
        val left = AppLock.leftAt
        if (left != 0L && AppLock.expired(AppState.appLock, left, android.os.SystemClock.elapsedRealtime()) && AppLock.available(this)) { AppLock.unlocked = false; locked = true }
        AppLock.leftAt = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.init(this)
        // поворот экрана пересоздаёт окно, но процесс жив и вход уже подтверждён; после смерти процесса флаг сброшен, и запрос будет снова
        locked = AppState.appLock && AppLock.available(this) && !Demo.requested(this, intent) && !(AppLock.unlocked && savedInstanceState != null)
        AppLock.unlocked = !locked
        // защита окна ставится до первого кадра, а не после первой композиции
        if (AppState.secureScreen || AppState.appLock) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        Demo.apply(this, intent)   // только отладочная сборка и только по команде adb (скриншоты в CI)
        AppState.autoUpdate()
        Scheduler.apply(this)
        Scheduler.applyMonitor(this)
        VpnSchedule.enqueue(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = when (AppState.theme) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
            // «Скрывать экран от скриншотов»: снимки и запись экрана запрещены, в недавних приложениях пустое окно
            val secure = AppState.secureScreen || AppState.appLock
            DisposableEffect(secure) {
                if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {}
            }
            // targetSdk 35 включает «от края до края» принудительно: делаем так на всех версиях, цвет значков в системных панелях следует за нашей темой
            DisposableEffect(dark) {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            RayTheme(dark) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (locked) LockScreen { AppLock.unlocked = true; locked = false } else Root() } }
        }
    }

    internal var scanCamera: () -> Unit = {}
    internal var scanImage: () -> Unit = {}

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    /** Ссылка из браузера или мессенджера (vless://… и т.п.) либо «Поделиться → ${BRAND}». */
    private fun handleIntent(i: Intent?) {
        // ярлык значка «Подключить»: только включает VPN (если разрешение VPN уже выдано, иначе обычный запрос); «отключить» через намерение сознательно нет
        if (i?.action == "app.rayclient.action.CONNECT") { AppState.pendingConnect = true; return }
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()
        if (!text.isNullOrEmpty() && (Links.isSupported(text) || text.startsWith("http://") || text.startsWith("https://"))) AppState.pendingInput = text
    }

    private fun decodeQr(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px)))).text
    }.getOrNull()

    private fun describePending(text: String): String {
        val t = text.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) {
            val host = runCatching { java.net.URL(t).host }.getOrDefault("?")
            return "Подписка с адреса $host.\nПриложение скачает с него список серверов." + if (t.startsWith("http://")) "\nАдрес без шифрования (http) не поддерживается." else ""
        }
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ok = lines.mapNotNull { runCatching { Links.parse(it) }.getOrNull() }
        if (ok.isEmpty()) return "Не удалось разобрать ссылку."
        val p = ok.first()
        return (if (ok.size > 1) "Серверов: ${ok.size}. Первый: " else "Сервер: ") + "${p.name}\n${p.proto.uppercase()} · ${p.host}:${p.port}"
    }

    /** sensitive: Android 13+ не покажет текст во всплывающем окне буфера (ссылки серверов и подписок содержат ключи). */
    internal fun copy(text: String, sensitive: Boolean = true) {
        val clip = ClipData.newPlainText("link", text)
        if (sensitive) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        AppState.message = "Скопировано"
    }

    internal fun clipboardText(): String =
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()

    @Composable
    private fun Root() {
        var tab by remember { mutableIntStateOf(Demo.tab) }
        var servers by remember { mutableStateOf(Demo.servers) }   // шторка со списком серверов поверх главной
        val ctx = this
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) ContextCompat.startForegroundService(ctx, Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_START))
        }
        val toggle: () -> Unit = {
            if (RayVpnService.connected || RayVpnService.busy || RayVpnService.blocked)
                ctx.startService(Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_STOP))
            else {
                val i = VpnService.prepare(ctx)
                if (i != null) permission.launch(i)
                else ContextCompat.startForegroundService(ctx, Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_START))
            }
        }
        val quick: () -> Unit = {
            if (!RayVpnService.connected && !RayVpnService.busy && !AppState.quickBusy && AppState.servers.isNotEmpty()) {
                AppState.quickBusy = true
                val pool = AppState.servers.filter { it.link in AppState.favs }.ifEmpty { AppState.servers.toList() }
                Pinger.viaServers(pool, fast = true) { best ->
                    AppState.quickBusy = false
                    if (best != null) { AppState.selectedId = best; AppState.save(); runOnUiThread { toggle() } }
                    else AppState.message = "Не нашёл отвечающих серверов"
                }
            }
        }
        LaunchedEffect(AppState.pendingConnect) {
            if (!AppState.pendingConnect) return@LaunchedEffect
            AppState.pendingConnect = false
            if (AppState.selectedId == null) AppState.message = "Выберите сервер на главном экране"
            else if (!RayVpnService.connected && !RayVpnService.busy && !RayVpnService.blocked) toggle()
        }
        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { AppState.pendingInput = it } }
        val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { u -> Thread { val t = decodeQr(u); if (t != null) AppState.pendingInput = t else AppState.message = "QR-код на картинке не найден" }.start() }
        }
        scanCamera = { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Наведите камеру на QR-код").setBeepEnabled(false).setOrientationLocked(false)) }
        scanImage = { pickImg.launch("image/*") }
        LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            if (AppState.autoOpen && AppState.selectedId != null && !RayVpnService.connected && !RayVpnService.busy) toggle()
            AppState.autoCheckUpdate()
            if (AppState.scenariosOn) Scenarios.syncService(ctx)
        }
        // «Проверять защиту после подключения» (по умолчанию выключено): ждём, пока туннель поднимется, запускаем проверку сети и сообщаем только о проблеме
        val tunnelUp = RayVpnService.connected
        // короткая отдача при успешном подключении; если приложение открыли уже при работающем VPN, не вибрируем (начальное значение = текущее)
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        var wasUp by remember { mutableStateOf(tunnelUp) }
        LaunchedEffect(tunnelUp) {
            if (tunnelUp && !wasUp) haptic.tap(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            // один раз после первого подключения: если телефон может закрывать VPN в фоне, подсказываем, где это поправить
            if (tunnelUp && !wasUp && !AppState.bgHintShown) {
                AppState.bgHintShown = true; AppState.save()
                if (!Background.ignoringBatteryOptimizations(ctx)) AppState.message = "Чтобы VPN не отключался в фоне: Настройки → Подключение → Работа в фоне"
            }
            wasUp = tunnelUp
        }
        LaunchedEffect(tunnelUp, AppState.shieldAuto) {
            if (!tunnelUp || !AppState.shieldAuto) return@LaunchedEffect
            val session = RayVpnService.since
            kotlinx.coroutines.delay(8_000)
            if (!RayVpnService.connected || RayVpnService.since != session) return@LaunchedEffect
            Shield.runNet()
            var waited = 0
            while (AppState.shieldBusy && waited < 60_000) { kotlinx.coroutines.delay(500); waited += 500 }
            val checks = Shield.evaluate(ctx)
            if (Shield.hasProblem(checks, Shield.score(checks))) AppState.message = "Щит: найдена проблема. Откройте «Щит приватности»."
        }
        LaunchedEffect(AppState.message) { if (AppState.message.isNotEmpty()) { Toast.makeText(ctx, I18n.tr(AppState.message), Toast.LENGTH_LONG).show(); AppState.message = "" } }
        // живой фон под всеми вкладками; цвет следует за состоянием подключения
        Box(Modifier.fillMaxSize()) {
            AuroraBackground(connectionColor())
            // навигация плавает над содержимым: вкладки прокручиваются под неё, снизу мягкое затемнение, чтобы подписи читались
            val bg = MaterialTheme.colorScheme.background
            Scaffold(containerColor = Color.Transparent, bottomBar = {
                Box {
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.9f)))))
                    FloatingNav(tab) { tab = it }
                }
            }) { pad ->
                CompositionLocalProvider(LocalTabBottom provides pad.calculateBottomPadding()) {
                Box(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
                    // вкладка въезжает с той стороны, куда идёт переход, с лёгким приближением
                    AnimatedContent(tab, transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(280, 60)) + slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { w -> dir * w / 8 } + scaleIn(tween(360), initialScale = 0.97f)) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { w -> -dir * w / 8 })
                    }, label = "tab") { t -> when (t) { 0 -> HomeTab(toggle, quick, still = servers) { servers = true }; else -> SettingsTab() } }
                }
                }
            }
        }
        ServersSheet(servers) { servers = false }
        if (AppState.showAdd) AddDialog { AppState.showAdd = false }
        if (!AppState.onboarded) OnboardingDialog()
        AppState.pendingInput?.let { text ->
            val httpPlain = text.startsWith("http://")
            AlertDialog(onDismissRequest = { AppState.pendingInput = null }, title = { Text("Добавить из внешней ссылки?") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(describePending(text), style = MaterialTheme.typography.bodyMedium)
                    Text("Ссылку прислало другое приложение, сайт или QR-код. Добавляйте только то, чему доверяете.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                confirmButton = { TextButton({ AppState.addInput(text); AppState.pendingInput = null }, enabled = !httpPlain) { Text("Добавить") } },
                dismissButton = { TextButton({ AppState.pendingInput = null }) { Text("Отмена") } })
        }
        AppState.pendingMove?.let { (id, nu) ->
            val sub = AppState.subs.firstOrNull { it.id == id }
            if (sub == null) AppState.pendingMove = null
            else AlertDialog(onDismissRequest = { AppState.pendingMove = null }, title = { Text("Подписка переехала") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сервис сообщает новый адрес подписки «${sub.name}»:", style = MaterialTheme.typography.bodyMedium)
                    Text("${hostOf(sub.url)} → ${hostOf(nu)}", style = MaterialTheme.typography.titleSmall)
                    Text("Переключайтесь, только если доверяете новому адресу: с него приложение будет получать список серверов.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                confirmButton = { TextButton({ AppState.acceptMove(id, nu) }) { Text("Переключить") } },
                dismissButton = { TextButton({ AppState.declineMove(id, nu) }) { Text("Не сейчас") } })
        }
        ShieldDialog()
        JournalDialog()
        GameDialog()
        AppState.updateInfo?.let { u ->
            AlertDialog(onDismissRequest = { AppState.updateInfo = null }, title = { Text("Доступна версия ${u.version}") },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(u.notes.ifBlank { "Описание не указано." }, style = MaterialTheme.typography.bodyMedium)
                    Text("Страница загрузки: ${hostOf(u.url)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (u.sha256.isNotBlank()) Text("SHA-256: ${u.sha256}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("APK откроется в браузере, дальше установка вручную поверх текущей версии.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                confirmButton = { TextButton({ runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url))) }; AppState.updateInfo = null }) { Text("Скачать") } },
                dismissButton = { TextButton({ AppState.updateInfo = null }) { Text("Позже") } })
        }
    }

    // ================================================================ Главная
    internal fun doAct(a: Act) {
        when (a) {
            Act.KILL -> { AppState.killSwitch = true; AppState.save() }
            Act.DOH -> { AppState.dnsDoh = true; AppState.save() }
            Act.RECONNECT -> { AppState.reconnectNet = true; AppState.save() }
            Act.VPN_SETTINGS -> runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
            Act.NONE -> {}
        }
    }

    internal fun installedGames(): List<String> {
        val pm = packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.distinct()
        return launchable.filter { runCatching { pm.getApplicationInfo(it, 0).category == ApplicationInfo.CATEGORY_GAME }.getOrDefault(false) }
    }

}
