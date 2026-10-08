package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class AppLockTest {
    @Test fun locksOnlyWhenEnabledAndAwayLongerThanGrace() {
        val t0 = 1_000_000L
        assertFalse(AppLock.expired(false, t0, t0 + 10 * 60_000L))
        assertFalse("короткий выход (системное окно запроса VPN, выбор картинки) не запирает", AppLock.expired(true, t0, t0 + 5_000L))
        assertFalse(AppLock.expired(true, t0, t0 + AppLock.GRACE_SEC * 1000L))
        assertTrue(AppLock.expired(true, t0, t0 + AppLock.GRACE_SEC * 1000L + 1))
    }
}
