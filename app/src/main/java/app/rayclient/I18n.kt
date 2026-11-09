package app.rayclient

import android.content.Context
import java.util.Locale

/**
 * Слой перевода интерфейса. Тексты в коде русские; при английском языке они переводятся в момент показа
 * (в Text, подсказках, уведомлениях и тостах) по словарю res/raw/i18n_en.tsv. Логика приложения от перевода не зависит.
 * Словарь: «S» — обычная фраза или шаблон с {0},{1}; «P» — фраза-начало («Сейчас:», «до»), за которой идёт переменная часть.
 * Строки из нескольких строк и со « · » переводятся по частям.
 */
object I18n {
    private class Tpl(val re: Regex, val en: String, val order: List<Int>, val prefix: Boolean, val literalLen: Int)

    @Volatile private var exact: Map<String, String> = emptyMap()
    @Volatile private var tpls: List<Tpl> = emptyList()
    @Volatile private var loaded = false
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val CYR = Regex("[А-Яа-яЁё]")
    private val PH = Regex("""\{(\d+)\}""")

    fun english(): Boolean = when (AppState.lang) { "en" -> true; "ru" -> false; else -> Locale.getDefault().language != "ru" }
    val locale: Locale get() = if (english()) Locale.ENGLISH else Locale("ru")

    fun init(ctx: Context) {
        if (loaded) return
        runCatching { load(ctx.resources.openRawResource(R.raw.i18n_en).bufferedReader(Charsets.UTF_8).readText()) }
    }

    /** Формат строки: тип<TAB>русский<TAB>английский. */
    fun load(tsv: String) {
        val ex = HashMap<String, String>(); val tp = ArrayList<Tpl>()
        for (line in tsv.split('\n')) {
            val p = line.split('\t'); if (p.size != 3) continue
            val prefix = p[0] == "P"; val ru = p[1]; val en = p[2]
            if (!prefix && !PH.containsMatchIn(ru)) { ex[ru] = en; continue }
            // шаблон: литералы экранируем, {n} превращаем в группы захвата. У коротких шаблонов вроде «{0} мс» переменная — одно слово без пробелов,
            // иначе они «съедали» бы целые куски других фраз.
            val group = if (PH.replace(ru, "").length <= 5) """([^\s·]+)""" else "(.+?)"
            val sb = StringBuilder("^"); val order = ArrayList<Int>(); var last = 0; var lit = 0
            for (m in PH.findAll(ru)) {
                val chunk = ru.substring(last, m.range.first); lit += chunk.length
                sb.append(Regex.escape(chunk)).append(group); order += m.groupValues[1].toInt(); last = m.range.last + 1
            }
            val tail = ru.substring(last); lit += tail.length; sb.append(Regex.escape(tail))
            if (prefix) { sb.append(if (tail.endsWith(":")) """\s*(.*)""" else """\s+(.*)"""); order += -1 }
            sb.append("$")
            if (lit >= 2) tp.add(Tpl(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), en, order, prefix, lit))
        }
        exact = ex; tpls = tp.sortedWith(compareBy<Tpl> { it.prefix }.thenByDescending { it.literalLen })
        cache.clear(); loaded = true
    }

    fun tr(s: String): String {
        if (s.isEmpty() || !CYR.containsMatchIn(s) || !english()) return s
        if (cache.size > 3000) cache.clear()
        return cache.getOrPut(s) { translate(s) }
    }

    /** Перевод без проверки языка (для тестов и фонового кода). */
    fun translate(s: String): String = s.split('\n').joinToString("\n") { line(it) }

    private fun line(l: String): String {
        if (l.isBlank() || !CYR.containsMatchIn(l)) return l
        val lead = l.takeWhile { it.isWhitespace() }; val trail = l.takeLastWhile { it.isWhitespace() }; val core = l.trim()
        direct(core)?.let { return lead + it + trail }
        if (" · " in core) return lead + joinParts(core.split(" · ")) + trail
        val sentences = core.split(Regex("(?<=[.!?…])\\s+"))
        if (sentences.size > 1) return lead + sentences.joinToString(" ") { line(it) } + trail
        return l
    }

    /** Части строки через « · »: ищем самое длинное сочетание соседних частей, для которого есть перевод, иначе переводим часть отдельно. */
    private fun joinParts(parts: List<String>): String {
        val out = ArrayList<String>(); var i = 0
        while (i < parts.size) {
            var done = false
            for (j in parts.size - 1 downTo i + 1) {
                val d = direct(parts.subList(i, j + 1).joinToString(" · ").trim())
                if (d != null) { out.add(d); i = j + 1; done = true; break }
            }
            if (!done) { out.add(line(parts[i])); i++ }
        }
        return out.joinToString(" · ")
    }

    /** Точное совпадение или совпадение с другой первой буквой («Нет ответа» и «нет ответа»): регистр результата подгоняется под исходный. */
    private fun direct(core: String): String? {
        direct0(core)?.let { return it }
        val c0 = core.firstOrNull() ?: return null
        if (!c0.isLetter()) return null
        val flipped = (if (c0.isUpperCase()) c0.lowercaseChar() else c0.uppercaseChar()) + core.substring(1)
        val r = direct0(flipped) ?: return null
        return if (c0.isUpperCase()) r.replaceFirstChar { it.uppercaseChar() } else r.replaceFirstChar { it.lowercaseChar() }
    }

    private fun direct0(core: String): String? {
        exact[core]?.let { return it }
        for (t in tpls) {
            val m = t.re.matchEntire(core) ?: continue
            if (t.prefix) {
                val rest = m.groupValues.last()
                if (rest.isBlank() || (CYR.containsMatchIn(rest) && !core.substringBefore(rest).trim().endsWith(":"))) continue
            }
            val vals = HashMap<Int, String>()
            t.order.forEachIndexed { i, n -> vals[n] = m.groupValues[i + 1] }
            var out = PH.replace(t.en) { r -> line(vals[r.groupValues[1].toInt()] ?: "") }
            if (t.prefix) { val rest = vals[-1] ?: ""; if (rest.isNotBlank()) out += " " + line(rest) }
            return out
        }
        return null
    }
}
