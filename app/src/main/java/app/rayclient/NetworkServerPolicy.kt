package app.rayclient

import org.json.JSONArray
import org.json.JSONObject

internal enum class NetworkKind { WIFI, MOBILE, ROAMING, OPEN_WIFI, OTHER }

internal data class NetworkEnvironment(
    val kind: NetworkKind,
    val ssid: String? = null
)

internal data class NetworkSsidServerRule(
    val ssid: String,
    val serverId: String
)

internal data class NetworkServerPolicy(
    val enabled: Boolean = false,
    val wifiServerId: String? = null,
    val mobileServerId: String? = null,
    val roamingServerId: String? = null,
    val openWifiServerId: String? = null,
    val ssidRules: List<NetworkSsidServerRule> = emptyList()
) {
    fun setSsid(ssid: String, serverId: String): NetworkServerPolicy {
        val name = ssid.trim()
        require(name.isNotEmpty()) { "SSID is empty" }
        require(serverId.isNotBlank()) { "server id is empty" }
        return copy(ssidRules = ssidRules.filterNot { it.ssid == name } + NetworkSsidServerRule(name, serverId))
    }

    fun removeSsid(ssid: String): NetworkServerPolicy =
        copy(ssidRules = ssidRules.filterNot { it.ssid == ssid })

    fun removeMissingServers(exists: (String) -> Boolean): NetworkServerPolicy = copy(
        wifiServerId = wifiServerId?.takeIf(exists),
        mobileServerId = mobileServerId?.takeIf(exists),
        roamingServerId = roamingServerId?.takeIf(exists),
        openWifiServerId = openWifiServerId?.takeIf(exists),
        ssidRules = ssidRules.filter { it.serverId.isNotBlank() && exists(it.serverId) }
    )

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("enabled", enabled)
        .putNullable("wifi", wifiServerId)
        .putNullable("mobile", mobileServerId)
        .putNullable("roaming", roamingServerId)
        .putNullable("openWifi", openWifiServerId)
        .put("ssids", JSONArray().apply {
            ssidRules.forEach { put(JSONObject().put("ssid", it.ssid).put("server", it.serverId)) }
        })

    companion object {
        fun fromJson(json: JSONObject): NetworkServerPolicy {
            require(json.optInt("v", 1) == 1) { "unsupported network policy version" }
            val rules = ArrayList<NetworkSsidServerRule>()
            val array = json.optJSONArray("ssids") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ssid = item.optString("ssid").trim()
                val server = item.optString("server")
                if (ssid.isNotEmpty() && server.isNotBlank()) {
                    rules.removeAll { it.ssid == ssid }
                    rules += NetworkSsidServerRule(ssid, server)
                }
            }
            return NetworkServerPolicy(
                enabled = json.optBoolean("enabled", false),
                wifiServerId = json.optNullableString("wifi"),
                mobileServerId = json.optNullableString("mobile"),
                roamingServerId = json.optNullableString("roaming"),
                openWifiServerId = json.optNullableString("openWifi"),
                ssidRules = rules
            )
        }
    }
}

private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
    put(key, value?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal object NetworkServerPolicyResolver {
    fun resolve(
        policy: NetworkServerPolicy,
        network: NetworkEnvironment,
        availableServerIds: Set<String>
    ): String? {
        if (!policy.enabled) return null
        val candidate = when (network.kind) {
            NetworkKind.WIFI -> {
                val ssid = Scenarios.cleanSsid(network.ssid)
                ssid?.let { name -> policy.ssidRules.firstOrNull { it.ssid == name }?.serverId }
                    ?.takeIf { it in availableServerIds }
                    ?: policy.wifiServerId
            }
            NetworkKind.MOBILE -> policy.mobileServerId
            // Roaming inherits the mobile rule only when no roaming-specific rule was configured.
            NetworkKind.ROAMING -> policy.roamingServerId ?: policy.mobileServerId
            // An open network must never inherit a trusted/known Wi-Fi SSID rule or the regular Wi-Fi fallback.
            NetworkKind.OPEN_WIFI -> policy.openWifiServerId
            NetworkKind.OTHER -> null
        }
        return candidate?.takeIf { it in availableServerIds }
    }
}

internal data class NetworkServerRuntimeKey private constructor(
    val kind: NetworkKind,
    val ssid: String?
) {
    companion object {
        fun of(network: NetworkEnvironment) = NetworkServerRuntimeKey(
            network.kind,
            network.ssid?.let(Scenarios::cleanSsid)
                .takeIf { network.kind == NetworkKind.WIFI || network.kind == NetworkKind.OPEN_WIFI }
        )
    }
}

/**
 * Process-local manual override. Selecting a server by hand wins for this network only;
 * the next real network identity change clears the override and evaluates the new rule.
 */
internal class NetworkServerPolicyRuntime {
    private var currentKey: NetworkServerRuntimeKey? = null
    private var manualOverrideKey: NetworkServerRuntimeKey? = null

    @Synchronized
    fun onNetworkChanged(
        policy: NetworkServerPolicy,
        network: NetworkEnvironment,
        currentServerId: String?,
        availableServerIds: Set<String>,
        force: Boolean = false
    ): String? {
        val key = NetworkServerRuntimeKey.of(network)
        val changed = key != currentKey
        if (!changed && (!force || manualOverrideKey == key)) return null
        currentKey = key
        if (changed) manualOverrideKey = null
        if (manualOverrideKey == key) return null
        return NetworkServerPolicyResolver.resolve(policy, network, availableServerIds)
            ?.takeIf { it != currentServerId }
    }

    @Synchronized
    fun noteManualSelection(network: NetworkEnvironment) {
        val key = NetworkServerRuntimeKey.of(network)
        currentKey = key
        manualOverrideKey = key
    }
}
