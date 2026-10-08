package app.rayclient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Набор компонентов Vitrum вместо стоковых Material 3. Button, FilledTonalButton, FilterChip и AssistChip объявлены в пакете
 * приложения, поэтому вызовы на экранах подхватывают их сами (как Text и Icon в Texts.kt): вид меняется, поведение то же.
 * AlertDialog и TextButton остаются из Material 3, их цвета задаёт тема.
 */

/** Нижний отступ под плавающую навигацию: содержимое вкладок прокручивается под панель, а не обрывается над ней. */
val LocalTabBottom = compositionLocalOf { 0.dp }

/** Отклик на нажатие с учётом настройки «Вибрация» (Настройки → Оформление). */
fun androidx.compose.ui.hapticfeedback.HapticFeedback.tap(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType) { if (AppState.haptics) performHapticFeedback(type) }

/** Поверхности: слои вместо рамок. fill — вложенные элементы на карточке, selected — выбранное, accentFill — главное действие. */
object Tone {
    val fill get() = if (Pal.dark) Color.White.copy(alpha = 0.06f) else Color(0xFF17151F).copy(alpha = 0.05f)
    val fillStrong get() = if (Pal.dark) Color.White.copy(alpha = 0.10f) else Color(0xFF17151F).copy(alpha = 0.075f)
    val selected get() = if (Pal.dark) Color(0xFFA78BFA).copy(alpha = 0.22f) else Color(0xFF5B3FD6).copy(alpha = 0.12f)
    val divider get() = if (Pal.dark) Color.White.copy(alpha = 0.07f) else Color(0xFF17151F).copy(alpha = 0.07f)
    val accentFill get() = if (Pal.dark) Color(0xFF7C5CF5) else Color(0xFF5B3FD6)
}

/** Кнопка: главная (заливка акцентом) или обычная (полупрозрачная заливка без рамки). Нажатие слегка утапливает её. */
@Composable
fun RayButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "btn")
    val shape = RoundedCornerShape(16.dp)
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onSurface
    Row(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
        .clip(shape).background(if (primary) Tone.accentFill else Tone.fillStrong)
        .then(if (primary) Modifier.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent)), shape) else Modifier)
        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides MaterialTheme.typography.labelLarge) { content() }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape? = null, content: @Composable RowScope.() -> Unit) =
    RayButton(onClick, modifier, enabled, primary = true, content = content)

@Suppress("UNUSED_PARAMETER")
@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape? = null, content: @Composable RowScope.() -> Unit) =
    RayButton(onClick, modifier, enabled, primary = false, content = content)

/** Чип-фильтр: капсула 36 dp без рамки, зона нажатия 48 dp. */
@Composable
fun RayChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
            leadingIcon: (@Composable () -> Unit)? = null) {
    val bg by animateColorAsState(if (selected) Tone.selected else Tone.fill, label = "chipBg")
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.minimumInteractiveComponentSize().height(36.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }.clip(shape).background(bg)
        .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick).padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            if (leadingIcon != null) { Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }; Spacer(Modifier.width(6.dp)) }
            label()
        }
    }
}

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
               leadingIcon: (@Composable () -> Unit)? = null) = RayChip(selected, onClick, label, modifier, enabled, leadingIcon)

@Composable
fun AssistChip(onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
               leadingIcon: (@Composable () -> Unit)? = null) = RayChip(false, onClick, label, modifier, enabled, leadingIcon)

/** Полоса расхода (трафик подписки): дорожка и заполнение без точки на конце. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 6.dp) =
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Tone.fillStrong)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2)).background(color))
    }

/** Шапка страницы и полноэкранных окон: стрелка «назад», заголовок, справа действия. Одна и та же везде. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onBack) { Icon(RayIcons.Back, "Назад") }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        actions()
    }

/**
 * Окно (Dialog) во весь экран: фон рисуется под статус-баром и жестовой панелью, значки системных панелей светлые на тёмной теме
 * и тёмные на светлой. Вызывать внутри содержимого Dialog с DialogProperties(decorFitsSystemWindows = false).
 */
@Composable
internal fun EdgeToEdgeDialog() {
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = Pal.dark
    androidx.compose.runtime.SideEffect {
        val w = ((view as? androidx.compose.ui.window.DialogWindowProvider) ?: (view.parent as? androidx.compose.ui.window.DialogWindowProvider))?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
        w.statusBarColor = android.graphics.Color.TRANSPARENT
        w.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
    }
}
