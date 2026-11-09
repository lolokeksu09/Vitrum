package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    private val H = 3600_000L
    @Test fun rating() {
        Health.clear()
        val now = 1_000_000_000_000L
        // A: стабильный (20 из 20), 150 мс. B: быстрый, но нестабильный (12 из 20), 70 мс. C: мало данных. D: всегда мёртвый.
        for (i in 0 until 20) { Health.add("A", true, 150, now - (20 - i) * H / 2); Health.add("B", i % 5 < 3, 70, now - (20 - i) * H / 2); Health.add("D", false, 0, now - (20 - i) * H / 2) }
        Health.add("C", true, 60, now - H)
        val a = Health.rank("A", null, now); val b = Health.rank("B", null, now); val c = Health.rank("C", null, now); val d = Health.rank("D", null, now)
        println("A=$a B=$b C=$c D=$d")
        assertTrue("стабильный выше быстрого, но нестабильного", a > b)
        assertTrue("мало данных не обгоняет стабильный", a > c)
        assertTrue("мёртвый хуже всех", d < b && d < c)
        val sa = Health.stats("A", now)!!
        assertEquals(1.0, sa.uptime, 1e-9); assertEquals(150, sa.medianMs); assertEquals(12, sa.dots.size)
        assertEquals(0.6, Health.stats("B", now)!!.uptime, 1e-9)
        assertNull(Health.stats("нет такого", now))
    }
    @Test fun windowAndPersistence() {
        Health.clear()
        val now = 2_000_000_000_000L
        Health.add("X", false, 0, now - 30 * H)   // старше суток: в статистику не входит
        Health.add("X", true, 100, now - 2 * H); Health.add("X", true, 120, now - H); Health.add("X", true, 110, now)
        val st = Health.stats("X", now)!!
        assertEquals(3, st.n); assertEquals(110, st.medianMs)
        val json = Health.toJson(); Health.clear(); assertNull(Health.stats("X", now)); Health.fromJson(json)
        assertEquals(3, Health.stats("X", now)!!.n)
        for (i in 0 until 400) Health.add("Y", true, 10, now - 400 + i)   // кольцевой буфер ограничен
        assertTrue(Health.count() <= 4 + 200)
    }
}
