package app.rayclient

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Конфигурации авто-серверов (JSON-подписка). В них ключи серверов, поэтому лежат отдельным файлом в закрытой папке приложения
 * и шифруются тем же ключом Keystore, что и ссылки (Vault). Отдельно от настроек, чтобы не перезаписывать их при каждом сохранении.
 */
object AutoStore {
    private val plans = HashMap<String, AutoPlan>()   // ссылка авто-сервера -> план
    private val subOf = HashMap<String, String>()     // ссылка авто-сервера -> id подписки
    private var loaded = false
    private var unreadable = false   // файл не расшифровался: не перезаписываем его до перезапуска

    private fun file(c: Context) = File(c.filesDir, "autosub.dat")

    @Synchronized fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file(c); if (!f.exists()) return
            val root = JSONObject(Vault.open(f.readText()) ?: run { unreadable = true; return })
            for (k in root.keys()) root.getJSONObject(k).let { o -> plans[k] = AutoSub.fromJson(o.getJSONObject("plan")); subOf[k] = o.getString("sub") }
        }
    }

    @Synchronized fun get(c: Context, link: String): AutoPlan? { load(c); return plans[link] }

    /** Заменяет все авто-конфигурации подписки. */
    @Synchronized fun replace(c: Context, subId: String, refs: List<AutoRef>) {
        load(c)
        subOf.filterValues { it == subId }.keys.toList().forEach { plans.remove(it); subOf.remove(it) }
        refs.forEach { plans[it.key] = it.plan; subOf[it.key] = subId }
        persist(c)
    }

    @Synchronized fun removeSub(c: Context, subId: String) = replace(c, subId, emptyList())

    private fun persist(c: Context) {
        val root = JSONObject()
        plans.forEach { (k, p) -> root.put(k, JSONObject().put("sub", subOf[k]).put("plan", AutoSub.toJson(p))) }
        val f = file(c)
        if (unreadable) return
        if (plans.isEmpty()) { f.delete(); return }
        val sealed = Vault.seal(root.toString()) ?: return   // без Keystore открытым текстом не пишем, на диске остаётся прежний файл
        f.apply { createNewFile(); setReadable(false, false); setReadable(true, true); setWritable(false, false); setWritable(true, true) }.writeText(sealed)
    }
}
