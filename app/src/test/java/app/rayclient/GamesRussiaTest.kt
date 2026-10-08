package app.rayclient

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GamesRussiaTest {
    private fun fx(n: String) = File("src/test/fixtures/$n.json").readText()

    @Test fun realRipeStatResponsesParse() {
        val riot = AsnPrefixes.parse(fx("AS6507")); val bliz = AsnPrefixes.parse(fx("AS57976")); val valve = AsnPrefixes.parse(fx("AS32590"))
        assertEquals("в ответе 37 префиксов, все разумного размера", 37, riot.size); assertEquals(182, bliz.size); assertEquals(78, valve.size)
        assertTrue(riot.contains("151.106.248.0/24")); assertTrue(bliz.any { ':' in it }); assertTrue(valve.contains("162.254.193.0/24"))
        (riot + bliz + valve).forEach { assertTrue("префикс $it прошёл проверку", AsnPrefixes.valid(it)) }
    }

    @Test fun dangerousOrBrokenPrefixesAreRejected() {
        val json = """{"data":{"prefixes":[{"prefix":"0.0.0.0/0"},{"prefix":"8.0.0.0/8"},{"prefix":"10.1.0.0/11"},{"prefix":"203.0.113.0/24"},{"prefix":"300.1.1.0/24"},
            {"prefix":"2001:db8::/16"},{"prefix":"2001:db8::/48"},{"prefix":"::/0"},{"prefix":"hello"},{"prefix":"1.2.3.4"},{"prefix":""},{}]}}"""
        assertEquals("остаются только разумные: /24 IPv4 и /48 IPv6", listOf("203.0.113.0/24", "2001:db8::/48"), AsnPrefixes.parse(json))
        assertTrue(AsnPrefixes.valid("192.0.2.0/12")); assertFalse(AsnPrefixes.valid("192.0.2.0/11")); assertTrue(AsnPrefixes.valid("203.0.113.5/32"))
        assertTrue("настоящий IPv6-блок Riot /29 принимается", AsnPrefixes.valid("2a04:82c0::/29")); assertFalse("целый регистр /12 — нет", AsnPrefixes.valid("2a00::/12")); assertFalse(AsnPrefixes.valid("2000::/3"))
    }

    @Test fun presetsReferenceRealAsnsAndKnownTags() {
        val ids = PRESETS.map { it.id }; assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("AS6507"), PRESETS.first { it.id == "riot" }.asns); assertEquals(listOf("AS57976"), PRESETS.first { it.id == "blizzard" }.asns); assertEquals(listOf("AS32590"), PRESETS.first { it.id == "steam" }.asns)
        PRESETS.forEach { p -> assertTrue(p.tags.isNotEmpty()); p.asns.forEach { assertTrue(Regex("AS\\d+").matches(it)) } }
        assertTrue("сохранённые профили со старыми id (steam, epic, faceit, telegram) продолжают работать", listOf("steam", "epic", "faceit", "telegram").all { it in ids })
    }

    @Test fun configCarriesSubnetsAndWhitelistTag() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&pbk=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg&sid=ab12&sni=www.microsoft.com&fp=chrome#R"
        val p = Links.parse(link)
        val ips = AsnPrefixes.parse(fx("AS6507")) + AsnPrefixes.parse(fx("AS57976")) + AsnPrefixes.parse(fx("AS32590"))
        val opts = RoutingOptions(bypassPresets = setOf("riot", "blizzard", "steam", "telegram"), whitelistDomains = listOf("yandex.ru", "vk.com"), exceptRu = true, presetIps = ips, whitelistOn = true, geoRu = true)
        val cfg = org.json.JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), opts, "/tmp/x/main.sock"))
        val rules = cfg.getJSONObject("routing").getJSONArray("rules"); val all = (0 until rules.length()).map { rules.getJSONObject(it) }
        val direct = all.filter { it.optString("outboundTag") == "direct" }
        val directIps = direct.flatMap { r -> r.optJSONArray("ip")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList() }
        assertTrue("подсети Riot, Blizzard и Valve ушли в правила «напрямую»", directIps.containsAll(listOf("151.106.248.0/24", "162.254.193.0/24")) && ips.all { it in directIps })
        assertTrue("метка белого списка добавлена", "geoip:ru-whitelist" in directIps)
        // без подключённой базы runetfreedom метка не появляется (иначе Xray не запустится)
        val off = org.json.JSONObject(ConfigBuilder.build(p, Links.withAddress(p, "203.0.113.5"), RoutingOptions(whitelistDomains = listOf("yandex.ru"), whitelistOn = true, geoRu = false), "/tmp/x/main.sock")).toString()
        assertFalse(off.contains("ru-whitelist"))
        File("/tmp/vitrum-test/cfgtest12").apply { mkdirs() }.resolve("games-on.json").writeText(cfg.toString())
        File("/tmp/vitrum-test/cfgtest12/games-off.json").writeText(off)
    }

    @Test fun shaFileParsingAndDownloadVerification() {
        assertEquals("71622fe772552cd30cc161cc1b49d25b3ee51c90c579001e4fd1222d30edcce2", GeoData.parseSha("71622fe772552cd30cc161cc1b49d25b3ee51c90c579001e4fd1222d30edcce2  geoip.dat\n"))
        assertNull(GeoData.parseSha("not a hash")); assertNull(GeoData.parseSha("abc123"))
        // настоящая загрузка: ≈18 МБ с GitHub, проверка суммы и отказ при подмене
        val reachable = runCatching { (URL(GeoData.BASE + "geoip.dat.sha256sum").openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }.inputStream.use { it.read() } >= 0 }.getOrDefault(false)
        assumeTrue("нет доступа к GitHub: проверка загрузки пропущена", reachable)
        val dest = File.createTempFile("geoip", ".dat"); dest.delete()
        assertNull("загрузка и сверка суммы прошли", GeoData.fetchVerified(GeoData.BASE + "geoip.dat", GeoData.BASE + "geoip.dat.sha256sum", dest))
        assertTrue(dest.length() > 10_000_000); assertEquals(GeoData.sha256(dest), GeoData.parseSha(URL(GeoData.BASE + "geoip.dat.sha256sum").readText()))
        // подмена: контрольная сумма от другого файла (geosite) не должна подойти к geoip
        val bad = File.createTempFile("bad", ".dat"); bad.delete()
        val err = GeoData.fetchVerified(GeoData.BASE + "geoip.dat", GeoData.BASE + "geosite.dat.sha256sum", bad)
        assertEquals("контрольная сумма не совпала", err); assertFalse("после отказа файл не остаётся", bad.exists())
        assertEquals("нужен https", "IllegalArgumentException: нужен https", GeoData.fetchVerified("http://example.com/x", "http://example.com/x.sha", File("/tmp/zz")))
        dest.delete()
    }
}
