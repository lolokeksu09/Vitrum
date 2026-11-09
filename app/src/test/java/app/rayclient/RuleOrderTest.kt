package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class RuleOrderTest {
    private val a = Rule(0, "a.example", 0); private val b = Rule(1, "10.0.0.0/8", 1, enabled = false); private val c = Rule(2, "443", 2)
    private fun names(l: List<Rule>) = l.map { it.value }

    @Test fun movesUpAndDown() {
        assertEquals(listOf("10.0.0.0/8", "a.example", "443"), names(RuleOrder.move(listOf(a, b, c), 1, -1)!!))
        assertEquals(listOf("a.example", "443", "10.0.0.0/8"), names(RuleOrder.move(listOf(a, b, c), 1, 1)!!))
    }

    @Test fun edgesAndBadArgumentsGiveNull() {
        assertNull(RuleOrder.move(listOf(a, b, c), 0, -1)); assertNull(RuleOrder.move(listOf(a, b, c), 2, 1))
        assertNull(RuleOrder.move(emptyList(), 0, 1)); assertNull(RuleOrder.move(listOf(a, b), 5, -1)); assertNull(RuleOrder.move(listOf(a, b), 0, 2)); assertNull(RuleOrder.move(listOf(a, b), 0, 0))
    }

    @Test fun rulesKeepTheirFieldsAndTheSourceListIsNotChanged() {
        val src = listOf(a, b, c); val out = RuleOrder.move(src, 1, 1)!!
        assertEquals(listOf(a, b, c).map { it.value }, names(src))
        val moved = out.first { it.value == "10.0.0.0/8" }
        assertEquals(1, moved.kind); assertEquals(1, moved.action); assertFalse(moved.enabled)
        assertEquals(3, out.size)
    }
}
