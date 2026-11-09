package app.rayclient

import android.content.Context

/** Компонент, который входит в приложение или с которым оно работает. license — короткое название, text — ключ полного текста в res/raw/license_texts.txt. */
class Component(val name: String, val version: String, val license: String, val text: String?, val url: String, val note: String)

object Licenses {
    /** Строка про лицензию самого приложения. Здесь её удобно менять, когда решите, под какой лицензией публиковать исходники. */
    const val APP_NOTE = "© 2026 Lolokeksu. Лицензия: GNU GPL v3 (только версия 3). Исходный код: github.com/lolokeksu09/Vitrum. Код DUORAY не копировался."

    /** Отметка о том, как ведётся разработка. */
    const val AI_NOTE = "Разработка ведётся с помощью ИИ-ассистента (Claude Code): код и документацию в основном пишет ассистент, автор ставит задачи, принимает решения и проверяет работу на телефоне."

    // Лицензии зависимостей проверены по POM-файлам настоящих артефактов: у 93 из 95 библиотек в POM указана Apache-2.0. У двух (ZXing core и Guava listenablefuture) блока лицензии в POM нет:
    // у ZXing core Apache-2.0 указана в заголовках файлов проекта, у listenablefuture (часть Guava) она принята по лицензии проекта Guava (отдельно не проверялась). Тексты лицензий побайтно сверены с оригиналами.
    val components = listOf(
        Component("Xray-core", XRAY_VERSION, "MPL-2.0", "mpl2", "https://github.com/XTLS/Xray-core",
            "Ядро, которое устанавливает защищённое соединение. Входит в приложение без изменений. Исходный код этой версии: github.com/XTLS/Xray-core, метка v26.3.27."),
        Component("Модули Go в составе Xray: sagernet/sing и sing-shadowsocks", "v0.5.1 / v0.2.7", "GPL-3.0-or-later", "gpl3", "https://github.com/SagerNet/sing",
            "© 2022 nekohasekai. Входят в программу xray вместе с Xray-core без изменений. Исходный код модулей этих версий: github.com/SagerNet/sing (метка v0.5.1) и github.com/SagerNet/sing-shadowsocks (метка v0.2.7); исходный код Xray-core: github.com/XTLS/Xray-core (метка v26.3.27)."),
        Component("Модуль Go в составе Xray: juju/ratelimit", "v1.0.2", "LGPL-3.0 с исключением", "ratelimit", "https://github.com/juju/ratelimit",
            "© 2015 Canonical Ltd. Входит в программу xray без изменений. Лицензия LGPL-3.0 с особым исключением для сборок, которые линкуются с библиотекой; текст приведён полностью. Исходный код: github.com/juju/ratelimit (метка v1.0.2)."),
        Component("Базы geoip и geosite", "из поставки Xray", "MIT / CC BY-SA 4.0", "mit+ccbysa", "https://github.com/v2fly/domain-list-community",
            "Списки доменов и IP-адресов по странам и сервисам. По умолчанию Xray берёт базы проекта v2fly: geosite под MIT, geoip под CC BY-SA 4.0. Точный источник файлов в архиве Xray не указан."),
        Component("ZXing и zxing-android-embedded", "3.5.3 / 4.3.0", "Apache-2.0", "apache2", "https://github.com/zxing/zxing",
            "Чтение QR-кодов камерой и с картинки."),
        Component("AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, WorkManager", "95 библиотек", "Apache-2.0", "apache2", "https://developer.android.com/jetpack",
            "Интерфейс, фоновые задачи и основа приложения. Лицензия 93 библиотек проверена по их POM-файлам. У ZXing core и Guava listenablefuture в POM лицензии нет: у первой Apache-2.0 указана проектом, у второй (часть Guava) принята по лицензии проекта Guava."),
        Component("Шрифт Manrope", "variable font", "SIL OFL 1.1", "ofl", "https://github.com/googlefonts/manrope",
            "© 2018–2019 The Manrope Project Authors."),
        Component("База IP для России (runetfreedom)", "скачивается по желанию", "GPL-3.0", null, "https://github.com/runetfreedom/russia-v2ray-rules-dat",
            "Лицензия GPL-3.0: файл LICENSE в репозитории содержит текст GNU GPL версии 3 (проверено 2026-10-08). Файл geoip.dat скачивается, только если вы включили эту базу, и в приложение не вшит. Он проверяется по контрольной сумме из релиза."),
        Component("Подсети игровых серверов (RIPEstat)", "запрашивается", "условия RIPEstat", null, "https://stat.ripe.net/docs/data_api",
            "Подсети Riot, Blizzard и Valve запрашиваются у открытого API RIPEstat, если вы включили эти наборы. В приложение данные не вшиты, на телефоне хранится только кэш."),
        Component("Список доменов «белого списка»", "снимок и ежедневное обновление", "лицензия не указана", null, "https://github.com/hxehex/russia-mobile-internet-whitelist",
            "Домены, которые открываются в мобильном интернете России при ограничениях. В репозитории лицензия не указана; это список адресов, но чужой файл поставляется с приложением как снимок."),
    )

    val services = listOf(
        "api.ipify.org, icanhazip.com, ifconfig.me: узнать ваш IP (проверка «Щита»). Запрос идёт, только когда вы сами запускаете проверку.",
        "cp.cloudflare.com: замер задержки серверов (адрес можно изменить в настройках).",
        "raw.githubusercontent.com: ежедневное обновление белого списка, если он включён.",
        "stat.ripe.net: подсети игровых серверов Steam, Riot и Battle.net, если вы их включили.",
        "github.com (runetfreedom): свежая база IP для России, если вы её включили (около 18 МБ).",
        "Адрес вашей подписки и сервера из неё: список серверов и само соединение.",
        "Адрес update.json, если вы его указали: проверка новой версии.",
    )

    val thanks = "Vitrum — Android-аналог по мотивам десктопного клиента DUORAY сервиса DUALIZM (Rust, GPL-3.0, github.com/m-a-prod/DUORAY): идея, набор функций. Код и тексты DUORAY не копировались; совпадают только общие служебные названия («Через VPN», «Белый список», «TCP и UDP»)."

    @Volatile private var texts: Map<String, String>? = null

    /** Файл license_texts.txt состоит из блоков «=====LICENSE:ключ=====» с полным текстом лицензии. */
    fun parse(all: String): Map<String, String> {
        val keys = Regex("=====LICENSE:(\\w+)=====").findAll(all).map { it.groupValues[1] }.toList()
        val bodies = Regex("=====LICENSE:\\w+=====\\n").split(all).drop(1)
        return keys.zip(bodies).associate { (k, b) -> k to b.trim() }
    }

    /** key — один ключ или несколько через «+» (тексты показываются подряд). */
    fun text(ctx: Context, key: String): String {
        val m = texts ?: runCatching { parse(ctx.resources.openRawResource(R.raw.license_texts).bufferedReader(Charsets.UTF_8).readText()) }.getOrDefault(emptyMap()).also { texts = it }
        return key.split('+').joinToString("\n\n————————————\n\n") { m[it] ?: "" }
    }
}
