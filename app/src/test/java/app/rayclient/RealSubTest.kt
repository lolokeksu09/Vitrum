package app.rayclient

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RealSubTest {
    @Test fun real() {
        val src = File(System.getProperty("user.home"), "links.txt")
        assumeTrue("нет ~/links.txt (ссылки подписки): тест пропущен, а не пройден", src.exists())
        val dir = File("/tmp/vitrum-test/cfgtest2").apply { deleteRecursively(); mkdirs() }
        var ok = 0; val bad = StringBuilder()
        src.readLines().filter { Links.isSupported(it) }.forEachIndexed { i, l ->
            runCatching {
                val p = Links.parse(l)
                File(dir, "%02d-%s.json".format(i, p.proto)).writeText(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(transport = 0)))
                ok++
            }.onFailure { bad.append("$i ${it.message}\n") }
        }
        File(dir, "_summary.txt").writeText("ok=$ok\n$bad")
    }
}
