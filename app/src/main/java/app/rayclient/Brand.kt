package app.rayclient

import android.content.Context

/** Имя приложения для текстов в коде. Для манифеста и системных меток — строка app_name в res/values/strings.xml. */
const val BRAND = "Vitrum"

/** Версия Xray-core в приложении. Меняется вместе с tools/FETCH_BINARIES.sh и tools/binaries.sha256. */
const val XRAY_VERSION = "26.3.27"

/** Версия приложения из системы (versionName в app/build.gradle.kts): вручную в коде её больше не пишем. */
fun appVersion(ctx: Context): String? = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
