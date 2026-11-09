package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CountriesTest {
    private val nl = "\uD83C\uDDF3\uD83C\uDDF1"   // 🇳🇱
    private val de = "\uD83C\uDDE9\uD83C\uDDEA"   // 🇩🇪
    private val ru = "\uD83C\uDDF7\uD83C\uDDFA"   // 🇷🇺

    @Test fun codeIsReadFromTheFlagAnywhereInTheName() {
        assertEquals("NL", Countries.flagCode("$nl Amsterdam")); assertEquals("DE", Countries.flagCode("Frankfurt $de 1x"))
        assertEquals("NL", Countries.flagCode("$nl $de two flags: first wins"))
        assertNull(Countries.flagCode("Amsterdam")); assertNull(Countries.flagCode("")); assertNull(Countries.flagCode("NL"))
    }

    @Test fun aLoneIndicatorIsNotACountry() {
        assertNull(Countries.flagCode("\uD83C\uDDF3 alone")); assertNull(Countries.flagCode("tail \uD83C\uDDF3"))
        assertEquals("RU", Countries.flagCode("\uD83C\uDDF3 x $ru"))   // одинокий индикатор в начале не мешает следующей настоящей паре
    }

    @Test fun flagAndCodeRoundTrip() {
        for (c in listOf("NL", "DE", "RU", "US", "JP")) assertEquals(c, Countries.flagCode(Countries.flag(c) + " x"))
        assertEquals(nl, Countries.flag("nl"))
    }

    @Test fun titleUsesTheAppLanguageAndFallsBackToTheCode() {
        assertTrue(Countries.title("NL", Locale("ru")).let { it.isNotBlank() && it != "NL" })
        assertEquals("XX", Countries.title("XX", Locale("ru")))
    }

    @Test fun countriesAreSortedByServerCountThenAlphabet() {
        val l = Countries.available(listOf("$de a", "$nl b", "$nl c", "$ru d", "plain", "$de e", "$nl f"))
        assertEquals(listOf("NL" to 3, "DE" to 2, "RU" to 1), l)
        assertEquals(listOf("DE" to 1, "NL" to 1), Countries.available(listOf("$nl a", "$de b")))
        assertTrue(Countries.available(listOf("a", "b")).isEmpty())
    }
}
