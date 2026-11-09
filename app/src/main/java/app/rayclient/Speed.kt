package app.rayclient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/** Скорость трафика Xray (байт в секунду) за последние 40 секунд. Считает служба VPN, главный экран только рисует. */
object Speed {
    const val WINDOW = 40
    val down = mutableStateListOf<Long>()
    val up = mutableStateListOf<Long>()
    var total by mutableLongStateOf(0L)

    fun reset() { down.clear(); up.clear(); total = 0L }

    fun add(d: Long, u: Long, sessionTotal: Long) {
        down.add(d); up.add(u); total = sessionTotal
        while (down.size > WINDOW) down.removeAt(0)
        while (up.size > WINDOW) up.removeAt(0)
    }
}
