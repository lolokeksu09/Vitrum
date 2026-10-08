package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class ConnWizardTest {
    private val now = ConnWizard.MIN_PLAUSIBLE + 1000L * 3600 * 24 * 280   // осень 2026

    @Test fun networkStepNamesNoInternetAndCaptivePortal() {
        assertEquals(WState.FAIL, ConnWizard.networkStep(false, false, false).state)
        assertTrue(ConnWizard.networkStep(true, false, true).detail.contains("войти"))
        assertEquals(WState.FAIL, ConnWizard.networkStep(true, false, true).state)
        assertEquals(WState.WARN, ConnWizard.networkStep(true, false, false).state); assertEquals(WState.OK, ConnWizard.networkStep(true, true, false).state)
    }

    @Test fun clockStepCatchesOldDatesAndManualTime() {
        assertEquals(WState.FAIL, ConnWizard.clockStep(true, 1_500_000_000_000L).state)
        assertEquals(WState.WARN, ConnWizard.clockStep(false, now).state); assertEquals(WState.OK, ConnWizard.clockStep(true, now).state); assertEquals(WState.OK, ConnWizard.clockStep(null, now).state)
    }

    @Test fun serverPortAndAddressSteps() {
        assertEquals(WState.FAIL, ConnWizard.serverStep(false).state); assertEquals(WState.OK, ConnWizard.serverStep(true).state)
        assertEquals(WState.FAIL, ConnWizard.dnsStep(false, 0).state); assertEquals(WState.OK, ConnWizard.dnsStep(true, 12).state)
        assertEquals(WState.FAIL, ConnWizard.tcpStep(false, 0, false).state); assertEquals(WState.OK, ConnWizard.tcpStep(true, 30, false).state)
        assertEquals("Hysteria2 по UDP: TCP не проверяется, это предупреждение, а не отказ", WState.WARN, ConnWizard.tcpStep(false, 0, true).state)
        assertNotNull(ConnWizard.blockedStep(true)); assertNull(ConnWizard.blockedStep(false))
        assertEquals(WState.WARN, ConnWizard.permissionStep(false).state); assertEquals(WState.WARN, ConnWizard.backgroundStep(false).state); assertEquals(WState.OK, ConnWizard.backgroundStep(true).state)
    }

    @Test fun verdictNamesTheFirstFailureThenWarnings() {
        val steps = listOf(ConnWizard.networkStep(true, true, false), ConnWizard.permissionStep(false), ConnWizard.dnsStep(false, 0), ConnWizard.tcpStep(false, 0, false))
        assertTrue(ConnWizard.verdict(steps).startsWith("Причина: адрес сервера"))
        val warnOnly = listOf(ConnWizard.networkStep(true, true, false), ConnWizard.backgroundStep(false))
        assertTrue(ConnWizard.verdict(warnOnly).startsWith("Явной причины не найдено")); assertTrue(ConnWizard.verdict(warnOnly).contains("экономия заряда"))
        val allOk = listOf(ConnWizard.networkStep(true, true, false), ConnWizard.permissionStep(true), ConnWizard.serverStep(true), ConnWizard.tcpStep(true, 20, false))
        assertTrue(ConnWizard.verdict(allOk).startsWith("Сеть, разрешение"))
    }

    @Test fun formatListsEveryStepAndTheVerdict() {
        val t = ConnWizard.format(listOf(ConnWizard.networkStep(false, false, false), ConnWizard.serverStep(true)))
        assertTrue(t.lines()[0].startsWith("✗ Сеть: ")); assertTrue(t.lines()[1].startsWith("✓ Сервер: ")); assertTrue(t.contains("Причина: сеть"))
    }
}
