package app.rayclient

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Custom DNS only accepts numeric IP literals. Reject hostnames before calling
 * InetAddress: do not trigger Android DNS lookups while validating settings.
 * Malformed values are not persisted as resolved IPs or sent to Xray.
 */
internal object DnsAddressPolicy {
    data class Parsed(val valid: List<String>, val invalidCount: Int) {
        val fallbackRequired: Boolean get() = valid.isEmpty()
        fun select(existingDefault: List<String>): List<String> =
            valid.ifEmpty { existingDefault }
    }

    fun isLiteral(address: String): Boolean {
        val ip = address.trim()
        if (ip.isEmpty() || '%' in ip || '[' in ip || ']' in ip) return false
        if (RuleCheck.isIpv4(ip)) return true
        if (':' !in ip || ip.any { c ->
                !(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' || c == ':' || c == '.')
            }) return false
        // A colon plus a constrained numeric alphabet cannot be a hostname.
        // The JDK/Android IP-literal parser rejects truncated or malformed IPv6.
        return runCatching { InetAddress.getByName(ip) is Inet6Address }.getOrDefault(false)
    }

    fun parse(raw: String): Parsed {
        val tokens = raw.split(',', ' ', '\n', '\t', ';').map { it.trim() }.filter(String::isNotEmpty)
        val valid = LinkedHashSet<String>()
        var invalid = 0
        for (token in tokens) {
            if (isLiteral(token)) valid += token
            else invalid++
        }
        return Parsed(valid.toList(), invalid)
    }
}
