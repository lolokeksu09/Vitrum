package app.rayclient

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * UI messages for subscription failures. Never include server-controlled response
 * content, headers, exception messages or subscription URLs in user-visible errors.
 */
internal object SubscriptionErrorText {
    fun unsupportedResponse(contentType: String?): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return when (type) {
            "text/html", "application/xhtml+xml" ->
                "Вместо списка серверов получена HTML-страница. Проверьте адрес подписки."
            "application/json" ->
                "В подписке нет поддерживаемых серверов (JSON-ответ). Проверьте формат подписки."
            else ->
                "В подписке нет поддерживаемых серверов. Проверьте формат подписки."
        }
    }

    fun downloadFailure(error: Exception): String = when (error) {
        is UnknownHostException -> "Не удалось найти адрес сервера подписки. Проверьте DNS и сеть."
        is SocketTimeoutException -> "Превышено время ожидания ответа подписки."
        is SSLException -> "Не удалось установить защищённое HTTPS-соединение. Проверьте сертификат и дату устройства."
        is ConnectException -> "Не удалось подключиться к серверу подписки."
        else -> "Не удалось загрузить подписку. Проверьте адрес и доступность сервера."
    }
}
