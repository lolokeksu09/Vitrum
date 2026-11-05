package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class HomeStatusUiTest {
    @Test fun disconnectedStateHasOneConnectAction() {
        val state = HomeStatusUi.from(connected = false, busy = false,
            blocked = false, error = false, selecting = false)
        assertEquals("Не подключено", state.title)
        assertEquals("Подключиться", state.action)
        assertEquals("VPN выключен", state.subtitle)
    }

    @Test fun connectingStateIsNotReportedAsProtected() {
        val state = HomeStatusUi.from(false, true, false, false, false)
        assertEquals("Подключение…", state.title)
        assertEquals("Подключаюсь…", state.action)
        assertFalse(state.subtitle.contains("защищ"))
    }

    @Test fun blockedTunnelHasPriorityOverConnected() {
        val state = HomeStatusUi.from(true, false, true, false, false)
        assertEquals("Трафик заблокирован", state.title)
        assertEquals("Отключиться", state.action)
        assertTrue(state.subtitle.contains("заблок"))
    }

    @Test fun connectedStateDescribesServiceNotLeakFreedom() {
        val state = HomeStatusUi.from(true, false, false, false, false)
        assertEquals("VPN запущен", state.title)
        assertEquals("Отключиться", state.action)
        assertFalse(state.subtitle.contains("утеч"))
        assertFalse(state.subtitle.contains("защищ"))
    }

    @Test fun quickSelectionAndFailureStayExplicit() {
        assertEquals("Подбираю сервер…", HomeStatusUi.from(
            false, false, false, false, true).title)
        assertEquals("Ошибка", HomeStatusUi.from(
            false, false, false, true, false).title)
    }
}
