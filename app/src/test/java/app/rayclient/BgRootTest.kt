package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class BgRootTest {
    @Test fun everyCommandPassesTheRootShellCheckAndTouchesOnlyOurPackage() {
        val all = BgRoot.applyCommands("app.rayclient")!! + BgRoot.revertCommands("app.rayclient")!! + listOf(BgRoot.statusCommand("app.rayclient")!!)
        all.forEach { assertTrue(it, RootShell.isSafe(it)); assertTrue(it, it.contains("app.rayclient")) }
        assertEquals(3, BgRoot.applyCommands("app.rayclient")!!.size); assertEquals(3, BgRoot.revertCommands("app.rayclient")!!.size)
        assertEquals("dumpsys deviceidle whitelist +app.rayclient", BgRoot.applyCommands("app.rayclient")!![0])
        assertEquals("dumpsys deviceidle whitelist -app.rayclient", BgRoot.revertCommands("app.rayclient")!![0])
        assertTrue(BgRoot.applyCommands("app.rayclient")!!.any { it.endsWith("RUN_ANY_IN_BACKGROUND allow") })
        assertTrue(BgRoot.revertCommands("app.rayclient")!!.all { it.contains("whitelist -") || it.endsWith(" default") })
    }

    @Test fun badPackageNamesAreRefused() {
        listOf("", "a b", "app;reboot", "app\$(id)", "app`id`", "a/b", "x".repeat(101)).forEach {
            assertNull(it, BgRoot.applyCommands(it)); assertNull(BgRoot.revertCommands(it)); assertNull(BgRoot.statusCommand(it))
        }
    }

    @Test fun modeIsParsedFromAppOpsOutput() {
        assertEquals("allow", BgRoot.parseMode("RUN_ANY_IN_BACKGROUND: allow"))
        assertEquals("allow", BgRoot.parseMode("RUN_ANY_IN_BACKGROUND: allow; time=+1d3h (running)"))
        assertEquals("ignore", BgRoot.parseMode("RUN_ANY_IN_BACKGROUND: ignore"))
        assertEquals("default", BgRoot.parseMode("RUN_ANY_IN_BACKGROUND: default; rejected by uid mode"))
        assertNull(BgRoot.parseMode("")); assertNull(BgRoot.parseMode("No operations."))
    }

    @Test fun descriptionIsOneSentencePerLine() {
        val ok = BgRoot.describe(true, "allow", 0).lines()
        assertEquals(listOf("Экономия заряда для приложения: отключена.", "Запуск в фоне: разрешён."), ok)
        val bad = BgRoot.describe(false, "ignore", 2).lines()
        assertEquals(3, bad.size); assertTrue(bad[1].contains("ignore")); assertTrue(bad[2].contains("2"))
        assertTrue(BgRoot.describe(false, null, 0).contains("не удалось определить"))
    }
}
