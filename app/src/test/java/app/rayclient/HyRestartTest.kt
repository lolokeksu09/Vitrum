package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class HyRestartTest {
    @Test fun dueOnlyWhenEnabledOnHysteriaAndTimeIsUp() {
        val five = 5 * 60_000L
        assertTrue(HyRestart.due(true, true, 5, five)); assertTrue(HyRestart.due(true, true, 5, five + 1))
        assertFalse(HyRestart.due(true, true, 5, five - 1))
        assertFalse(HyRestart.due(false, true, 5, five * 10)); assertFalse(HyRestart.due(true, false, 5, five * 10))
    }

    @Test fun minutesAreClamped() {
        assertEquals(1, HyRestart.clamp(0)); assertEquals(1, HyRestart.clamp(-7)); assertEquals(60, HyRestart.clamp(1000)); assertEquals(5, HyRestart.clamp(5))
        assertTrue(HyRestart.due(true, true, 0, 60_000L)); assertFalse(HyRestart.due(true, true, 0, 59_999L))
    }

    @Test fun hysteriaIsFoundInLinksAndInAutoConfigs() {
        assertTrue(HyRestart.usesHysteria("hysteria2", null)); assertFalse(HyRestart.usesHysteria("vless", null))
        assertTrue(HyRestart.usesHysteria("auto", """{"outbounds":[{"streamSettings":{"network":"hysteria","security":"tls"}}]}"""))
        assertTrue(HyRestart.usesHysteria("auto", "{\n \"streamSettings\": {\n  \"network\": \"hysteria\"\n }\n}"))
        assertFalse(HyRestart.usesHysteria("auto", """{"outbounds":[{"streamSettings":{"network":"tcp"}}],"tag":"hysteria-note"}"""))
    }

    @Test fun parsedHysteria2LinkReportsItsProtocol() {
        assertEquals("hysteria2", Links.parse("hysteria2://pw@example.com:443?sni=example.com#hy").proto)
    }
}
