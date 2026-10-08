package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class UpdaterPickTest {
    private fun rel(tag: String, draft: Boolean = false, host: String = "https://github.com/lolokeksu09/Vitrum/releases/download", names: List<String> = listOf("Vitrum-$tag-debug-fallback.apk")) =
        """{"tag_name":"v$tag","draft":$draft,"prerelease":true,"body":"заметки $tag","assets":[""" +
            names.joinToString(",") { """{"name":"$it","browser_download_url":"$host/v$tag/$it","digest":"sha256:abc$tag"}""" } + "]}"
    private fun list(vararg r: String) = "[" + r.joinToString(",") + "]"

    @Test fun versionParsingAndComparison() {
        assertEquals(listOf(0, 25, 19), Updater.parseVersion("v0.25.19")); assertNull(Updater.parseVersion("abc")); assertNull(Updater.parseVersion("1"))
        assertTrue(Updater.isNewer(Updater.parseVersion("0.25.10")!!, Updater.parseVersion("0.25.9")!!))
        assertFalse("0.25.9 не новее 0.25.10 (строкой было бы наоборот)", Updater.isNewer(Updater.parseVersion("0.25.9")!!, Updater.parseVersion("0.25.10")!!))
        assertFalse(Updater.isNewer(listOf(0, 25), listOf(0, 25, 0)))
    }

    @Test fun picksTheNewestFileForTheBuildSignature() {
        val json = list(rel("0.25.19"), rel("0.25.20"), rel("0.25.18"))
        val u = Updater.pick(json, "0.25.19", releaseSigned = false)!!
        assertEquals("0.25.20", u.version); assertTrue(u.url.endsWith("Vitrum-0.25.20-debug-fallback.apk")); assertEquals("abc0.25.20", u.sha256)
        assertNull("уже последняя", Updater.pick(json, "0.25.20", false))
    }

    @Test fun releaseSignedBuildNeverGetsTheDebugFile() {
        assertNull("для сборки с ключом автора отладочный файл не предлагается: система не поставит его поверх", Updater.pick(list(rel("0.25.20")), "0.25.19", releaseSigned = true))
        val u = Updater.pick(list(rel("0.25.20", names = listOf("Vitrum-0.25.20.apk", "Vitrum-0.25.20-debug-fallback.apk"))), "0.25.19", releaseSigned = true)!!
        assertTrue(u.url.endsWith("Vitrum-0.25.20.apk"))
    }

    @Test fun draftsAndForeignHostsAreIgnored() {
        assertNull(Updater.pick(list(rel("0.25.20", draft = true)), "0.25.19", false))
        assertNull("файл не из релизов этого репозитория", Updater.pick(list(rel("0.25.20", host = "https://evil.example.com/dl")), "0.25.19", false))
        assertNull("http не принимается", Updater.pick(list(rel("0.25.20", host = "http://github.com/lolokeksu09/Vitrum/releases/download")), "0.25.19", false))
        assertNull(Updater.pick("[]", "0.25.19", false)); assertNull(Updater.pick(list(rel("0.25.20")), "не версия", false))
    }
}
