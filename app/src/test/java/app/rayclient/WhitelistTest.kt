package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WhitelistTest {
    @Test fun bundledListPassesUnchanged() {
        val raw = File("src/main/assets/whitelist.txt").readText()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("все домены из встроенного списка проходят проверку", lines, Whitelist.clean(raw)!!.lines().filter { it.isNotEmpty() })
    }

    @Test fun junkIsDroppedAndShortListRejected() {
        val domains = (1..60).map { "host$it.example.ru" }
        val junk = listOf("<html>", "1.2.3.4", "*.evil.com", "geosite:google", "regexp:.*", "a b.ru", "-bad.ru", "# комментарий")
        val out = Whitelist.clean((domains + junk).joinToString("\n"))!!.lines().filter { it.isNotEmpty() }
        assertEquals(domains + "# комментарий", out)
        assertNull("меньше 50 доменов: ответ сломан или подменён", Whitelist.clean((1..49).joinToString("\n") { "h$it.example.ru" }))
        assertNull(Whitelist.clean("<html><body>404</body></html>"))
    }
}
