package app.rayclient

/** Observation of the VITRUM process's active network, NOT a DNS leak test. */
internal enum class AndroidPrivateDnsMode(val label: String) {
    UNSUPPORTED("Android 8: системный Private DNS недоступен"),
    UNKNOWN("UNKNOWN: активная сеть или сведения DNS недоступны"),
    STRICT("Private DNS: строгий режим на активной сети процесса"),
    UPGRADED("Private DNS: шифрование включено автоматически"),
    NOT_ACTIVE("Private DNS: неактивен на активной сети процесса")
}

internal object AndroidPrivateDnsPolicy {
    /** No hostnames, addresses or network identity enter the returned state. */
    fun classify(
        sdk: Int, hasLinkProperties: Boolean, hasStrictHostname: Boolean,
        encryptedDnsActive: Boolean
    ): AndroidPrivateDnsMode = when {
        sdk < 28 -> AndroidPrivateDnsMode.UNSUPPORTED
        !hasLinkProperties -> AndroidPrivateDnsMode.UNKNOWN
        hasStrictHostname -> AndroidPrivateDnsMode.STRICT
        encryptedDnsActive -> AndroidPrivateDnsMode.UPGRADED
        else -> AndroidPrivateDnsMode.NOT_ACTIVE
    }
}
