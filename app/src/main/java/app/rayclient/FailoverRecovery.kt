package app.rayclient

internal enum class RecoveryOutcome { SWITCHED, RESTORED, BLOCKED, CANCELLED }
internal data class RecoveryResult<T>(val outcome: RecoveryOutcome, val server: T? = null)

/** Transaction ordering shared by the service and JVM tests; never commits selection itself. */
internal object FailoverRecovery {
    fun <T> run(
        previous: T,
        candidates: List<T>,
        current: () -> Boolean,
        attempt: (T) -> Boolean
    ): RecoveryResult<T> {
        for (candidate in candidates) {
            if (!current()) return RecoveryResult(RecoveryOutcome.CANCELLED)
            val replied = attempt(candidate)
            if (!current()) return RecoveryResult(RecoveryOutcome.CANCELLED)
            if (replied) return RecoveryResult(RecoveryOutcome.SWITCHED, candidate)
        }
        if (!current()) return RecoveryResult(RecoveryOutcome.CANCELLED)
        val restored = attempt(previous)
        if (!current()) return RecoveryResult(RecoveryOutcome.CANCELLED)
        return if (restored) RecoveryResult(RecoveryOutcome.RESTORED, previous)
            else RecoveryResult(RecoveryOutcome.BLOCKED)
    }
}
