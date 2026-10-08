@file:OptIn(ExperimentalTextApi::class, ExperimentalMaterial3Api::class)

package app.rayclient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Смысловые цвета. Для светлой темы взяты более тёмные оттенки: контраст не ниже 4,5:1 (WCAG AA) и на фоне, и на собственных метках. */
object Pal {
    @Volatile var dark = true     // выставляется в RayTheme
    val Violet get() = if (dark) Color(0xFFA78BFA) else Color(0xFF5B3FD6)
    val Green get() = if (dark) Color(0xFF3DDBB4) else Color(0xFF0A7A62)     // «подключено» и «хорошо»: мятный, бирюзовый край логотипа
    val Amber get() = if (dark) Color(0xFFFFC857) else Color(0xFF8A5700)
    val Red get() = if (dark) Color(0xFFFF6B6B) else Color(0xFFB3261E)
    val Blue get() = if (dark) Color(0xFF6CB6FF) else Color(0xFF1F5FC4)
    val Teal get() = if (dark) Color(0xFF5FD4C4) else Color(0xFF0B6B63)
    val Orange get() = if (dark) Color(0xFFFFA45C) else Color(0xFF9A4A00)
    val Gray get() = if (dark) Color(0xFF9AA0B5) else Color(0xFF5A6072)
}

private val Manrope = FontFamily(listOf(300, 400, 500, 600, 700, 800).map { w ->
    Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

private fun rayTypography(): Typography {
    val t = Typography(); val f = Manrope
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f), displaySmall = t.displaySmall.copy(fontFamily = f),
        // корни вкладок 32, страницы и окна 24, строки 16/15, пояснения 13: одна шкала на всё приложение
        headlineLarge = t.headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp), labelLarge = t.labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold), labelSmall = t.labelSmall.copy(fontFamily = f))
}

private val Dark = darkColorScheme(
    primary = Color(0xFFA78BFA), onPrimary = Color(0xFF1A1033), background = Color(0xFF0A0A12), onBackground = Color(0xFFF3F1F9),
    surface = Color(0xFF16151F), onSurface = Color(0xFFF3F1F9), surfaceVariant = Color(0xFF23212E), onSurfaceVariant = Color(0xFFA9A5B8),
    outline = Color(0xFF34313F), error = Color(0xFFFF6B6B), secondaryContainer = Color(0x38A78BFA), onSecondaryContainer = Color(0xFFEDE6FF),
    surfaceContainerLowest = Color(0xFF0A0A12), surfaceContainerLow = Color(0xFF12111A), surfaceContainer = Color(0xFF17161F),
    surfaceContainerHigh = Color(0xFF1C1B25), surfaceContainerHighest = Color(0xFF24222E))
private val Light = lightColorScheme(
    primary = Color(0xFF5B3FD6), onPrimary = Color.White, background = Color(0xFFF4F3F8), onBackground = Color(0xFF17151F),
    surface = Color.White, onSurface = Color(0xFF17151F), surfaceVariant = Color(0xFFEAE8F0), onSurfaceVariant = Color(0xFF5E5A6B),
    outline = Color(0xFFD9D6E2), secondaryContainer = Color(0x1F5B3FD6), onSecondaryContainer = Color(0xFF2A1C66),
    surfaceContainerLow = Color(0xFFF8F7FB), surfaceContainer = Color(0xFFF3F2F8), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFEAE8F0))

/** Скругления: поля 12, меню 16, карточки 24, диалоги 30. */
private val RayShapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp))

@Composable
fun RayTheme(dark: Boolean, content: @Composable () -> Unit) {
    Pal.dark = dark
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = rayTypography(), shapes = RayShapes, content = content)
}

/** Плашка-кнопка: видимая часть компактная, а зона нажатия не меньше 48 dp. */
@Composable
fun Pill(onClick: () -> Unit, background: Color, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(background).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }

@Composable
fun Tag(text: String, color: Color) = Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** Заголовок группы. Если задан help, рядом появляется значок «?» с объяснением простыми словами. */
@Composable
fun GroupTitle(text: String, help: String? = null) {
    var show by remember { mutableStateOf(false) }
    // высота строки постоянная (40 dp), поэтому отступ до карточки одинаковый с «i» и без
    Row(Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr(text).uppercase(), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.9.sp)
        if (help != null) IconButton({ show = true }, Modifier.size(40.dp)) { Icon(RayIcons.Info, "Что это?", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (show && help != null) AlertDialog(onDismissRequest = { show = false }, title = { Text(text) }, text = { Text(help, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton({ show = false }) { Text("Понятно") } })
}

/** Плавное появление: элемент поднимается на 24 dp и проявляется. delayMs даёт «лесенку» для списков. */
@Composable
fun Modifier.riseIn(delayMs: Int = 0): Modifier {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val p by animateFloatAsState(if (started) 1f else 0f, tween(460, delayMs, FastOutSlowInEasing), label = "rise")
    return graphicsLayer { alpha = p; translationY = (1f - p) * 24.dp.toPx() }
}

/** Кромка «стекла»: светлая сверху и почти прозрачная снизу, как блик на гранёном стекле. */
@Composable
fun glassEdge(): Brush = Brush.verticalGradient(
    if (Pal.dark) listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f)) else listOf(Color.White, Color(0x1417151F)))

/** Заливка стеклянной карточки: в тёмной теме светлее фона сверху и чуть темнее снизу, в светлой почти белая. */
fun glassFill(): Brush = Brush.verticalGradient(
    if (Pal.dark) listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.035f)) else listOf(Color.White.copy(alpha = 0.86f), Color.White.copy(alpha = 0.72f)))

/** Стеклянная карточка: полупрозрачная поверхность поверх живого фона и тонкая кромка с бликом. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth().clip(shape)
        .background(glassFill())
        .border(1.dp, glassEdge(), shape), content = content)
}

/** Переключатель с понятным выключенным состоянием (раньше серый на сером почти не был виден). */
@Composable
fun RaySwitch(value: Boolean, onChange: ((Boolean) -> Unit)?) = Switch(value, onChange, colors = SwitchDefaults.colors(
    checkedTrackColor = Tone.accentFill, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Tone.fillStrong, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))

/** Строка-переключатель. Длинное пояснение свёрнуто до трёх строк, «Подробнее» раскрывает его. Для экранного диктора строка — переключатель. */
@Composable
fun SwitchRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = { haptic.tap(HapticFeedbackType.TextHandleMove); onChange(it) }).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (desc.isNotEmpty()) {
                Text(desc, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
                if (overflow || expanded) Text(if (expanded) "Свернуть" else "Подробнее",
                    Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        RaySwitch(value, null)
    }
}

/** Переключатель вариантов: подсветка переезжает к выбранному с пружинкой, длинные подписи переносятся на вторую строку. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).clip(shape).background(Tone.fill).padding(4.dp)) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * selected.coerceIn(0, (options.size - 1).coerceAtLeast(0)), spring(dampingRatio = 0.8f, stiffness = 420f), label = "seg")
        if (selected in options.indices) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(12.dp))
            .background(if (Pal.dark) Color.White.copy(alpha = 0.12f) else Color.White)
            .border(1.dp, if (Pal.dark) glassEdge() else Brush.verticalGradient(listOf(Color(0x1417151F), Color(0x1417151F))), RoundedCornerShape(12.dp)))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, t ->
                val sel = i == selected
                val c by animateColorAsState(if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "segc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).selectable(sel, role = Role.Tab) { if (!sel) haptic.tap(HapticFeedbackType.TextHandleMove); onSelect(i) }.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center) {
                    // высота переключателя 48 dp: при крупном системном шрифте подпись в две строки не поместилась бы, поэтому масштаб ограничен
                    CappedFontScale { Text(t, color = c, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

/** Плитка-иконка раздела: мягкая подложка оттенка раздела и контурный значок того же цвета. */
@Composable
fun IconTile(icon: ImageVector, hue: Color, size: androidx.compose.ui.unit.Dp = 40.dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(hue.copy(alpha = if (Pal.dark) 0.16f else 0.11f)),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = hue)
    }

@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit) {
    // свои контурные значки; выбранная вкладка выделена цветом и подложкой
    val items: List<Triple<String, ImageVector, ImageVector>> = listOf(
        Triple("Главная", RayIcons.Home, RayIcons.Home), Triple("Настройки", RayIcons.Sliders, RayIcons.Sliders))
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = if (items.size <= 2) 64.dp else 16.dp, vertical = 10.dp).height(64.dp).clip(shape)
        .background(if (Pal.dark) Color(0xFF1A1924) else Color.White).border(1.dp, glassEdge(), shape).padding(5.dp)) {
        val w = maxWidth / items.size
        // подсветка выбранной вкладки переезжает к новой с пружинкой
        val x by animateDpAsState(w * tab, spring(dampingRatio = 0.75f, stiffness = 380f), label = "navX")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(27.dp)).background(Tone.selected))
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, (t, on, off) ->
                val sel = tab == i
                val c by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "nav")
                val bump by animateFloatAsState(if (sel) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "navBump")
                // selectable вместо clickable: скринридер говорит «вкладка, выбрана»; значок без своей подписи, чтобы название не читалось дважды
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(27.dp)).selectable(sel, role = Role.Tab) { if (!sel) haptic.tap(HapticFeedbackType.TextHandleMove); onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (sel) on else off, null, Modifier.size(22.dp).graphicsLayer { scaleX = bump; scaleY = bump }, tint = c)
                    CappedFontScale { Text(t, Modifier.padding(top = 2.dp), color = c, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1) }
                }
            }
        }
    }
}
