@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.rayclient

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/** Знакомство при первом запуске: что это, как добавить подписку, какие разрешения нужны. Закрыть случайным касанием нельзя. */
@Composable
internal fun MainActivity.OnboardingDialog() {
    val act = this
    var page by remember { mutableIntStateOf(0) }
    var notifGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(act, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    fun finish() { AppState.onboarded = true; AppState.save() }
    Dialog({}, DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AuroraBackground(connectionColor())
            Column(Modifier.riseIn().fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ finish() }) { Text("Пропустить") } }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (page) {
                        0 -> {
                            Image(painterResource(R.drawable.ic_logo), null, Modifier.padding(top = 24.dp).size(96.dp))
                            Text(BRAND, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            Text("VPN, который проверяет сам себя", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(28.dp))
                            listOf("Показывает, куда идёт трафик: через VPN, напрямую или заблокирован.",
                                "Само переключается на рабочий сервер, если текущий замолчал.",
                                "Без аналитики и рекламы. Сеть используется только для вашей подписки, серверов и функций, которые вы включили сами.").forEach {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(14.dp))
                                    Text(it, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        1 -> {
                            Text("Добавьте подписку", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall)
                            Text("Ссылку на подписку даёт ваш VPN-сервис: в боте или личном кабинете. Скопируйте её и нажмите кнопку ниже, либо отсканируйте QR-код.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                            Button({ val t = act.clipboardText(); if (t.isBlank()) AppState.message = "В буфере обмена пусто" else AppState.addInput(t) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Вставить из буфера") }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton({ act.scanCamera() }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("QR камерой") }
                                FilledTonalButton({ act.scanImage() }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("QR из фото") }
                            }
                            TextButton({ AppState.showAdd = true }) { Text("Ввести самому") }
                            if (AppState.servers.isNotEmpty()) Panel(Modifier.padding(top = 12.dp)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("✓", color = Pal.Green, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp))
                                    Text("Добавлено серверов: ${AppState.servers.size}", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        else -> {
                            Text("Защита и разрешения", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall)
                            Text("При первом подключении система один раз спросит разрешение на VPN. Нажмите «ОК»: без него VPN работать не может.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Panel {
                                SwitchRow("Kill switch (рекомендуется)", "Если VPN оборвётся, интернет заблокируется, а не уйдёт напрямую.", AppState.killSwitch) { AppState.killSwitch = it; AppState.save() }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Уведомления", style = MaterialTheme.typography.bodyLarge)
                                    Text("Нужны, чтобы VPN работал в фоне и показывал состояние.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (notifGranted) Text("✓ Разрешены", color = Pal.Green, style = MaterialTheme.typography.bodyMedium)
                                    else FilledTonalButton({ notif.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Разрешить") }
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Работа в фоне", style = MaterialTheme.typography.bodyLarge)
                                    Text("На некоторых прошивках (например, ColorOS) система закрывает фоновые программы. Если VPN сам отключается, выберите «Не ограничивать» для приложения в настройках батареи.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FilledTonalButton({ runCatching { act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) { Text("Открыть настройки батареи") }
                                }
                            }
                            Text("Щит приватности на главной подскажет, что ещё можно усилить.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    for (i in 0..2) Box(Modifier.padding(horizontal = 4.dp).size(if (i == page) 10.dp else 8.dp).clip(CircleShape).background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page > 0) OutlinedButton({ page-- }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Назад") }
                    Button({ if (page < 2) page++ else finish() }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (page < 2) "Далее" else "Готово") }
                }
            }
        }
    }
}
