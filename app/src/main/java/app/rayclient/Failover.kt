package app.rayclient

/** Чистая логика автопереключения серверов (вынесена из службы, чтобы её можно было проверять тестами). */
object Failover {
    /** Кандидаты на переключение: избранные первыми, затем по рейтингу. Текущий сервер исключается. */
    fun candidates(servers: List<Server>, currentId: String?, favs: Set<String>, rank: (Server) -> Double, limit: Int = 6): List<Server> =
        servers.filter { it.id != currentId }.sortedWith(compareByDescending<Server> { it.link in favs }.thenByDescending(rank)).take(limit)

    enum class Verdict { OK, FAIL_COUNTED, IGNORED, DISABLED, SWITCH }

    /**
     * Счётчик подряд идущих сбоев проверки связи. Сбой считается, только если у телефона есть сеть;
     * сбой самого служебного сокета отключает автопереключение (иначе оно переключало бы исправные серверы).
     */
    class Watch(private val threshold: Int = 3) {
        var fails = 0; private set
        var disabled = false; private set

        fun reset() { fails = 0 }

        fun onResult(ok: Boolean, note: String, hasNetwork: Boolean): Verdict {
            if (disabled) return Verdict.IGNORED
            if (ok) { fails = 0; return Verdict.OK }
            if (note == HeadProbe.PROXY_UNAVAILABLE) { disabled = true; return Verdict.DISABLED }
            if (!hasNetwork) return Verdict.IGNORED
            fails++
            if (fails >= threshold) { fails = 0; return Verdict.SWITCH }
            return Verdict.FAIL_COUNTED
        }
    }
}
