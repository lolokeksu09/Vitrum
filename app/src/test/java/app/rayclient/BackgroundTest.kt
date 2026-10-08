package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class BackgroundTest {
    @Test fun hintDependsOnManufacturerAndNeverEmpty() {
        assertTrue(Background.hint("Xiaomi").startsWith("Xiaomi")); assertEquals(Background.hint("xiaomi"), Background.hint("  Redmi".trim().let { "XIAOMI" }))
        assertTrue(Background.hint("samsung").startsWith("Samsung")); assertTrue(Background.hint("HONOR").startsWith("Huawei"))
        assertTrue(Background.hint("OnePlus").startsWith("Oppo")); assertTrue(Background.hint("vivo").startsWith("Vivo"))
        for (m in listOf("", "Google", "Nokia", "???")) assertTrue(m, Background.hint(m).isNotBlank())
        assertEquals(Background.hint("Google"), Background.hint("Nokia"))
    }
}
