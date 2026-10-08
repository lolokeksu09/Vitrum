package app.rayclient

/** Порядок правил маршрутизации: Xray берёт первое подходящее правило, поэтому порядок в списке важен. */
object RuleOrder {
    /** Список с правилом [i], сдвинутым на [dir] (-1 вверх, 1 вниз). null, если сдвинуть некуда. Исходный список не меняется. */
    fun move(rules: List<Rule>, i: Int, dir: Int): List<Rule>? {
        val j = i + dir
        if (dir != -1 && dir != 1 || i !in rules.indices || j !in rules.indices) return null
        return rules.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
}
