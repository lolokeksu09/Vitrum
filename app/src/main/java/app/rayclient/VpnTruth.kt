package app.rayclient

/** Строка итога сравнения. word — короткая пометка справа, state задаёт цвет (SEEN — жёлтый, CLEAN — зелёный, UNKNOWN — серый). */
class TruthNote(val title: String, val detail: String, val state: TraceState, val word: String)

class TruthResult(val notes: List<TruthNote>, val at: Long, val traceAt: Long)

/** Правило маршрутизации «uid из диапазона идёт в таблицу VPN». */
class VpnRule(val from: Int, val to: Int, val table: String)

/**
 * «Эталон» для проверки «Что видят другие приложения» (нужен root): что в системе на самом деле, с точки зрения root.
 * Сравнение отличает «признака нет» от «признак есть, но приложению закрыт» и показывает, внутри ли правил VPN сам Vitrum.
 * Только чтение: `ip -o link show` и `ip rule show`.
 */
object VpnTruth {
    private val linkRe = Regex("^(\\d+):\\s+([^:\\s]+):")
    private val uidRe = Regex("uidrange\\s+(\\d+)-(\\d+)")
    private val tableRe = Regex("lookup\\s+(\\S+)")

    /** (номер, имя) интерфейсов из `ip -o link show`; суффикс «@родитель» у имени отбрасывается. */
    fun parseLinks(text: String): List<Pair<Int, String>> = text.lineSequence().mapNotNull { line ->
        val m = linkRe.find(line.trim()) ?: return@mapNotNull null
        (m.groupValues[1].toIntOrNull() ?: return@mapNotNull null) to m.groupValues[2].substringBefore('@')
    }.distinct().toList()

    /**
     * Правила с uidrange, ведущие в таблицу VPN. Таблица задаётся именем интерфейса (tun0) или числом: netd нумерует таблицы
     * интерфейсов как номер интерфейса + 1000 (по документации ZDT-D; на устройстве не проверялось), поэтому число сверяем с номерами VPN-интерфейсов.
     */
    fun parseVpnRules(text: String, links: List<Pair<Int, String>>): List<VpnRule> {
        val vpnTables = links.filter { VpnTraces.isVpnIface(it.second) }.map { (it.first + 1000).toString() }.toSet()
        return text.lineSequence().mapNotNull { line ->
            val u = uidRe.find(line) ?: return@mapNotNull null
            val table = tableRe.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            if (!VpnTraces.isVpnIface(table) && table !in vpnTables) return@mapNotNull null
            val a = u.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val b = u.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            VpnRule(a, b, table)
        }.toList()
    }

    private val COMPARED = listOf("iface_list", "proc_route", "proc_dev", "sys_net")

    fun evaluate(items: List<TraceItem>, links: List<Pair<Int, String>>?, rules: List<VpnRule>?, myUid: Int): List<TruthNote> {
        val l = mutableListOf<TruthNote>()
        val vpn = links?.map { it.second }?.filter(VpnTraces::isVpnIface).orEmpty()
        l += when {
            links == null -> TruthNote("Интерфейсы в системе (по данным root)", "Root не отдал список интерфейсов.", TraceState.UNKNOWN, "Нет данных")
            vpn.isEmpty() -> TruthNote("Интерфейсы в системе (по данным root)", "В системе нет VPN-интерфейса. Подключитесь к серверу и проверьте снова.", TraceState.UNKNOWN, "Нечего сравнивать")
            else -> TruthNote("Интерфейсы в системе (по данным root)", "В системе есть: ${vpn.joinToString()}.", TraceState.SEEN, "Есть")
        }
        l += when {
            rules == null -> TruthNote("Правила маршрутизации (по данным root)", "Root не отдал таблицу правил.", TraceState.UNKNOWN, "Нет данных")
            rules.isEmpty() -> TruthNote("Правила маршрутизации (по данным root)", "Правил с uidrange для VPN не нашёл: формат вывода на этом телефоне мог отличаться.", TraceState.UNKNOWN, "Нет данных")
            else -> {
                val r = rules.firstOrNull { myUid in it.from..it.to }
                if (r != null) TruthNote("Правила маршрутизации (по данным root)", "Номер приложения (uid $myUid) входит в правило VPN ${r.from}–${r.to}: проверки выше показывают вид приложения внутри VPN.", TraceState.SEEN, "Внутри VPN")
                else TruthNote("Правила маршрутизации (по данным root)", "Номер приложения (uid $myUid) не входит в правила VPN: проверки выше показывают вид приложения вне туннеля, как у банка, исключённого из VPN.", TraceState.CLEAN, "Вне VPN")
            }
        }
        if (vpn.isNotEmpty()) for (key in COMPARED) {
            val it0 = items.firstOrNull { it.key == key } ?: continue
            l += when (it0.state) {
                TraceState.SEEN -> TruthNote(it0.title, "Виден приложению и есть в системе.", TraceState.SEEN, "Подтверждено")
                TraceState.CLEAN -> TruthNote(it0.title, "В системе интерфейс есть, а приложение его не видит: это закрыто системой или скрыто.", TraceState.CLEAN, "Скрыто или закрыто")
                TraceState.UNKNOWN -> TruthNote(it0.title, "Приложению доступ закрыт, а в системе интерфейс есть.", TraceState.UNKNOWN, "Закрыто")
            }
        }
        return l
    }

    /** Запуск в фоне после проверки «Что видят другие приложения». */
    fun run() {
        if (AppState.truthBusy) return
        if (!AppState.rootOn) { AppState.message = "Root-функции выключены. Включите их в «Настройки → Сервис → Root»."; return }
        val tr = AppState.traceResult
        if (tr == null) { AppState.message = "Сначала запустите проверку «Что видят другие приложения»."; return }
        AppState.truthBusy = true
        val uid = android.os.Process.myUid()
        Thread {
            try {
                if (!RootShell.available()) { AppState.message = "Нет root-доступа. Выдайте его приложению в менеджере root и повторите."; return@Thread }
                val lk = RootShell.run("ip -o link show"); val rl = RootShell.run("ip rule show")
                val links = if (lk.ok) parseLinks(lk.out) else null
                val rules = if (rl.ok) parseVpnRules(rl.out, links.orEmpty()) else null
                AppState.truthResult = TruthResult(evaluate(tr.items, links, rules, uid), System.currentTimeMillis(), tr.at)
            } finally { AppState.truthBusy = false }
        }.start()
    }
}
