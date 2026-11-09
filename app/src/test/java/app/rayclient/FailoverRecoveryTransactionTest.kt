package app.rayclient

import org.junit.Assert.*
import org.junit.Test

class FailoverRecoveryTransactionTest {
    @Test fun failedSpawnDoesNotSkipTheNextCandidate() {
        val attempts = mutableListOf<String>()
        val result = FailoverRecovery.run("old", listOf("spawn-fails", "working", "unused"), { true }) {
            attempts += it
            it == "working"
        }
        assertEquals(listOf("spawn-fails", "working"), attempts)
        assertEquals(RecoveryResult(RecoveryOutcome.SWITCHED, "working"), result)
    }

    @Test fun allFailedCandidatesRestoreThePreviousRoute() {
        val attempts = mutableListOf<String>()
        val result = FailoverRecovery.run("old", listOf("bad-config", "no-http-reply"), { true }) {
            attempts += it
            it == "old"
        }
        assertEquals(listOf("bad-config", "no-http-reply", "old"), attempts)
        assertEquals(RecoveryResult(RecoveryOutcome.RESTORED, "old"), result)
    }

    @Test fun failedRollbackRequiresBlockingInsteadOfChoosingAnUnverifiedServer() {
        val attempts = mutableListOf<String>()
        val result = FailoverRecovery.run("old", listOf("bad"), { true }) {
            attempts += it
            false
        }
        assertEquals(listOf("bad", "old"), attempts)
        assertEquals(RecoveryOutcome.BLOCKED, result.outcome)
        assertNull(result.server)
    }

    @Test fun stoppedSessionDoesNotStartAnyCandidateOrRollback() {
        val result = FailoverRecovery.run("old", listOf("new"), { false }) {
            fail("Cancelled session must not modify Xray")
            false
        }
        assertEquals(RecoveryOutcome.CANCELLED, result.outcome)
    }

    @Test fun stopOrNewStartDuringAnyAttemptDiscardsBothSuccessAndFailure() {
        for (cancelAt in listOf("first", "second", "old")) {
            for (reply in listOf(false, true)) {
                var generation = 1
                val attempts = mutableListOf<String>()
                val result = FailoverRecovery.run("old", listOf("first", "second"), {
                    FailoverEpoch.canModify(1, generation, true)
                }) {
                    attempts += it
                    if (it == cancelAt) { generation++; reply } else false
                }
                assertEquals(RecoveryOutcome.CANCELLED, result.outcome)
                assertNull(result.server)
                assertEquals(cancelAt, attempts.last())
            }
        }
    }

    @Test fun closedTunCancelsRecoveryEvenIfHttpSucceeded() {
        var tunOpen = true
        val result = FailoverRecovery.run("old", listOf("new"), {
            FailoverEpoch.canModify(3, 3, tunOpen)
        }) { tunOpen = false; true }
        assertEquals(RecoveryOutcome.CANCELLED, result.outcome)
        assertNull(result.server)
    }
}
