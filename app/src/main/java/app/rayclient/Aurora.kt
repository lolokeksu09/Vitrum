package app.rayclient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Цвет состояния подключения: им окрашены фон, кнопка и подсветки. */
@Composable
fun connectionColor(): Color {
    val err = RayVpnService.state.startsWith("Ошибка")
    val target = when {
        RayVpnService.blocked || err -> Pal.Red
        RayVpnService.connected -> Pal.Green
        RayVpnService.busy || AppState.quickBusy -> Pal.Amber
        else -> Pal.Violet
    }
    return animateColorAsState(target, tween(900), label = "state").value
}

/**
 * Живой фон «северное сияние»: несколько больших мягких пятен света медленно плывут по кругу.
 * Обычный Canvas с радиальными градиентами: работает на любом Android 8+, без шейдеров, которые нельзя проверить без устройства.
 */
@Composable
fun AuroraBackground(accent: Color, modifier: Modifier = Modifier, animate: Boolean = true) {
    // фон плывёт медленно (круг за 24 с), поэтому достаточно ~12 кадров в секунду: перерисовка трёх больших градиентов на весь экран
    // шестьдесят раз в секунду дорога (на эмуляторе без видеокарты главный поток переставал отвечать, ANR)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableFloatStateOf(0.15f) }
    val reduced = remember { Access.reducedMotion(ctx) }   // «Убрать анимации» в системе: фон стоит
    if (animate && !reduced) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) { delay(80); phase = (withFrameMillis { it } - start) % 24_000L / 24_000f }
    }
    val bg = MaterialTheme.colorScheme.background
    val dark = Pal.dark
    val second = Pal.Blue
    val third = Pal.Teal
    Canvas(modifier.fillMaxSize()) {
        drawRect(bg)
        val w = size.width; val h = size.height; val a = phase * 2f * PI.toFloat()
        fun glow(cx: Float, cy: Float, r: Float, c: Color, alpha: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.35f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        val k = if (dark) 1f else 0.7f
        glow(w * (0.30f + 0.18f * cos(a)), h * (0.20f + 0.07f * sin(a)), w * 0.95f, accent, 0.34f * k)
        glow(w * (0.88f + 0.10f * sin(2 * a)), h * (0.42f + 0.09f * cos(a)), w * 0.70f, second, 0.17f * k)
        glow(w * (0.20f + 0.22f * sin(a)), h * (0.88f + 0.05f * cos(2 * a)), w * 0.80f, third, 0.13f * k)
        // лёгкое затемнение к низу, чтобы стеклянная панель навигации читалась
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.55f)), startY = h * 0.55f, endY = h))
    }
}
