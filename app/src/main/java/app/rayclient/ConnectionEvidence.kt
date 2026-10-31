package app.rayclient

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

/** A manual HTTP response over the Xray Unix socket. NOT DNS/IPv6/leak validation. */
enum class ProbeOutcome { CHECKING, REACHABLE, NO_REPLY }

data class EvidenceSnapshot(
    val outcome: ProbeOutcome,
    val elapsedMs: Int,
    val atMs: Long,
    val serverId: String?,
    val session: Long,
    val token: Long
)

object EvidencePolicy {
    private const val MAX_AGE_MS = 60_000L

    /** Reject stale probes after any change of server, session, network or service generation. */
    fun isCurrent(
        proof: EvidenceSnapshot?,
        connected: Boolean,
        session: Long,
        serverId: String?,
        token: Long,
        now: Long
    ): Boolean = proof != null &&
        connected && session > 0 &&
        proof.session == session && proof.serverId == serverId &&
        proof.token == token &&
        now >= proof.atMs && now - proof.atMs <= MAX_AGE_MS
}

/**
 * A read-only, user-triggered check: no background or automatic HTTP requests.
 * The only outbound request goes via the existing Xray Unix-socket connector.
 */
object ConnectionEvidence {
    private val generation = AtomicLong()
    private val main = Handler(Looper.getMainLooper())

    var snapshot by mutableStateOf<EvidenceSnapshot?>(null)
        private set

    val token: Long get() = generation.get()

    fun invalidate() {
        val next = generation.incrementAndGet()
        main.post {
            if (generation.get() == next) snapshot = null
        }
    }

    fun check() {
        val session = RayVpnService.since
        val server = AppState.selectedId
        val path = Pinger.probePath
        if (!RayVpnService.connected || RayVpnService.busy || RayVpnService.blocked ||
            session <= 0L || path.isBlank()) return
        if (EvidencePolicy.isCurrent(snapshot, true, session, server, generation.get(),
                System.currentTimeMillis()) && snapshot?.outcome == ProbeOutcome.CHECKING) return

        val id = generation.incrementAndGet()
        snapshot = EvidenceSnapshot(ProbeOutcome.CHECKING, -1, System.currentTimeMillis(), server, session, id)
        Thread({
            val result = runCatching {
                HeadProbe.head("http://cp.cloudflare.com/generate_204", Pinger.connector(path), 5000, false)
            }.getOrNull()
            val outcome = if (result != null && result.ms >= 0) ProbeOutcome.REACHABLE
                          else ProbeOutcome.NO_REPLY
            main.post {
                if (generation.get() == id && RayVpnService.connected &&
                    RayVpnService.since == session && AppState.selectedId == server &&
                    Pinger.probePath == path) {
                    snapshot = EvidenceSnapshot(outcome, result?.ms ?: -1,
                        System.currentTimeMillis(), server, session, id)
                }
            }
        }, "VitrumManualProof").start()
    }
}
