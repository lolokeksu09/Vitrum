package app.rayclient

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ProbeTest {
    @Test fun probe() {
        val src = File(System.getProperty("user.home"), "links.txt")
        assumeTrue("нет ~/links.txt (ссылки подписки): тест пропущен, а не пройден", src.exists())
        val dir = File("/tmp/vitrum-test/cfgtest3").apply { deleteRecursively(); mkdirs() }
        val items = src.readLines().filter { Links.isSupported(it) }.map { Links.parse(it) }.map { it to "203.0.113.5" }
        File(dir, "ping-all.json").writeText(ConfigBuilder.buildProbe(items, "/tmp/uxprobe"))
        val p = items.first().first
        File(dir, "main-probe.json").writeText(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"),
            RoutingOptions(rules = listOf(Rule(0, "example.org", 1)), exceptRu = true), "/tmp/uxprobe/main.sock"))
    }
}
