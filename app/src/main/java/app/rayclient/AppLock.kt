package app.rayclient

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign

/**
 * Блокировка входа в приложение. Подтверждение делает система (KeyguardManager.createConfirmDeviceCredentialIntent): отпечаток, лицо, PIN
 * или рисунок телефона, поэтому свои PIN-коды и новые зависимости не нужны. Защищает только окно приложения: служба VPN работает и при блокировке.
 */
object AppLock {
    const val GRACE_SEC = 30

    /** Вход подтверждён в этом процессе (переживает поворот экрана, но не перезапуск процесса). */
    @Volatile var unlocked = false

    /** Когда окно ушло из переднего плана (elapsedRealtime, 0 — не уходило). Общий для всех экземпляров Activity. */
    @Volatile var leftAt = 0L

    /** Нужна ли блокировка при возврате: включена и приложение было закрыто или свёрнуто дольше льготного времени (внешние окна вроде запроса VPN не считаются выходом). */
    fun expired(enabled: Boolean, leftAtMs: Long, nowMs: Long): Boolean = enabled && nowMs - leftAtMs > GRACE_SEC * 1000L

    /** У телефона задана системная блокировка экрана. Без неё подтверждать личность нечем. */
    fun available(ctx: Context): Boolean = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
}

@Composable
internal fun MainActivity.LockScreen(onUnlocked: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == android.app.Activity.RESULT_OK) onUnlocked() }
    val ask: () -> Unit = {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val i = km.createConfirmDeviceCredentialIntent(BRAND, I18n.tr("Подтвердите, что это вы"))
        if (i != null) launcher.launch(i) else onUnlocked()   // блокировку экрана сняли: запирать нечем, пускаем
    }
    LaunchedEffect(Unit) { ask() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(RayIcons.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(BRAND, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Приложение заблокировано", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(ask) { Text("Разблокировать") }
    }
}
