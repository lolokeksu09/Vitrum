package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class VpnEventHistoryTest {
    @Test fun keepsOnlyLastFortyEventsInChronologicalOrder() {
        var history = emptyList<VpnEvent>()
        repeat(45) { n ->
            history = VpnEventPolicy.append(history, if (n % 2 == 0) VpnEventType.START_REQUEST else VpnEventType.CONNECTED, n * 4000L)
        }
        assertEquals(40, history.size)
        assertEquals(20_000L, history.first().atMs)
        assertEquals(176_000L, history.last().atMs)
    }

    @Test fun coalescesRepeatedEventsOnlyWithinTwoSeconds() {
        val first = VpnEventPolicy.append(emptyList(), VpnEventType.NETWORK_LOST, 10000L)
        val second = VpnEventPolicy.append(first, VpnEventType.NETWORK_LOST, 12000L)
        assertEquals(1, second.size)
        assertEquals(2, second[0].count)
        val changed = VpnEventPolicy.append(second, VpnEventType.NETWORK_AVAILABLE, 12010L)
        assertEquals(2, changed.size)
        val late = VpnEventPolicy.append(changed, VpnEventType.NETWORK_LOST, 18000L)
        assertEquals(3, late.size)
        val reversed = VpnEventPolicy.append(late, VpnEventType.NETWORK_LOST, 17000L)
        assertEquals(4, reversed.size)
    }

    @Test fun countersRepresentEventsNotNetworkTraffic() {
        var history = emptyList<VpnEvent>()
        for (t in listOf(VpnEventType.START_REQUEST, VpnEventType.CONNECTED,
                VpnEventType.SWITCHED, VpnEventType.SWITCH_RESTORED,
                VpnEventType.FAIL_CLOSED, VpnEventType.NETWORK_LOST)) {
            history = VpnEventPolicy.append(history, t, history.size * 3000L)
        }
        val summary = VpnEventPolicy.summary(history)
        assertEquals(1, summary.switches)
        assertEquals(3, summary.problems)
        assertEquals(1, summary.networkLosses)
    }

    @Test fun reportContainsOnlyFiniteKnownEventLabels() {
        val history = listOf(
            VpnEvent(VpnEventType.SWITCH_REQUEST, 1700000000000L, 2),
            VpnEvent(VpnEventType.SWITCHED, 1700000004000L)
        )
        val report = VpnEventPolicy.report(history)
        assertTrue(report.contains("Запрос смены сервера"))
        assertTrue(report.contains("×2"))
        assertFalse(report.contains("vless://"))
        assertFalse(report.contains("http://"))
        assertFalse(report.contains("server-a"))
        assertFalse(report.contains("app.rayclient.selectedId"))
    }
}
