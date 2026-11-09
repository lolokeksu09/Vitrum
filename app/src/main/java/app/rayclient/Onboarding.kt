@file:OptIn(ExperimentalMaterial3Api::class)

package app.rayclient

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** First-run assistant. Use the Activity viewport, not a separate full-screen Dialog.
 *  Some Android 16 window configurations measured the dialog beyond the visible screen.
 */
@Composable
internal fun MainActivity.OnboardingDialog() {
    val act = this
    var page by rememberSaveable { mutableIntStateOf(0) }
    var notifGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(act, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    var diagnosis by remember { mutableStateOf<String?>(null) }
    var diagnosisBusy by remember { mutableStateOf(false) }

    fun finish(connect: Boolean = false) {
        AppState.onboarded = true
        AppState.save()
        if (connect) AppState.pendingConnect = true
    }

    fun checkNetwork() {
        if (diagnosisBusy) return
        val selected = AppState.servers.firstOrNull { it.id == AppState.selectedId }
        diagnosisBusy = true
        diagnosis = null
        Thread {
            val result = try {
                ConnWizard.run(act, selected)
            } catch (e: Exception) {
                I18n.tr("Не удалось выполнить проверку: {0}".replace("{0}", e.javaClass.simpleName))
            }
            act.runOnUiThread {
                if (page == 2 && selected?.id == AppState.selectedId) diagnosis = result
                diagnosisBusy = false
            }
        }.start()
    }

    BackHandler(enabled = page > 0) { page-- }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            I18n.tr("ЗНАКОМСТВО · {0}/3".replace("{0}", (page + 1).toString())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 88.dp)) { Text(I18n.tr("Пропустить"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    }

                    // The scroll viewport is bounded by the real Activity, with a
                    // distinct fixed footer. Short pages start above the visual center.
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val topSpace = (maxHeight * 0.08f).coerceAtMost(64.dp)
                        key(page) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = topSpace, bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (page) {
                                    0 -> {
                                        Surface(
                                            modifier = Modifier.size(124.dp),
                                            shape = RoundedCornerShape(32.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            tonalElevation = 1.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painterResource(R.drawable.ic_logo),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(78.dp),
                                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                                )
                                            }
                                        }
                                        Text(
                                            act.getString(R.string.app_name),
                                            Modifier.padding(top = 14.dp),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            I18n.tr("Добавьте сервер или подписку, чтобы подключить VPN."),
                                            Modifier.padding(top = 8.dp),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Panel(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                OnboardingFeature(
                                                    RayIcons.Route,
                                                    I18n.tr("Раздельное туннелирование"),
                                                    "Часть трафика идёт через VPN, часть напрямую: по приложениям, по доменам и адресам или режимом «Все, кроме РФ»."
                                                )
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                                OnboardingFeature(
                                                    RayIcons.ShieldCheck,
                                                    I18n.tr("Щит приватности"),
                                                    I18n.tr("Оценка защиты по вашим настройкам и по проверке сети, с советами. Показывает, что можно усилить.")
                                                )
                                            }
                                        }
                                    }

                                    1 -> {
                                        Text(
                                            I18n.tr("Добавьте сервер"),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            I18n.tr("Скопируйте ссылку на подписку или сервер из своего VPN-сервиса. Также можно отсканировать QR-код."),
                                            Modifier.padding(top = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        Button(
                                            onClick = {
                                                val text = act.clipboardText()
                                                if (text.isBlank()) AppState.message = "В буфере обмена пусто"
                                                else AppState.addInput(text)
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) { Text(I18n.tr("Вставить из буфера")) }
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = { act.scanCamera() },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) { Text(I18n.tr("QR камерой")) }
                                            FilledTonalButton(
                                                onClick = { act.scanImage() },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) { Text(I18n.tr("QR из фото")) }
                                        }
                                        TextButton(onClick = { AppState.showAdd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Ввести самому")) }
                                        Spacer(Modifier.height(16.dp))
                                        val count = AppState.servers.size
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                when {
                                                    count > 0 -> {
                                                        Text(I18n.tr("Серверов добавлено: {0}".replace("{0}", count.toString())), color = Pal.Green, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            I18n.tr("Можно перейти к подключению."),
                                                            Modifier.padding(top = 6.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (AppState.selectedId == null) {
                                                            TextButton(onClick = {
                                                                AppState.selectedId = AppState.servers.first().id
                                                                AppState.save()
                                                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Выбрать первый сервер")) }
                                                        }
                                                    }
                                                    AppState.subBusy > 0 -> {
                                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                                        Text(
                                                            I18n.tr("Получаем список серверов…"),
                                                            Modifier.padding(top = 12.dp),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    AppState.subs.isNotEmpty() -> {
                                                        Text(I18n.tr("Подписка добавлена, но серверов пока нет."))
                                                        Text(
                                                            I18n.tr("Проверьте сообщение об обновлении или попробуйте другую ссылку."),
                                                            Modifier.padding(top = 6.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    else -> {
                                                        Text(I18n.tr("Сервер пока не добавлен"))
                                                        Text(
                                                            I18n.tr("Приложение не создаёт VPN-сервер. Нужна ваша подписка или ссылка."),
                                                            Modifier.padding(top = 6.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        Text(
                                            I18n.tr("Готовность к подключению"),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            I18n.tr("Проверьте основные условия. Разрешение Android на VPN будет запрошено при первом подключении."),
                                            Modifier.padding(top = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        val selected = AppState.servers.firstOrNull { it.id == AppState.selectedId }
                                        val status = OnboardingReadiness.evaluate(
                                            AppState.servers.map { it.id },
                                            AppState.selectedId,
                                            VpnService.prepare(act) == null,
                                            notifGranted
                                        )
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                ReadinessRow(I18n.tr("Сервер"), selected?.name ?: I18n.tr("Не выбран"), status.hasServer)
                                                ReadinessRow(
                                                    I18n.tr("Разрешение VPN"),
                                                    if (status.vpnGranted) I18n.tr("Выдано") else I18n.tr("Спросим при подключении"),
                                                    status.vpnGranted
                                                )
                                                ReadinessRow(
                                                    I18n.tr("Уведомления"),
                                                    if (status.notificationsGranted) I18n.tr("Разрешены") else I18n.tr("Не разрешены"),
                                                    status.notificationsGranted
                                                )
                                                if (!status.notificationsGranted) {
                                                    FilledTonalButton(
                                                        onClick = { notif.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    ) { Text(I18n.tr("Разрешить уведомления")) }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                SwitchRow(
                                                    I18n.tr("Kill switch (внутри VPN)"),
                                                    I18n.tr(I18n.tr("При сбое закрывает трафик, пока служба VPN работает.")),
                                                    AppState.killSwitch
                                                ) {
                                                    AppState.killSwitch = it
                                                    AppState.save()
                                                }
                                                Text(
                                                    I18n.tr("Для блокировки интернета даже после остановки службы включите в Android «Постоянный VPN» и «Блокировать соединения без VPN»."),
                                                    Modifier.padding(top = 10.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                TextButton(onClick = {
                                                    runCatching { act.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                                                        .onFailure { AppState.message = "Не удалось открыть настройки VPN" }
                                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Настройки VPN Android")) }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text(I18n.tr("Проверка перед подключением"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    I18n.tr("Проверяет сеть, DNS и доступность сервера. Результат не подтверждает работу VPN-туннеля."),
                                                    Modifier.padding(top = 6.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                FilledTonalButton(
                                                    onClick = { checkNetwork() },
                                                    enabled = !diagnosisBusy,
                                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
                                                ) { Text(if (diagnosisBusy) I18n.tr("Проверяем…") else I18n.tr("Проверить сеть и сервер")) }
                                                if (diagnosisBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                                diagnosis?.let { result ->
                                                    Text(
                                                        result,
                                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        if (!status.hasServer) {
                                            TextButton(onClick = { page = 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Вернуться к добавлению сервера")) }
                                        }
                                        TextButton(onClick = {
                                            runCatching { act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                                .onFailure { AppState.message = "Не удалось открыть настройки батареи" }
                                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(I18n.tr("Настройки работы в фоне")) }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(3) { i ->
                                Box(
                                    Modifier.width(if (i == page) 34.dp else 18.dp)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (page > 0) OutlinedButton(
                                onClick = { page-- },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(I18n.tr("Назад")) }
                            val canConnect = OnboardingReadiness.evaluate(
                                AppState.servers.map { it.id },
                                AppState.selectedId,
                                VpnService.prepare(act) == null,
                                notifGranted
                            ).canRequestConnection
                            Button(
                                onClick = {
                                    if (page < 2) page++
                                    else finish(connect = canConnect)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(when {
                                page < 2 -> I18n.tr("Далее")
                                canConnect -> I18n.tr("Подключиться")
                                else -> I18n.tr("Завершить позже")
                            }) }
                        }
                    }
                }
            }
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, description: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(I18n.tr(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                I18n.tr(description),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadinessRow(title: String, detail: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ready) "✓" else "•",
            color = if (ready) Pal.Green else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(I18n.tr(title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
