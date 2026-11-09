package app.rayclient

/**
 * Case-insensitive search over both title and explanatory copy.
 * All typed words must occur, but can be in different fields.
 */
internal object SettingsSearch {
    fun matches(query: String, title: String, description: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = (title + " " + description).lowercase()
        return words.all { it in haystack }
    }
}
