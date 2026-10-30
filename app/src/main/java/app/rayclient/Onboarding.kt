@file:OptIn(ExperimentalMaterial3Api::class)

package app.rayclient

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

/** First-run assistant. Content scrolls; the footer stays above system bars and keyboard. */
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
                "Не удалось выполнить проверку: " + e.javaClass.simpleName
            }
            act.runOnUiThread {
                if (page == 2 && selected?.id == AppState.selectedId) diagnosis = result
                diagnosisBusy = false
            }
        }.start()
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = false,
        dismissOnClickOutside = false
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AuroraBackground(connectionColor())
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
                            "ЗНАКОМСТВО · " + (page + 1) + "/3",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { finish() }) { Text("Пропустить") }
                    }

                    // A wrap-content scroll viewport centers short screens and scrolls long text
                    // without ever scrolling the action buttons off the screen.
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        key(page) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (page) {
                                    0 -> {
                                        Icon(
                                            painterResource(R.drawable.ic_logo),
                                            contentDescription = null,
                                            modifier = Modifier.size(78.dp),
                                            tint = androidx.compose.ui.graphics.Color.Unspecified
                                        )
                                        Text(
                                            act.getString(R.string.app_name),
                                            Modifier.padding(top = 12.dp),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "VPN, который проверяет сам себя",
                                            Modifier.padding(top = 6.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                Text("Куда идёт трафик", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "Вы управляете маршрутами: через VPN, напрямую или блокировать.",
                                                    Modifier.padding(top = 4.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.height(12.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf("Через VPN", "Напрямую", "Закрыто").forEach { label ->
                                                        Surface(
                                                            Modifier.weight(1f),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(10.dp)
                                                        ) {
                                                            Text(
                                                                label,
                                                                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        OnboardingFeature("01", "Контроль", "Показывает состояние соединения и результаты проверок защиты.")
                                        OnboardingFeature("02", "Автопереключение", "При включённом автопереключении ищет рабочий сервер при сбое.")
                                        OnboardingFeature("03", "Приватность", "Без рекламы и аналитики. Настройки и ссылки хранятся на устройстве.")
                                    }

                                    1 -> {
                                        Text(
                                            "Добавьте сервер",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Скопируйте ссылку на подписку или сервер из своего VPN-сервиса. Также можно отсканировать QR-код.",
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
                                        ) { Text("Вставить из буфера") }
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = { act.scanCamera() },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) { Text("QR камерой") }
                                            FilledTonalButton(
                                                onClick = { act.scanImage() },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(14.dp)
                                            ) { Text("QR из фото") }
                                        }
                                        TextButton(onClick = { AppState.showAdd = true }) { Text("Ввести самому") }
                                        Spacer(Modifier.height(16.dp))
                                        val count = AppState.servers.size
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                when {
                                                    count > 0 -> {
                                                        Text("Серверов добавлено: " + count, color = Pal.Green, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            "Можно перейти к подключению.",
                                                            Modifier.padding(top = 6.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (AppState.selectedId == null) {
                                                            TextButton(onClick = {
                                                                AppState.selectedId = AppState.servers.first().id
                                                                AppState.save()
                                                            }) { Text("Выбрать первый сервер") }
                                                        }
                                                    }
                                                    AppState.subBusy > 0 -> {
                                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                                        Text(
                                                            "Получаем список серверов…",
                                                            Modifier.padding(top = 12.dp),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    AppState.subs.isNotEmpty() -> {
                                                        Text("Подписка добавлена, но серверов пока нет.")
                                                        Text(
                                                            "Проверьте сообщение об обновлении или попробуйте другую ссылку.",
                                                            Modifier.padding(top = 6.dp),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    else -> {
                                                        Text("Сервер пока не добавлен")
                                                        Text(
                                                            "Приложение не создаёт VPN-сервер. Нужна ваша подписка или ссылка.",
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
                                            "Готовность к подключению",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            "Проверьте основные условия. Разрешение Android на VPN будет запрошено при первом подключении.",
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
                                                ReadinessRow("Сервер", selected?.name ?: "Не выбран", status.hasServer)
                                                ReadinessRow(
                                                    "Разрешение VPN",
                                                    if (status.vpnGranted) "Выдано" else "Спросим при подключении",
                                                    status.vpnGranted
                                                )
                                                ReadinessRow(
                                                    "Уведомления",
                                                    if (status.notificationsGranted) "Разрешены" else "Не разрешены",
                                                    status.notificationsGranted
                                                )
                                                if (!status.notificationsGranted) {
                                                    FilledTonalButton(
                                                        onClick = { notif.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                                                    ) { Text("Разрешить уведомления") }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                SwitchRow(
                                                    "Kill switch (внутри VPN)",
                                                    "При сбое закрывает трафик, пока служба VPN работает.",
                                                    AppState.killSwitch
                                                ) {
                                                    AppState.killSwitch = it
                                                    AppState.save()
                                                }
                                                Text(
                                                    "Для блокировки интернета даже после остановки службы включите в Android «Постоянный VPN» и «Блокировать соединения без VPN».",
                                                    Modifier.padding(top = 10.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                TextButton(onClick = {
                                                    runCatching { act.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                                                        .onFailure { AppState.message = "Не удалось открыть настройки VPN" }
                                                }) { Text("Настройки VPN Android") }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Panel(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Проверка перед подключением", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "Проверяет сеть, DNS и доступность сервера. Результат не подтверждает работу VPN-туннеля.",
                                                    Modifier.padding(top = 6.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                FilledTonalButton(
                                                    onClick = { checkNetwork() },
                                                    enabled = !diagnosisBusy,
                                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                                ) { Text(if (diagnosisBusy) "Проверяем…" else "Проверить сеть и сервер") }
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
                                            TextButton(onClick = { page = 1 }) { Text("Вернуться к добавлению сервера") }
                                        }
                                        TextButton(onClick = {
                                            runCatching { act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                                .onFailure { AppState.message = "Не удалось открыть настройки батареи" }
                                        }) { Text("Настройки работы в фоне") }
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
                            ) { Text("Назад") }
                            val hasSelected = AppState.servers.any { it.id == AppState.selectedId }
                            Button(
                                onClick = {
                                    if (page < 2) page++
                                    else finish(connect = hasSelected)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(when {
                                page < 2 -> "Далее"
                                hasSelected -> "Подключиться"
                                else -> "Завершить позже"
                            }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeature(number: String, title: String, description: String) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(
                number,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
