package app.rayclient

import org.junit.Assert.assertEquals
import org.junit.Test

class XrayCoreManagerTest {
    @Test fun semanticVersionComponentsSortNumerically() {
        assertEquals(1, XrayCoreManager.compareVersionParts(listOf(26, 10, 31), listOf(26, 3, 27)).compareTo(0))
        assertEquals(-1, XrayCoreManager.compareVersionParts(listOf(26, 3, 27), listOf(26, 10, 31)).compareTo(0))
    }

    @Test fun missingTrailingComponentsAreTreatedAsZero() {
        assertEquals(0, XrayCoreManager.compareVersionParts(listOf(26, 10), listOf(26, 10, 0)))
    }

    @Test fun higherPatchVersionWinsWithinSameMinorVersion() {
        assertEquals(1, XrayCoreManager.compareVersionParts(listOf(26, 10, 31), listOf(26, 10, 21)).compareTo(0))
    }
}
