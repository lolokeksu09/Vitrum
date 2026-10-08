package app.rayclient

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

/** Bitmap на JVM недоступен (Android), поэтому проверяем ту же кодировку матрицей: ссылка пишется и читается обратно без потерь. */
class QrTest {
    @Test fun linkSurvivesEncodeAndDecode() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#Германия"
        val size = 640
        val m = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val res = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, px))), mapOf(com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8"))
        assertEquals(link, res.text)
    }

    @Test fun tooLongTextCannotBeEncoded() {
        assertThrows(Exception::class.java) { QRCodeWriter().encode("x".repeat(5000), BarcodeFormat.QR_CODE, 300, 300) }
    }
}
