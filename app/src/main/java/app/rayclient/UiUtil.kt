package app.rayclient

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Флаг в начале названия сервера → (флаг, название без флага). */
internal fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: url

internal fun splitFlag(name: String): Pair<String, String> {
    val cps = name.codePoints().toArray()
    if (cps.size >= 2 && cps[0] in 0x1F1E6..0x1F1FF && cps[1] in 0x1F1E6..0x1F1FF) {
        val flag = String(cps, 0, 2); return flag to name.drop(flag.length).trim()
    }
    return "🌐" to name
}

internal fun chipsOf(link: String): List<Pair<String, Color>> = if (AutoSub.isAuto(link)) listOf("Авто" to Pal.Teal) else runCatching {
    val p = Links.parse(link); val st = p.outbound.optJSONObject("streamSettings")
    buildList {
        add((if (p.proto == "hysteria2") "HY2" else p.proto.uppercase()) to (if (p.proto == "hysteria2") Pal.Orange else Pal.Gray))
        when (st?.optString("security")) { "reality" -> add("Reality" to Pal.Violet); "tls" -> add("TLS" to Pal.Blue) }
        st?.optString("network")?.takeIf { it !in listOf("tcp", "hysteria", "") }?.let { add(it.uppercase() to Pal.Teal) }
    }
}.getOrDefault(emptyList())

internal fun speed(b: Long) = when { b < 1024 -> "$b Б/с"; b < 1024 * 1024 -> "%.0f КБ/с".format(b / 1024.0); else -> "%.1f МБ/с".format(b / 1048576.0) }
internal fun size(b: Long) = when { b < 1024 * 1024 -> "%.0f КБ".format(b / 1024.0); b < 1024L * 1024 * 1024 -> "%.1f МБ".format(b / 1048576.0); else -> "%.2f ГБ".format(b / 1073741824.0) }
internal fun pingColor(ms: Int) = if (ms < 150) Pal.Green else if (ms < 400) Pal.Amber else Pal.Red
