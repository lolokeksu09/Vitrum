package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AppTrafficTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 7)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test fun periodsStartAtMidnightWeekAndMonthStart() {
        assertEquals(day(2026, 10, 7), AppTraffic.periodStart(AppTraffic.Period.TODAY, today, utc))
        assertEquals(day(2026, 10, 1), AppTraffic.periodStart(AppTraffic.Period.WEEK, today, utc))   // 7 дней включая сегодня: 1–7 октября
        assertEquals(day(2026, 10, 1), AppTraffic.periodStart(AppTraffic.Period.MONTH, today, utc))
        assertEquals(day(2026, 9, 29), AppTraffic.periodStart(AppTraffic.Period.WEEK, LocalDate.of(2026, 10, 5), utc))
        assertEquals(day(2025, 12, 1), AppTraffic.periodStart(AppTraffic.Period.MONTH, LocalDate.of(2025, 12, 31), utc))
    }

    @Test fun rowsAreMergedBiggestFirstWithoutZeros() {
        val rows = AppTraffic.merge(mapOf(1 to 100L, 2 to 5000L, 3 to 0L), mapOf(1 to 900L, 4 to 7000L)) { "app$it" }
        assertEquals(listOf("app4", "app2", "app1"), rows.map { it.label })
        val a = rows.first { it.label == "app1" }
        assertEquals(1000L, a.total); assertEquals(100L, a.wifi); assertEquals(900L, a.mobile)
    }

    @Test fun uidsWithTheSameLabelAreSummedOnce() {
        val rows = AppTraffic.merge(mapOf(10 to 100L, 11 to 50L), mapOf(11 to 25L)) { if (it >= 10) "Shared" else "x" }
        assertEquals(1, rows.size); assertEquals(150L, rows[0].wifi); assertEquals(25L, rows[0].mobile)
    }

    @Test fun labelsForSpecialUidsAndSharedUids() {
        assertEquals("Удалённые приложения", AppTraffic.labelFor(-4, emptyList())); assertEquals("Раздача интернета (точка доступа)", AppTraffic.labelFor(-5, emptyList()))
        assertEquals("Система Android", AppTraffic.labelFor(1000, listOf("x"))); assertEquals("Система (root)", AppTraffic.labelFor(0, emptyList()))
        assertEquals("Неизвестное приложение (uid 10234)", AppTraffic.labelFor(10234, emptyList()))
        assertEquals("Telegram", AppTraffic.labelFor(10100, listOf("Telegram"))); assertEquals("Telegram +2", AppTraffic.labelFor(10100, listOf("Telegram", "A", "B")))
    }
}
