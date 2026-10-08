package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Строки пакета Г должны быть в английском словаре (иначе у англоязычного пользователя останется русский текст). */
class PrivacyTextsTest {
    @Test fun newTextsHaveEnglish() {
        val tsv = listOf("src/main/res/raw/i18n_en.tsv", "app/src/main/res/raw/i18n_en.tsv").map(::File).firstOrNull { it.exists() } ?: return
        val ru = tsv.readLines().filter { it.startsWith("S\t") }.map { it.split('\t')[1] }.toSet()
        for (s in listOf("Блокировка приложения", "Приватные уведомления", "Стереть все данные", "VPN работает", "Сценарии по сети включены", "Не удалось стереть данные"))
            assertTrue("нет перевода: $s", s in ru)
    }
}
