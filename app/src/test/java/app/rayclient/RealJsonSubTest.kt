package app.rayclient

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Настоящий ответ /json (файл лежит вне проекта: $HOME/secrets/sub.json, в нём ключи серверов). Без файла тест пропускается.
 * Пишет конфигурации в /tmp/vitrum-test/json: дальше их проверяют `xray run -test` и живой запуск (tools вне проекта).
 */
class RealJsonSubTest {
    private val src = File(System.getProperty("user.home"), "secrets/sub.json")

    @Test fun everyBalancerConfigBuildsForBothRoutingVariants() {
        assumeTrue("нет $src: проверка на настоящей подписке пропущена", src.exists())
        val dir = File("/tmp/vitrum-test/json").apply { deleteRecursively(); mkdirs() }
        val parsed = AutoSub.parse(src.readText(), "real")
        assertEquals("в ответе панели 12 конфигов с балансировщиком", 12, parsed.configs.size); assertEquals(0, parsed.skipped)
        val rich = RoutingOptions(rules = listOf(Rule(0, "example.org", 0), Rule(0, "direct.example", 1), Rule(1, "198.51.100.0/24", 2), Rule(2, "8080", 0)),
            bypassPresets = setOf("steam", "riot", "telegram"), transport = 0, exceptRu = true, dnsServers = listOf("1.1.1.1", "8.8.8.8"), doh = true, game = true)
        val summary = StringBuilder()
        parsed.configs.forEachIndexed { i, ref ->
            val ips = AutoSub.resolveAll(AutoSub.domains(ref.plan), 10_000) { n -> InetAddress.getAllByName(n).let { a -> (a.firstOrNull { it is Inet4Address } ?: a.first()).hostAddress } }
            val plan = AutoSub.pinAddresses(ref.plan) { ips[it] }
            assertNotNull("конфиг #$i: после подстановки адресов не осталось серверов", plan)
            assertTrue("конфиг #$i: все домены заменены на IP", AutoSub.domains(plan!!).isEmpty())
            for ((v, o) in listOf("base" to RoutingOptions(), "rich" to rich)) {
                val text = ConfigBuilder.buildAuto(plan, o, "/tmp/vitrum-test/json/main-$i.sock", "/tmp/vitrum-test/json/access-$i.log")
                File(dir, "%02d-%s.json".format(i, v)).writeText(text)
                assertFalse(text.contains("10808")); assertFalse(text.contains("10809"))
            }
            summary.append("#%02d серверов %d из %d, балансировщиков %d, доменов разрешено %d\n".format(i, plan.outbounds.size, ref.plan.outbounds.size, plan.balancers.size, ips.size))
        }
        File(dir, "summary.txt").writeText(summary.toString())
    }
}
