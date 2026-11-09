package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class SpeedAllTest {
    private fun ok(mbps: Double) = SpeedResult((mbps * 1_000_000 / 8).toLong(), 1000, 50, "")   // за 1 секунду
    private val bad = SpeedResult(0, 0, 0, "таймаут")

    @Test fun serversAreRankedFastestFirstAndFailuresDropOut() {
        val r = SpeedTest.rank(mapOf("a" to ok(20.0), "b" to ok(80.0), "c" to bad, "d" to ok(45.0)))
        assertEquals(listOf("b", "d", "a"), r.map { it.first })
    }

    @Test fun fastestIsNullWithoutAnyGoodResult() {
        assertNull(SpeedTest.fastestId(emptyMap())); assertNull(SpeedTest.fastestId(mapOf("x" to bad)))
        assertEquals("b", SpeedTest.fastestId(mapOf("a" to ok(1.0), "b" to ok(2.0), "c" to bad)))
    }

    @Test fun perServerLimitIsSmallAndExpressedInMegabytes() {
        assertEquals(3L * 1024 * 1024, SpeedTest.ALL_MAX_BYTES); assertTrue(SpeedTest.ALL_MAX_BYTES < SpeedTest.MAX_BYTES); assertEquals(6, SpeedTest.ALL_SECONDS)
    }
}
