package app.rayclient

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR-код со ссылкой сервера (чтобы перенести его на другой телефон). Ссылка содержит ключи сервера: показывать только после подтверждения. */
object Qr {
    /** null — ссылка не помещается в QR (слишком длинная). Чёрный на белом: так читают камеры. */
    fun bitmap(text: String, size: Int = 640): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

@Composable
internal fun QrDialog(title: String, link: String, onClose: () -> Unit) {
    val bmp = remember(link) { Qr.bitmap(link) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title, maxLines = 2) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmp != null) Image(bmp.asImageBitmap(), "QR-код сервера", Modifier.fillMaxWidth().padding(4.dp))
                else Text("Ссылка слишком длинная для QR-кода. Скопируйте её.", style = MaterialTheme.typography.bodyMedium)
                Text("Отсканируйте камерой в приложении на другом телефоне («Добавить» → «QR камерой»). Закройте окно, когда закончите.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClose) { Text("Закрыть") } })
}
