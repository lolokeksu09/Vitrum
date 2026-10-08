package app.rayclient

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class GameTest {
    @Test fun statsMath() {
        val l = GameStats.live(listOf(50, 60, 50, -1, 70))
        assertEquals(58, l.avg); assertEquals(20, l.loss); assertEquals(70, l.last)
        assertEquals(13, l.jitter)                       // (10+10+20)/3 = 13.3
        assertEquals(-1, GameStats.live(listOf(-1, -1)).avg); assertEquals(100, GameStats.live(listOf(-1, -1)).loss)
        assertEquals(0, GameStats.live(listOf(40)).jitter)
        val good = GameRow("a", "A", 60, 55, 70, 4, 0, 200, ""); val shaky = GameRow("b", "B", 45, 30, 150, 40, 10, 200, ""); val dead = GameRow("c", "C", -1, -1, -1, 0, 100, -1, "x")
        assertTrue(good.score < shaky.score); assertTrue(shaky.score < dead.score)
    }
    @Test fun targets() {
        val a = GameStats.parseTarget("http://cp.cloudflare.com/generate_204")!!
        assertEquals("cp.cloudflare.com", a.host); assertEquals(80, a.port); assertEquals("/generate_204", a.path)
        val s = GameStats.parseTarget("https://example.org/x")!!; assertEquals(80, s.port)     // https сводится к http
        val b = GameStats.parseTarget("game.example.net:8080")!!; assertEquals("game.example.net", b.host); assertEquals(8080, b.port); assertEquals("/", b.path)
        assertEquals(80, GameStats.parseTarget("example.org")!!.port)
        assertNull(GameStats.parseTarget("")); assertNull(GameStats.parseTarget("без-порта"))
    }
    @Test fun gameConfigs() {
        val dir = File("/tmp/vitrum-test/cfgtest9").apply { deleteRecursively(); mkdirs() }
        val src = File(System.getProperty("user.home"), "links.txt")
        assumeTrue("нет ~/links.txt (ссылки подписки): тест пропущен, а не пройден", src.exists())
        val links = src.readLines().filter { it.contains("://") }
        val seen = HashSet<String>()
        links.forEach { l ->
            val p = Links.parse(l); val st = p.outbound.optJSONObject("streamSettings")
            val key = p.proto + "-" + st?.optString("security") + "-" + st?.optString("network")
            if (seen.add(key)) File(dir, "$key.json").writeText(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(transport = 1, game = true), "/tmp/x/main.sock"))
        }
    }
}
