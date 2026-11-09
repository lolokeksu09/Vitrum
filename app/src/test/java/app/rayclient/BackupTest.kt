package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val values = mapOf<String, Any?>("servers" to """[{"id":"a","name":"A","link":"vless://x@example.com:443"}]""", "subs" to "[]",
        "killSwitch" to true, "theme" to 2, "lastUpd" to 1_700_000_000_000L, "lang" to "ru", "ratio" to 0.5f, "skip" to setOf("x"))

    @Test fun roundTripKeepsValuesAndTypes() {
        val file = Backup.seal(values, "correct horse")
        val back = Backup.open(file, "correct horse")!!
        assertEquals(values["servers"], back["servers"]); assertEquals(true, back["killSwitch"]); assertEquals(2, back["theme"])
        assertEquals(1_700_000_000_000L, back["lastUpd"]); assertEquals("ru", back["lang"]); assertEquals(0.5f, back["ratio"])
        assertFalse("значения неподдерживаемого типа не попадают в копию", back.containsKey("skip"))
    }

    @Test fun fileDoesNotContainPlainData() {
        val file = Backup.seal(values, "correct horse")
        assertFalse(file.contains("example.com")); assertFalse(file.contains("vless")); assertFalse(file.contains("correct horse"))
    }

    @Test fun wrongPasswordAndTamperingGiveNull() {
        val file = Backup.seal(values, "correct horse")
        assertNull(Backup.open(file, "wrong password"))
        val o = org.json.JSONObject(file); val ct = o.getString("ct")
        o.put("ct", (if (ct.first() == 'A') "B" else "A") + ct.drop(1))
        assertNull("изменённый файл не открывается", Backup.open(o.toString(), "correct horse"))
    }

    @Test fun sameDataGivesDifferentFiles() {
        assertNotEquals(Backup.seal(values, "correct horse"), Backup.seal(values, "correct horse"))
    }

    @Test fun foreignFilesAreRejectedWithMessage() {
        for (bad in listOf("", "not json", "{}", """{"magic":"other"}""")) {
            try { Backup.open(bad, "x"); fail("принят: $bad") } catch (e: BackupFormatException) { assertTrue(e.message!!.isNotEmpty()) }
        }
        val o = org.json.JSONObject(Backup.seal(values, "correct horse")).put("it", 5)
        try { Backup.open(o.toString(), "correct horse"); fail("число итераций вне границ") } catch (e: BackupFormatException) { }
        val v2 = org.json.JSONObject(Backup.seal(values, "correct horse")).put("v", 2)
        try { Backup.open(v2.toString(), "correct horse"); fail("версия 2") } catch (e: BackupFormatException) { }
    }

    @Test fun countsServersAndSubs() {
        assertEquals(1 to 0, Backup.counts(Backup.open(Backup.seal(values, "correct horse"), "correct horse")!!))
        assertEquals(0 to 0, Backup.counts(emptyMap()))
    }
}
