package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class AccessTest {
    @Test fun fontScaleIsCappedOnlyAboveTheLimit() {
        assertEquals(1.0f, Access.cappedScale(1.0f, 1.3f), 0f)
        assertEquals(1.3f, Access.cappedScale(2.0f, 1.3f), 0f)
        assertEquals("меньше единицы не поднимаем", 0.85f, Access.cappedScale(0.85f, 1.3f), 0f)
    }
}
