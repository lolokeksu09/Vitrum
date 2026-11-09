package app.rayclient

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoIsolationTest {
    @Test fun normalLaunchAllowsPersistence() {
        val previous = Demo.active
        try {
            Demo.active = false
            assertTrue(Demo.allowsPersistence)
        } finally {
            Demo.active = previous
        }
    }

    @Test fun demoLaunchDisablesAppStatePersistence() {
        val previous = Demo.active
        try {
            Demo.active = true
            assertFalse(Demo.allowsPersistence)
        } finally {
            Demo.active = previous
        }
    }
}
