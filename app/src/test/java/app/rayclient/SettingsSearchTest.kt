package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class SettingsSearchTest {
    @Test fun emptySearchShowsEverySection() {
        assertTrue(SettingsSearch.matches(" ", "Сеть и DNS", "Трафик, замер задержки"))
    }

    @Test fun matchesCaseInsensitiveAcrossTitleAndDescription() {
        assertTrue(SettingsSearch.matches("DNS трафик", "Сеть и DNS", "Трафик и замер задержки"))
        assertTrue(SettingsSearch.matches("килл", "Защита", "Килл свитч и журнал"))
    }

    @Test fun unrelatedTermsDoNotMatch() {
        assertFalse(SettingsSearch.matches("dns root", "Сеть и DNS", "DNS и трафик"))
        assertFalse(SettingsSearch.matches("vpn", "Оформление", "Тема"))
    }
}
