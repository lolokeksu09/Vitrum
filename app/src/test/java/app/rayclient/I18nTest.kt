package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class I18nTest {
    private val CYR = Regex("[А-Яа-яЁё]")
    private val PH = Regex("""\{(\d+)\}""")
    private fun loadReal() { I18n.load(File("src/main/res/raw/i18n_en.tsv").readText(Charsets.UTF_8)) }

    @Test fun everyEntryTranslatesToItselfWithoutLeftovers() {
        loadReal()
        val rows = File("src/main/res/raw/i18n_en.tsv").readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { it.split('\t') }
        assertTrue("словарь не пуст", rows.size > 400)
        val problems = ArrayList<String>()
        for ((kind, ru, en) in rows) {
            assertFalse("в английском тексте осталась кириллица: $en", CYR.containsMatchIn(en.replace(".рф", "")))
            assertEquals("плейсхолдеры должны совпадать: $ru", PH.findAll(ru).map { it.value }.sorted().toList(), PH.findAll(en).map { it.value }.sorted().toList())
            val vals = PH.findAll(ru).map { it.groupValues[1].toInt() }.distinct().sorted()
            fun fill(t: String) = PH.replace(t) { m -> "v" + m.groupValues[1] }
            val input = if (kind == "P") fill(ru) + (if (ru.endsWith(":")) " " else " ") + "42" else fill(ru)
            val expected = if (kind == "P") fill(en) + " 42" else fill(en)
            val got = I18n.translate(input)
            if (got != expected) problems.add("[$kind] «$input» -> «$got», ожидалось «$expected»")
        }
        assertTrue("записи, которые переводятся не так, как задумано:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun composedStrings() {
        loadReal()
        val cases = mapOf(
            "Отключить" to "Disconnect",
            "Подключено: Nederland" to "Connected: Nederland",
            "Подключено: Vitrum-srv · пинг 48 мс · джиттер 5 мс · потери 0%" to "Connected: Vitrum-srv · ping 48 ms · jitter 5 ms · loss 0%",
            "tcp:443 · 12 раз · 14:05:12 · по правилу" to "tcp:443 · 12 times · 14:05:12 · by rule",
            "98% за сутки · 120 мс" to "98% per day · 120 ms",
            "Сейчас: Wi-Fi" to "Now: Wi-Fi",
            "Сейчас: Мобильная сеть" to "Now: Mobile network",
            "до 4 Nov 2026" to "until 4 Nov 2026",
            "52 серверов · обновлено 4 Oct, 06:47" to "52 servers · updated 4 Oct, 06:47",
            "0,0 из 300 ГБ · до 4 Nov 2026" to "0,0 of 300 GB · until 4 Nov 2026",
            "Отключить\nПодключиться" to "Disconnect\nConnect",
            "  Подключиться " to "  Connect ",
            "Правило добавлено: example.com · напрямую" to "Rule added: example.com · direct",
            "Правило добавлено: ya.ru · через VPN" to "Rule added: ya.ru · via VPN",
            "Ошибка: Сервер не отвечает (таймаут)" to "Error: The server is not responding (timeout)",
            "Нет ответа" to "No response",
            "Лицензия: Apache-2.0" to "License: Apache-2.0",
            "Лицензия: лицензия не указана" to "License: license not stated",
            "О приложении" to "About the app",
            "340 КБ/с" to "340 KB/s", "12,5 МБ" to "12,5 MB",
            "Vitrum 0.19.0 · by Lolokeksu · ядро Xray 26.3.27" to "Vitrum 0.19.0 · by Lolokeksu · core: Xray 26.3.27",
        )
        for ((ru, en) in cases) assertEquals(ru, en, I18n.translate(ru))
    }

    @Test fun userDataIsNotMangled() {
        loadReal()
        // названия серверов и профилей из панели остаются как есть, даже если рядом есть похожие слова
        for (name in listOf("🇳🇱 Россия -> Нидерланды (Мост 0x)", "Нидерланды", "Эстония (Таллин 0x)", "ya.ru", "Свой сервер домой")) assertEquals(name, I18n.translate(name))
        assertEquals("", I18n.translate("")); assertEquals("1.1.1.1", I18n.translate("1.1.1.1"))
    }
}
