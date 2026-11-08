package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class HomeStatusUiTest {
    @Test fun disconnectedStateHasOneConnectAction() {
        val state = HomeStatusPolicy.from(connected = false, busy = false,
            blocked = false, error = false, selecting = false)
        assertEquals("Не подключено", state.title)
        assertEquals("Подключиться", state.action)
        assertEquals("VPN выключен", state.subtitle)
    }

    @Test fun connectingStateIsNotReportedAsProtected() {
        val state = HomeStatusPolicy.from(false, true, false, false, false)
        assertEquals("Подключение…", state.title)
        assertEquals("Подключаюсь…", state.action)
        assertFalse(state.subtitle.contains("защищ"))
    }

    @Test fun blockedTunnelHasPriorityOverConnected() {
        val state = HomeStatusPolicy.from(true, false, true, false, false)
        assertEquals("Трафик заблокирован", state.title)
        assertEquals("Отключиться", state.action)
        assertTrue(state.subtitle.contains("заблок"))
    }

    @Test fun connectedStateDescribesServiceNotLeakFreedom() {
        val state = HomeStatusPolicy.from(true, false, false, false, false)
        assertEquals("VPN запущен", state.title)
        assertEquals("Отключиться", state.action)
        assertTrue(state.subtitle.contains("отдельно"))
        assertFalse(state.subtitle.contains("защищ"))
    }

    @Test fun quickSelectionAndFailureStayExplicit() {
        assertEquals("Подбираю сервер…", HomeStatusPolicy.from(
            false, false, false, false, true).title)
        assertEquals("Ошибка", HomeStatusPolicy.from(
            false, false, false, true, false).title)
    }
    @Test fun switchingStateIsNotReportedAsAStableConnection() {
        val state = HomeStatusPolicy.from(
            connected = true,
            busy = false,
            blocked = false,
            error = false,
            selecting = false,
            switching = true
        )
        assertEquals("Переключение сервера…", state.title)
        assertEquals("Отключиться", state.action)
        assertTrue(state.subtitle.contains("новый сервер"))
        assertTrue(state.subtitle.contains("восстановить"))
    }

    @Test fun blockedStateTakesPriorityOverSwitching() {
        val state = HomeStatusPolicy.from(
            connected = false,
            busy = false,
            blocked = true,
            error = false,
            selecting = false,
            switching = true
        )
        assertEquals("Трафик заблокирован", state.title)
    }

}
