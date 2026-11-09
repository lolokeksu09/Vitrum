package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class IntegrityTest {
    @Test fun fingerprintFormatAndMatch() {
        assertEquals("2C:6A:FE:BF", Integrity.fmt("2c6afebf")); assertEquals(95, Integrity.fmt(Integrity.RELEASE_CERT).length)
        assertTrue(Integrity.matches(Integrity.RELEASE_CERT)); assertTrue(Integrity.matches(Integrity.RELEASE_CERT.uppercase()))
        assertFalse(Integrity.matches("00" + Integrity.RELEASE_CERT.drop(2))); assertFalse(Integrity.matches(null)); assertFalse(Integrity.matches(""))
    }

    @Test fun shieldFlagsAForeignSignatureButNotAMissingOne() {
        fun sig(signer: String?) = Shield.evaluate(ShieldInput(true, true, false, emptyList(), false, null, false, "app.rayclient", null, 0, null, null, null, true, null, 1, 0, 0L, signer)).firstOrNull { it.key == "signature" }
        assertEquals(St.OK, sig(Integrity.RELEASE_CERT)!!.st)
        assertEquals("чужая или отладочная подпись — предупреждение", St.WARN, sig("ab".repeat(32))!!.st)
        assertNull("если подпись получить не удалось, пункта нет (не ругаемся без причины)", sig(null))
        assertEquals("пункт не влияет на оценку", 0, sig(Integrity.RELEASE_CERT)!!.weight)
    }
}
