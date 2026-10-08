package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class VpnScheduleTest {
    private fun m(h: Int, mi: Int = 0) = h * 60 + mi

    @Test fun timeIsParsedAndFormatted() {
        assertEquals(m(7, 5), VpnSchedule.parse("7:05")); assertEquals(m(23, 30), VpnSchedule.parse(" 23:30 ")); assertEquals(0, VpnSchedule.parse("00:00")); assertEquals(m(23, 59), VpnSchedule.parse("23:59"))
        listOf("", "24:00", "7:5", "12:60", "abc", "7-05", "7:05:00", "-1:00").forEach { assertNull(it, VpnSchedule.parse(it)) }
        assertEquals("07:05", VpnSchedule.format(m(7, 5))); assertEquals("23:00", VpnSchedule.format(m(23)))
    }

    @Test fun windowAcrossMidnightAndWithinADay() {
        assertTrue(VpnSchedule.inWindow(m(23), m(23), m(7))); assertTrue(VpnSchedule.inWindow(m(3), m(23), m(7))); assertTrue(VpnSchedule.inWindow(0, m(23), m(7)))
        assertFalse(VpnSchedule.inWindow(m(7), m(23), m(7))); assertFalse(VpnSchedule.inWindow(m(12), m(23), m(7))); assertFalse(VpnSchedule.inWindow(m(22, 59), m(23), m(7)))
        assertTrue(VpnSchedule.inWindow(m(13), m(13), m(15))); assertFalse(VpnSchedule.inWindow(m(15), m(13), m(15))); assertFalse(VpnSchedule.inWindow(m(12), m(13), m(15)))
        assertFalse("окна нет", VpnSchedule.inWindow(m(5), m(5), m(5)))
    }

    @Test fun desiredStateIsStopInsideAndStartOutside() {
        assertEquals(2, VpnSchedule.desired(m(2), m(23), m(7))); assertEquals(1, VpnSchedule.desired(m(9), m(23), m(7)))
    }

    @Test fun nextBoundaryIsTheNearestOne() {
        assertEquals(60 * 60, VpnSchedule.secondsToNext(m(22) * 60, m(23), m(7)))              // 22:00 → 23:00
        assertEquals(4 * 3600, VpnSchedule.secondsToNext(m(3) * 60, m(23), m(7)))              // 03:00 → 07:00
        assertEquals("в 07:00:01 до 23:00 без секунды", 16 * 3600 - 1, VpnSchedule.secondsToNext(m(7) * 60 + 1, m(23), m(7)))
        assertEquals("ровно на границе берётся следующая", 16 * 3600, VpnSchedule.secondsToNext(m(7) * 60, m(23), m(7)))
        assertNull(VpnSchedule.secondsToNext(1000, m(7), m(7)))
    }

    @Test fun planStopsOrStartsOnlyWhenNeeded() {
        fun p(v: Int, connected: Boolean, selected: Boolean = true, permitted: Boolean = true) =
            Scenarios.plan(Scen(vpn = v), "main", emptySet(), false, connected, false, false, selected, permitted).vpn
        assertEquals(Scenarios.VpnStep.STOP, p(2, true)); assertEquals(Scenarios.VpnStep.NONE, p(2, false))
        assertEquals(Scenarios.VpnStep.START, p(1, false)); assertEquals(Scenarios.VpnStep.NONE, p(1, true))
        assertEquals(Scenarios.VpnStep.NONE, p(1, false, selected = false)); assertEquals(Scenarios.VpnStep.NEED_PERMISSION, p(1, false, permitted = false))
    }
}
