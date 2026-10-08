package app.rayclient

import android.app.ActivityManager
import android.content.Context

/**
 * «Стереть все данные»: просит систему очистить данные приложения, как «Очистить данные» в настройках Android (настройки, файлы, кэш, задания
 * по расписанию). Система сама закрывает процесс, поэтому VPN отключается вместе с ним. Ключ Vault система убирает из Keystore вместе с данными
 * (предположение по устройству Android, не проверено); сами ссылки без файлов и настроек всё равно недоступны. Ключ заранее не удаляем: при отказе
 * системы данные остались бы нечитаемыми. false — система отказала, данные на месте.
 */
object DataWipe {
    fun run(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching { am.clearApplicationUserData() }.getOrDefault(false)
    }
}
