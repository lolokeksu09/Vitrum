package app.rayclient

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

/**
 * Reads only public LinkProperties for the active network available to VITRUM.
 * The app itself is excluded from its Android VPN, so the network may not be
 * the tunneled app network. No DNS host/IP is displayed, logged or persisted.
 * Called only after explicit user action, never on the UI's recomposition.
 */
internal object AndroidPrivateDnsObservation {
    fun read(ctx: Context): AndroidPrivateDnsMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return AndroidPrivateDnsMode.UNSUPPORTED
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching AndroidPrivateDnsMode.UNKNOWN
            val network = cm.activeNetwork ?: return@runCatching AndroidPrivateDnsMode.UNKNOWN
            val link = cm.getLinkProperties(network) ?: return@runCatching AndroidPrivateDnsMode.UNKNOWN
            AndroidPrivateDnsPolicy.classify(
                Build.VERSION.SDK_INT, true, !link.privateDnsServerName.isNullOrBlank(),
                link.isPrivateDnsActive
            )
        }.getOrDefault(AndroidPrivateDnsMode.UNKNOWN)
    }
}
