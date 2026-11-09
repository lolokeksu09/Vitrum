package app.rayclient

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Доступность: ограничение системного масштаба шрифта там, где текст стоит в блоке фиксированного размера, и учёт «Убрать анимации». */
object Access {
    /** Масштаб шрифта с потолком: больше потолка текст в тесной вёрстке (таймер, шкала, вкладки, переключатель) обрезается или не помещается. */
    fun cappedScale(fontScale: Float, max: Float): Float = minOf(fontScale, max)

    /** Системная настройка «Убрать анимации» (разработчикам или спец. возможности): шкала длительности анимаций равна нулю. */
    fun reducedMotion(ctx: Context): Boolean =
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}

/** Содержимое с потолком масштаба шрифта (по умолчанию 1.3). Остальной интерфейс масштабируется как задал пользователь. */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, Access.cappedScale(d.fontScale, max)), content = content)
}
