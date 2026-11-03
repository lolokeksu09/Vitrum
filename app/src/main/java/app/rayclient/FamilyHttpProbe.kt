package app.rayclient

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

/** HTTP reachability through the existing private Xray Unix socket; never a DNS leak test. */
enum class IpFamily { V4, V6 }
enum class FamilyHttpStatus { REACHABLE, UNKNOWN }
data class FamilyHttpResult(val status: FamilyHttpStatus, val ms: Int)
data class FamilyHttpSnapshot(
    val ipv4: FamilyHttpResult?,
    val ipv6: FamilyHttpResult?,
    val atMs: Long,
    val session: Long,
    val serverId: String?,
    val probePath: String,
    val evidenceToken: Long,
    val requestId: Long,
    val running: Boolean
)

/** Pure logic: do not turn timeouts, HTTP errors or a wrong-family response into proof. */
object FamilyHttpPolicy {
    const val MAX_AGE_MS = 60_000L
    val dnsLeak = FamilyHttpStatus.UNKNOWN

    fun classify(reply: HeadResult?, family: IpFamily): FamilyHttpResult {
        val body = reply?.body?.trim().orEmpty()
        val correct = reply != null && reply.ms >= 0 && body.length in 7..45 &&
            when (family) {
                IpFamily.V4 -> RuleCheck.isIpv4(body)
                IpFamily.V6 -> RuleCheck.isIpv6(body)
            }
        return FamilyHttpResult(if (correct) FamilyHttpStatus.REACHABLE else FamilyHttpStatus.UNKNOWN,
            if (correct) reply!!.ms else -1)
    }

    fun isCurrent(
        proof: FamilyHttpSnapshot?,
        connected: Boolean, busy: Boolean, blocked: Boolean,
        session: Long, serverId: String?, path: String,
        token: Long, requestId: Long, now: Long
    ): Boolean = proof != null && connected && !busy && !blocked &&
        session > 0L && path.isNotBlank() &&
        proof.session == session && proof.serverId == serverId &&
        proof.probePath == path && proof.evidenceToken == token &&
        proof.requestId == requestId &&
        now >= proof.atMs && now - proof.atMs <= MAX_AGE_MS
}

/**
 * Runs only on explicit user action. Each request uses the already-running Xray HTTP
 * Unix socket, never Android's own DNS resolver or an unprotected direct socket.
 * Both IP values are validated in memory and immediately discarded; no file/log/telemetry.
 *
 * api4/api6.ipify.org are IPv4-only / IPv6-only public-IP services, respectively.
 * HTTP is carried inside Xray but is plaintext after the proxy exit. Do not interpret
 * these responses as proof of DNS isolation, TUN routing or absence of IP leaks.
 */
object FamilyHttpProbe {
    private val nextRequest = AtomicLong()
    private val main = Handler(Looper.getMainLooper())
    var snapshot by mutableStateOf<FamilyHttpSnapshot?>(null)
        private set

    fun check() {
        val session = RayVpnService.since
        val server = AppState.selectedId
        val path = Pinger.probePath
        if (!RayVpnService.connected || RayVpnService.busy || RayVpnService.blocked ||
            session <= 0L || path.isBlank()) return
        val token = ConnectionEvidence.token
        val active = snapshot
        if (active?.running == true && FamilyHttpPolicy.isCurrent(
                active, true, false, false, session, server, path, token, nextRequest.get(),
                System.currentTimeMillis())) return
        val request = nextRequest.incrementAndGet()
        snapshot = FamilyHttpSnapshot(null, null, System.currentTimeMillis(), session, server,
            path, token, request, true)

        Thread({
            fun stillCurrent(): Boolean = nextRequest.get() == request &&
                ConnectionEvidence.token == token && RayVpnService.connected &&
                !RayVpnService.busy && !RayVpnService.blocked &&
                RayVpnService.since == session && AppState.selectedId == server &&
                Pinger.probePath == path
            if (!stillCurrent()) return@Thread
            val v4 = FamilyHttpPolicy.classify(runCatching {
                HeadProbe.get("http://api4.ipify.org", Pinger.connector(path))
            }.getOrNull(), IpFamily.V4)
            if (!stillCurrent()) return@Thread
            val v6 = FamilyHttpPolicy.classify(runCatching {
                HeadProbe.get("http://api6.ipify.org", Pinger.connector(path))
            }.getOrNull(), IpFamily.V6)
            main.post {
                if (stillCurrent()) snapshot = FamilyHttpSnapshot(
                    v4, v6, System.currentTimeMillis(), session, server,
                    path, token, request, false)
            }
        }, "VitrumFamilyHttpProbe").start()
    }

    fun current(now: Long): FamilyHttpSnapshot? {
        val value = snapshot ?: return null
        return value.takeIf {
            FamilyHttpPolicy.isCurrent(it, RayVpnService.connected, RayVpnService.busy,
                RayVpnService.blocked, RayVpnService.since, AppState.selectedId,
                Pinger.probePath, ConnectionEvidence.token, nextRequest.get(), now)
        }
    }
}
