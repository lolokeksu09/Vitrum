package app.rayclient

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Commit a new in-memory snapshot only after encrypted persistence succeeds. */
internal class AutoStoreState {
    val plans = HashMap<String, AutoPlan>()
    val subOf = HashMap<String, String>()

    fun replace(
        subId: String,
        refs: List<AutoRef>,
        persist: (Map<String, AutoPlan>, Map<String, String>) -> Boolean
    ): Boolean {
        val nextPlans = HashMap(plans)
        val nextOwners = HashMap(subOf)
        nextOwners.filterValues { it == subId }.keys.toList().forEach {
            nextPlans.remove(it)
            nextOwners.remove(it)
        }
        refs.forEach { nextPlans[it.key] = it.plan; nextOwners[it.key] = subId }
        if (!persist(nextPlans, nextOwners)) return false
        plans.clear(); plans.putAll(nextPlans)
        subOf.clear(); subOf.putAll(nextOwners)
        return true
    }
}

/** Конфигурации авто-серверов с секретами хранятся зашифрованными через Vault. */
object AutoStore {
    private val state = AutoStoreState()
    private var loaded = false
    private var unreadable = false

    private fun file(c: Context) = File(c.filesDir, "autosub.dat")

    @Synchronized fun load(c: Context) {
        if (loaded) return
        loaded = true
        val f = file(c)
        if (!f.exists() && !File(f.path + ".bak").exists()) return
        try {
            val raw = AtomicFile(f).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val plain = Vault.open(raw) ?: error("Keystore недоступен")
            val root = JSONObject(plain)
            val loadedPlans = HashMap<String, AutoPlan>()
            val owners = HashMap<String, String>()
            for (k in root.keys()) {
                val o = root.getJSONObject(k)
                loadedPlans[k] = AutoSub.fromJson(o.getJSONObject("plan"))
                owners[k] = o.getString("sub")
            }
            state.plans.putAll(loadedPlans)
            state.subOf.putAll(owners)
        } catch (_: Exception) {
            // A corrupt or undecryptable file must never be overwritten.
            unreadable = true
        }
    }

    @Synchronized fun get(c: Context, link: String): AutoPlan? {
        load(c)
        return state.plans[link]
    }

    /** false = old persisted and in-memory snapshots are preserved. */
    @Synchronized fun replace(c: Context, subId: String, refs: List<AutoRef>): Boolean {
        load(c)
        if (unreadable) return false
        return state.replace(subId, refs) { plans, owners -> persist(c, plans, owners) }
    }

    @Synchronized fun removeSub(c: Context, subId: String): Boolean = replace(c, subId, emptyList())

    private fun persist(c: Context, plans: Map<String, AutoPlan>, owners: Map<String, String>): Boolean = try {
        val root = JSONObject()
        plans.forEach { (k, p) ->
            root.put(k, JSONObject().put("sub", owners[k]).put("plan", AutoSub.toJson(p)))
        }
        // An encrypted empty snapshot is safer than deleting the old file non-atomically.
        val sealed = Vault.seal(root.toString()) ?: return false
        val file = AtomicFile(file(c))
        val stream = file.startWrite()
        try {
            stream.write(sealed.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            true
        } catch (_: Exception) {
            file.failWrite(stream)
            false
        }
    } catch (_: Exception) {
        false
    }
}
