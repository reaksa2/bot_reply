package com.example.manualreply

/**
 * Caches resolved Telegram profile photo URLs by userId, in memory only.
 * Prevents re-hitting Telegram's API (and re-flickering the UI) every
 * time the conversation list or thread polls for updates.
 */
object AvatarCache {
    // null value = "we checked, this person has no photo" (still cache the negative result)
    private val cache = HashMap<String, String?>()
    private val inFlight = HashSet<String>()

    fun get(userId: String): String? = cache[userId]

    fun has(userId: String): Boolean = cache.containsKey(userId)

    fun put(userId: String, url: String?) {
        cache[userId] = url
    }

    // prevents firing duplicate network fetches for the same userId
    // while one is already in progress (e.g. list scroll rebinding)
    @Synchronized
    fun markInFlight(userId: String): Boolean {
        if (inFlight.contains(userId)) return false
        inFlight.add(userId)
        return true
    }

    @Synchronized
    fun clearInFlight(userId: String) {
        inFlight.remove(userId)
    }
}
