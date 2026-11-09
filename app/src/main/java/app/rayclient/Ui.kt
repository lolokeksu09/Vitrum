@file:OptIn(ExperimentalTextApi::class, ExperimentalMaterial3Api::class)

package app.rayclient

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
    primary = Color(0xFFA78BFA), onPrimary = Color(0xFF1A1033), background = Color(0xFF000000), onBackground = Color(0xFFF3F1F9),
    surface = Color(0xFF111218), onSurface = Color(0xFFF3F1F9), surfaceVariant = Color(0xFF1B1C24), onSurfaceVariant = Color(0xFFA9A5B8),
    outline = Color(0xFF373840), error = Color(0xFFFF6B6B), secondaryContainer = Color(0xFF292337), onSecondaryContainer = Color(0xFFEDE6FF),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF111218), surfaceContainer = Color(0xFF171820),
    surfaceContainerHigh = Color(0xFF1B1C24), surfaceContainerHighest = Color(0xFF262730))
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
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
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
    // Text-first section header: hierarchy from typography, not all-caps decoration.
    Row(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr(text), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (help != null) IconButton({ show = true }, Modifier.size(48.dp)) { Icon(RayIcons.Info, I18n.tr("Что это?"), Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (show && help != null) RayInfoDialog(
        title = I18n.tr(text),
        message = I18n.tr(help),
        onDismiss = { show = false }
    )
}

/** Quiet, opaque Android surface. No glass/gradient under scroll content. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape), content = content)
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
            Text(I18n.tr(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (desc.isNotEmpty()) {
                Text(I18n.tr(desc), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
                if (overflow || expanded) Text(I18n.tr(if (expanded) "Свернуть" else "Подробнее"),
                    Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        RaySwitch(value, null)
    }
}

/** Выбор одного варианта. Длинные подписи могут переноситься. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().selectableGroup().height(IntrinsicSize.Min)
        .clip(shape).background(scheme.surfaceContainerLow).padding(4.dp)) {
        options.forEachIndexed { i, title ->
            val selectedOption = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(shape)
                    .background(if (selectedOption) scheme.secondaryContainer else Color.Transparent)
                    .selectable(selectedOption, role = Role.Tab) {
                        if (!selectedOption) haptic.tap(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }.padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = if (selectedOption) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Native Android navigation; deterministic and readable at increased font scale. */
@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth() // Material NavigationBar owns navigation bar insets
    ) {
        listOf(
            Triple("Главная", RayIcons.Home, 0),
            Triple("Настройки", RayIcons.Sliders, 1)
        ).forEach { (label, image, index) ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { if (tab != index) onTab(index) },
                icon = { Icon(image, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(label, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
