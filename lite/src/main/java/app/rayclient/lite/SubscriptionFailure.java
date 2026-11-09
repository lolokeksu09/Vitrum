package app.rayclient.lite;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathBuilderException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/** Stable user-facing error categories. Never render exception.getMessage():
 * network/TLS exceptions can contain credentials or a private subscription URL. */
final class SubscriptionFailure {
    private SubscriptionFailure() {}

    static String describe(Exception failure) {
        // All validation and HTTP errors raised by our own fetcher have controlled text.
        if (failure instanceof IllegalArgumentException &&
            !(failure instanceof java.net.MalformedURLException))
            return failure.getMessage() == null ? "Неверная подписка" : failure.getMessage();

        if (hasCause(failure, UnknownHostException.class))
            return "Не найден адрес сервера подписки (DNS). Проверь интернет или DNS.";
        if (hasCause(failure, SocketTimeoutException.class))
            return "Время ожидания подписки истекло. Проверь подключение к интернету.";
        if (hasCause(failure, NoRouteToHostException.class))
            return "Нет маршрута до сервера подписки. Проверь интернет.";
        if (hasCause(failure, ConnectException.class))
            return "Сервер подписки не принимает соединение. Проверь сеть.";
        if (hasCause(failure, CertificateException.class) ||
            hasCause(failure, CertPathValidatorException.class) ||
            hasCause(failure, CertPathBuilderException.class) ||
            hasCause(failure, SSLPeerUnverifiedException.class))
            return "Не удалось подтвердить HTTPS-сертификат. Проверь дату и время планшета и сертификат сервера.";
        if (hasCause(failure, SSLHandshakeException.class))
            return "Ошибка TLS при подключении к подписке. Сервер может не поддерживать TLS 1.2, доступный на Android 6.";
        if (hasCause(failure, SSLException.class))
            return "Защищённое HTTPS-соединение прервалось. Проверь поддержку TLS сервером.";
        if (hasCause(failure, SecurityException.class))
            return "Android запретил сетевое подключение к подписке.";
        if (hasCause(failure, IOException.class))
            return "Не удалось связаться с сервером подписки. Проверь сеть.";
        return "Не удалось загрузить подписку. Проверь соединение.";
    }

    private static boolean hasCause(Throwable error, Class<?> cls) {
        for (int i = 0; error != null && i < 8; i++, error = error.getCause()) {
            if (cls.isInstance(error)) return true;
            if (error.getCause() == error) break;
        }
        return false;
    }
}
