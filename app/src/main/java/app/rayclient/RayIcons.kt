package app.rayclient

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Свои значки Vitrum: контур на сетке 24×24, штрих 1.8, скруглённые концы. Нарисованы для приложения (не скопированы из наборов).
 * Цвет задаёт Icon(tint), поэтому штрих чёрный. Точки (h.01) при круглом конце штриха рисуются кружками.
 */
object RayIcons {
    private const val CIRCLE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"

    private fun icon(name: String, d: String, fill: Boolean = false, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    // навигация
    val Home = icon("home", "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z")
    val Globe = icon("globe", "$CIRCLE M3 12h18 M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9 M12 3c-2.5 2.6-3.8 5.6-3.8 9s1.3 6.4 3.8 9")
    val Route = icon("route", "M8 19h7a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h7 M6 21a2 2 0 1 0 0-4a2 2 0 1 0 0 4z M18 7a2 2 0 1 0 0-4a2 2 0 1 0 0 4z")
    val Sliders = icon("sliders", "M4 7h9 M17 7h3 M4 17h3 M11 17h9 M15 9a2 2 0 1 0 0-4a2 2 0 1 0 0 4z M9 19a2 2 0 1 0 0-4a2 2 0 1 0 0 4z")

    // разделы настроек
    val Shield = icon("shield", "M12 3l7.5 3v5.5c0 4.6-3.1 8.2-7.5 9.5c-4.4-1.3-7.5-4.9-7.5-9.5V6z")
    val ShieldCheck = icon("shieldCheck", "M12 3l7.5 3v5.5c0 4.6-3.1 8.2-7.5 9.5c-4.4-1.3-7.5-4.9-7.5-9.5V6z M9 12l2 2l4-4")
    val Lock = icon("lock", "M6.5 11h11a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 18.5v-6A1.5 1.5 0 0 1 6.5 11z M8 11V8a4 4 0 0 1 8 0v3")
    val Plug = icon("plug", "M9 3v4 M15 3v4 M7 7h10v4a5 5 0 0 1-10 0z M12 16v5")
    val Bolt = icon("bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z")
    val Palette = icon("palette", "M12 3a9 9 0 1 0 0 18c1 0 1.6-.7 1.6-1.5c0-.5-.3-.9-.6-1.3c-.3-.4-.5-.8-.5-1.3c0-.9.7-1.4 1.6-1.4H16a5 5 0 0 0 5-5c0-4.1-4-7.5-9-7.5z M7.5 11.5h.01 M10 7.5h.01 M14.5 7.5h.01")
    val Terminal = icon("terminal", "M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M7 9.5l2.5 2.5L7 14.5 M12 15h5")
    val Help = icon("help", "$CIRCLE M9.6 9.3a2.5 2.5 0 0 1 4.8.9c0 1.6-2.4 2.1-2.4 3.3 M12 17h.01")
    val Info = icon("info", "$CIRCLE M12 11v5 M12 8h.01")

    // действия
    val Journal = icon("journal", "M9 6h11 M9 12h11 M9 18h11 M4.5 6h.01 M4.5 12h.01 M4.5 18h.01")
    val Search = icon("search", "M11 18a7 7 0 1 0 0-14a7 7 0 1 0 0 14z M20 20l-4-4")
    val Plus = icon("plus", "M12 5v14 M5 12h14")
    val Close = icon("close", "M6 6l12 12 M18 6L6 18")
    val Back = icon("back", "M19 12H5 M11 6l-6 6l6 6")
    val Chevron = icon("chevron", "M9.5 5.5L16 12l-6.5 6.5")
    val More = icon("more", "M12 5h.01 M12 12h.01 M12 19h.01", stroke = 2.6f)
    val Refresh = icon("refresh", "M19.5 10A8 8 0 0 0 5 7.5 M4.5 4v3.8h3.8 M4.5 14A8 8 0 0 0 19 16.5 M19.5 20v-3.8h-3.8")
    val Star = icon("star", "M12 3.8l2.5 5.2l5.7.8l-4.1 4l1 5.7L12 16.8l-5.1 2.7l1-5.7l-4.1-4l5.7-.8z")
    val StarFilled = icon("starFilled", "M12 3.8l2.5 5.2l5.7.8l-4.1 4l1 5.7L12 16.8l-5.1 2.7l1-5.7l-4.1-4l5.7-.8z", fill = true)
    val Gauge = icon("gauge", "M4.6 17.5a8.5 8.5 0 1 1 14.8 0 M12 14l3.5-4 M12 14h.01")
    val Trash = icon("trash", "M4.5 7h15 M10 7V4.8h4V7 M6.5 7l1 12.2h9l1-12.2 M10.2 11v5 M13.8 11v5")
    val Alert = icon("alert", "M12 4l9 16H3z M12 10v4 M12 17h.01")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7")
}
