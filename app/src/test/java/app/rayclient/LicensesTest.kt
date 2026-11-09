package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LicensesTest {
    private val all = File("src/main/res/raw/license_texts.txt").readText(Charsets.UTF_8)
    private val texts = Licenses.parse(all)

    @Test fun fiveLicenseTextsArePresentAndRecognisable() {
        assertEquals(setOf("apache2", "mpl2", "ofl", "mit", "ccbysa", "gpl3", "ratelimit"), texts.keys)
        assertTrue(texts.getValue("gpl3").contains("GNU GENERAL PUBLIC LICENSE") && texts.getValue("gpl3").contains("Version 3, 29 June 2007"))
        assertTrue(texts.getValue("ratelimit").contains("LGPL3") && texts.getValue("ratelimit").contains("special exception"))
        assertTrue(texts.getValue("apache2").contains("Apache License") && texts.getValue("apache2").contains("Version 2.0"))
        assertTrue(texts.getValue("mpl2").startsWith("Mozilla Public License Version 2.0"))
        assertTrue(texts.getValue("ofl").contains("SIL Open Font License") && texts.getValue("ofl").contains("Manrope Project Authors"))
        assertTrue(texts.getValue("mit").startsWith("MIT License") && texts.getValue("mit").contains("V2Ray"))
        assertTrue(texts.getValue("ccbysa").contains("Attribution-ShareAlike 4.0"))
        texts.forEach { (k, v) -> assertTrue("текст $k не должен быть пустым или обрезанным", v.length > 900) }
    }

    @Test fun everyComponentPointsToExistingTextsAndAnHttpsSource() {
        for (c in Licenses.components) {
            assertTrue("${c.name}: ссылка", c.url.startsWith("https://"))
            assertTrue("${c.name}: версия", c.version.isNotBlank()); assertTrue("${c.name}: лицензия", c.license.isNotBlank())
            c.text?.split('+')?.forEach { assertTrue("${c.name}: нет текста «$it»", texts.containsKey(it)) }
        }
        val geo = Licenses.components.first { it.name.contains("geoip") }
        assertEquals("у баз geo должны быть доступны оба текста (MIT и CC BY-SA)", "mit+ccbysa", geo.text)
        assertEquals("без полного текста: белый список, база runetfreedom (скачивается), RIPEstat (запрашивается)", 3, Licenses.components.count { it.text == null })
    }

    @Test fun attributionFacts() {
        assertTrue(Licenses.APP_NOTE.contains("Lolokeksu"))
        assertTrue("отметка о разработке с ИИ", Licenses.AI_NOTE.contains("ИИ-ассистента"))
        assertTrue(Licenses.thanks.contains("GPL-3.0") && Licenses.thanks.contains("по мотивам") && Licenses.thanks.contains("не копировались"))
        val manrope = Licenses.components.first { it.name.contains("Manrope") }
        assertTrue("годы должны совпадать с авторскими строками шрифта и OFL", manrope.note.contains("2018") && manrope.note.contains("2019"))
    }
}
