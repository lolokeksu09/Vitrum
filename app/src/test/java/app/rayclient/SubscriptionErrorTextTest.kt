package app.rayclient

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class SubscriptionErrorTextTest {
    @Test fun untrustedMimeParametersAreNotShownToUser() {
        val credential = "vless://user:secret@example.invalid:443"
        val html = SubscriptionErrorText.unsupportedResponse("text/html; boundary=$credential")
        assertEquals(
            "Вместо списка серверов получена HTML-страница. Проверьте адрес подписки.",
            html
        )
        assertFalse(html.contains(credential))
        assertEquals(
            "В подписке нет поддерживаемых серверов. Проверьте формат подписки.",
            SubscriptionErrorText.unsupportedResponse("application/x-unknown; token=$credential")
        )
    }

    @Test fun unsupportedJsonIsDistinguishedWithoutEchoingHeaders() {
        assertEquals(
            "В подписке нет поддерживаемых серверов (JSON-ответ). Проверьте формат подписки.",
            SubscriptionErrorText.unsupportedResponse("application/json; token=private")
        )
        assertFalse(SubscriptionErrorText.unsupportedResponse("application/json; token=private").contains("private"))
    }

    @Test fun transportErrorsNeverEchoExceptionMessagesOrSubscriptionUrls() {
        val secretUrl = "https://example.invalid/sub?token=private-credential"
        val cases = listOf(
            IOException("GET $secretUrl failed"),
            UnknownHostException(secretUrl),
            SocketTimeoutException(secretUrl),
            SSLHandshakeException(secretUrl)
        )
        for (error in cases) {
            val message = SubscriptionErrorText.downloadFailure(error)
            assertFalse("A credential escaped into a UI error: $message", message.contains(secretUrl))
            assertFalse(message.contains("private-credential"))
            assertFalse(message.isBlank())
        }
    }

    @Test fun failureCategoriesRemainActionable() {
        assertEquals("Не удалось найти адрес сервера подписки. Проверьте DNS и сеть.",
            SubscriptionErrorText.downloadFailure(UnknownHostException("secret")))
        assertEquals("Превышено время ожидания ответа подписки.",
            SubscriptionErrorText.downloadFailure(SocketTimeoutException("secret")))
        assertEquals("Не удалось установить защищённое HTTPS-соединение. Проверьте сертификат и дату устройства.",
            SubscriptionErrorText.downloadFailure(SSLHandshakeException("secret")))
        assertEquals("Не удалось загрузить подписку. Проверьте адрес и доступность сервера.",
            SubscriptionErrorText.downloadFailure(IOException("secret")))
    }
}
