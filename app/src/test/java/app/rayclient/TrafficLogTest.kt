package app.rayclient

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class TrafficLogTest {
    private val today = LocalDate.of(2026, 10, 7)
    @Before fun reset() = TrafficLog.clear()

    @Test fun sumsPerDayWeekAndMonth() {
        TrafficLog.add("2026-10-07", 100, 10); TrafficLog.add("2026-10-07", 50, 5); TrafficLog.add("2026-10-03", 1000, 100)
        TrafficLog.add("2026-09-30", 7000, 700); TrafficLog.add("2026-09-01", 1, 1)
        assertEquals(150L to 15L, TrafficLog.lastDays(1, today))
        assertEquals(1150L to 115L, TrafficLog.lastDays(7, today))
        assertEquals("месяц — октябрь, сентябрь не входит", 1150L to 115L, TrafficLog.month(today))
        assertEquals(7, TrafficLog.byDay(7, today).size); assertEquals("2026-10-07", TrafficLog.byDay(7, today).first().first)
        assertEquals(0L, TrafficLog.byDay(7, today)[1].second)
    }

    @Test fun ignoresEmptyAndNegativeAdds() {
        TrafficLog.add("2026-10-07", 0, 0); TrafficLog.add("2026-10-07", -5, -5); TrafficLog.add("2026-10-07", -5, 20)
        assertEquals(0L to 20L, TrafficLog.lastDays(1, today))
    }

    @Test fun jsonRoundTripKeepsOnlyRecentDays() {
        TrafficLog.add("2026-10-07", 5, 6); TrafficLog.add("2026-01-01", 9, 9)
        val json = TrafficLog.toJson(today)
        assertFalse("старше 62 дней отбрасывается", json.contains("2026-01-01"))
        TrafficLog.fromJson(json)
        assertEquals(5L to 6L, TrafficLog.lastDays(1, today))
    }

    @Test fun brokenJsonGivesEmptyLog() {
        TrafficLog.add("2026-10-07", 5, 6); TrafficLog.fromJson("не json"); assertEquals(0L to 0L, TrafficLog.lastDays(1, today))
        TrafficLog.fromJson("""{"плохая дата":[1,2],"2026-10-07":[3,4]}"""); assertEquals(3L to 4L, TrafficLog.lastDays(1, today))
    }
}

class TrafficLimitTest {
    private val gb = 1024L * 1024 * 1024
    @Test fun levels() {
        assertEquals(0, TrafficLimit.level(100 * gb, 0)); assertEquals(0, TrafficLimit.level(0, 10)); assertEquals(0, TrafficLimit.level(8 * gb - 1, 10))
        assertEquals(80, TrafficLimit.level(8 * gb, 10)); assertEquals(80, TrafficLimit.level(10 * gb - 1, 10)); assertEquals(100, TrafficLimit.level(10 * gb, 10)); assertEquals(100, TrafficLimit.level(50 * gb, 10))
    }

    @Test fun warnsOncePerLevelPerMonth() {
        assertFalse(TrafficLimit.shouldWarn(0, "2026-10", null))
        assertTrue(TrafficLimit.shouldWarn(80, "2026-10", null))
        assertFalse("тот же уровень повторно не сообщаем", TrafficLimit.shouldWarn(80, "2026-10", "2026-10:80"))
        assertTrue("100 после 80 сообщаем", TrafficLimit.shouldWarn(100, "2026-10", "2026-10:80"))
        assertFalse(TrafficLimit.shouldWarn(100, "2026-10", "2026-10:100"))
        assertTrue("новый месяц — снова", TrafficLimit.shouldWarn(80, "2026-11", "2026-10:100"))
        assertTrue(TrafficLimit.shouldWarn(80, "2026-10", "мусор"))
    }

    @Test fun monthKey() = assertEquals("2026-03", TrafficLimit.monthKey(LocalDate.of(2026, 3, 9)))
}

class MemLimitTest {
    @Test fun env() {
        assertNull(MemLimit.env(0)); assertEquals("128MiB", MemLimit.env(1)); assertEquals("256MiB", MemLimit.env(2)); assertNull(MemLimit.env(9)); assertNull(MemLimit.env(-1))
    }
}
