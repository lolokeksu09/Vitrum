package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAppsTest {
    private val sber = "ru.sberbankmobile"
    private val gos = "ru.rostel"

    @Test fun listIsSane() {
        val pk = BankApps.list.map { it.first }
        assertEquals("пакеты не повторяются", pk.size, pk.toSet().size)
        assertTrue(pk.all { Regex("[a-z][a-z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+").matches(it) })
        assertFalse("не наш пакет", "app.rayclient" in pk)
        assertTrue(BankApps.list.all { it.second.isNotBlank() })
    }

    @Test fun onlyInstalledAreFound() {
        val p = BankApps.plan(0, emptyList(), setOf(sber, "com.other.app"))
        assertEquals(listOf(sber), p.found)
        assertEquals(listOf(sber), p.added)
        assertEquals(2, p.mode)
        assertFalse(p.flipsMeaning)
    }

    @Test fun alreadyAddedAreNotAddedAgain() {
        val p = BankApps.plan(2, listOf(sber), setOf(sber, gos))
        assertEquals(listOf(sber, gos), p.found)
        assertEquals(listOf(gos), p.added)
        assertFalse(p.flipsMeaning)
    }

    @Test fun nothingInstalledMeansNothingFound() {
        val p = BankApps.plan(0, listOf("x.y"), setOf("a.b"))
        assertTrue(p.found.isEmpty())
        assertTrue(p.added.isEmpty())
    }

    @Test fun onlySelectedModeWithAppsChangesMeaning() {
        // режим «Только выбранные»: список шёл через VPN, после переключения на «Кроме выбранных» пойдёт мимо
        assertTrue(BankApps.plan(1, listOf("x.y"), setOf(sber)).flipsMeaning)
        // пустой список в режиме 1 ничего не переворачивает
        assertFalse(BankApps.plan(1, emptyList(), setOf(sber)).flipsMeaning)
    }

    @Test fun namesFollowListOrder() {
        assertEquals(listOf("СберБанк Онлайн", "Госуслуги"), BankApps.names(listOf(gos, sber)))
    }
}
