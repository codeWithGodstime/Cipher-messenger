package com.example.messenger.data.chat

import com.example.messenger.data.api.WsUserHit

/**
 * A user.search.results frame. [requestId] matches the command that produced it.
 */
data class UserSearchFrame(
    val query: String,
    val requestId: String,
    val users: List<WsUserHit>,
    val error: String?,
)

/**
 * Typed username fragment: lowercase letters, digits, and underscores, at most 32.
 * Blank means the search should return no users.
 */
fun normalizeUsernameQuery(raw: String): String {
    val trimmed = raw.trim().removePrefix("@").lowercase()
    val out = StringBuilder()
    for (ch in trimmed) {
        if (ch !in 'a'..'z' && ch !in '0'..'9' && ch != '_') continue
        if (out.length >= 32) break
        out.append(ch)
    }
    return out.toString()
}

/** True when this result belongs to the newest search still on screen. */
fun acceptUserSearch(responseId: String?, latestId: String): Boolean {
    return latestId.isNotEmpty() && responseId == latestId
}
