package app.rayclient

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrafficMobileTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test fun mobileBytesAreCountedSeparatelyAndTotalsStayTheSame() {
        TrafficLog.clear()
        TrafficLog.add("2026-10-07", 100, 10, mobile = true); TrafficLog.add("2026-10-07", 50, 5)
        TrafficLog.add("2026-10-03", 1000, 100, mobile = true); TrafficLog.add("2026-09-30", 7000, 700, mobile = true)
        assertEquals(110L, TrafficLog.mobileLastDays(1, today)); assertEquals(1210L, TrafficLog.mobileLastDays(7, today)); assertEquals(1210L, TrafficLog.mobileMonth(today))
        assertEquals(1150L to 115L, TrafficLog.month(today)); assertEquals(150L to 15L, TrafficLog.lastDays(1, today))
    }

    @Test fun jsonKeepsMobileBytesAndOldJsonStillLoads() {
        TrafficLog.clear(); TrafficLog.add("2026-10-07", 100, 10, mobile = true)
        val json = TrafficLog.toJson(today); assertTrue(json.contains("[100,10,110]"))
        TrafficLog.clear(); TrafficLog.fromJson(json); assertEquals(110L, TrafficLog.mobileLastDays(1, today))
        TrafficLog.clear(); TrafficLog.fromJson("""{"2026-10-07":[100,10]}""")   // запись версии без разбивки
        assertEquals(100L to 10L, TrafficLog.lastDays(1, today)); assertEquals(0L, TrafficLog.mobileLastDays(1, today))
        TrafficLog.add("2026-10-07", 5, 5, mobile = true); assertEquals(10L, TrafficLog.mobileLastDays(1, today))
    }

    @Test fun networkKindIsMobileOnlyWithoutWifiOrEthernet() {
        assertTrue(TrafficLog.mobileFrom(wifiOrEthernet = false, cellular = true))
        assertFalse(TrafficLog.mobileFrom(wifiOrEthernet = true, cellular = true)); assertFalse(TrafficLog.mobileFrom(true, false)); assertFalse(TrafficLog.mobileFrom(false, false))
    }

    @Test fun limitUsesMobileBytesOnlyWhenAsked() {
        val gb = 1024L * 1024 * 1024
        assertEquals(8 * gb, TrafficLimit.used(8 * gb, gb, false)); assertEquals(gb, TrafficLimit.used(8 * gb, gb, true))
        assertEquals(0, TrafficLimit.level(TrafficLimit.used(8 * gb, gb, true), 10)); assertEquals(80, TrafficLimit.level(TrafficLimit.used(8 * gb, gb, false), 10))
    }
}
