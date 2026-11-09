package app.rayclient

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoStoreStateTest {
    private fun ref(key: String) = AutoRef(key, key,
        AutoPlan(key, listOf(JSONObject().put("tag", "proxy").put("protocol", "vless")),
            listOf(JSONObject().put("tag", "bal").put("selector", JSONArray().put("proxy"))), null))

    @Test fun failedSaveKeepsPreviousSnapshot() {
        val s = AutoStoreState()
        assertTrue(s.replace("one", listOf(ref("old"))) { _, _ -> true })
        assertFalse(s.replace("one", listOf(ref("new"))) { _, _ -> false })
        assertEquals(setOf("old"), s.plans.keys)
        assertEquals("one", s.subOf["old"])
        assertFalse(s.plans.containsKey("new"))
    }

    @Test fun successPublishesOnlyAfterPersistence() {
        val s = AutoStoreState()
        assertTrue(s.replace("one", listOf(ref("old"))) { _, _ -> true })
        assertTrue(s.replace("two", listOf(ref("other"))) { _, _ -> true })
        assertTrue(s.replace("one", listOf(ref("new"))) { plans, owners ->
            assertEquals(setOf("new", "other"), plans.keys)
            assertEquals("two", owners["other"])
            assertTrue("original memory should remain while writing", s.plans.containsKey("old"))
            true
        })
        assertEquals(setOf("new", "other"), s.plans.keys)
        assertEquals("two", s.subOf["other"])
    }

    @Test fun failedDeletionKeepsExistingServer() {
        val s = AutoStoreState()
        assertTrue(s.replace("one", listOf(ref("old"))) { _, _ -> true })
        assertFalse(s.replace("one", emptyList()) { _, _ -> false })
        assertEquals(setOf("old"), s.plans.keys)
    }
}
