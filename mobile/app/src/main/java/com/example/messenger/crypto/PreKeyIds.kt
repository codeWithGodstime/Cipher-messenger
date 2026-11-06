package com.example.messenger.crypto

/** Upload another batch when the server has fewer one-time prekeys than this. */
const val ONE_TIME_PRE_KEY_LOW_WATER = 10

/** New one-time prekeys generated on signup and on each top-up. */
const val ONE_TIME_PRE_KEY_BATCH = 100

/** Highest libsignal medium prekey id this client issues. Id 0 is unused. */
const val ONE_TIME_PRE_KEY_ID_MAX = 0xFFFFFE

/**
 * Ids for a new one-time prekey batch, starting at [start] and skipping [used].
 * The sequence wraps to 1 after [ONE_TIME_PRE_KEY_ID_MAX]. Fewer than [count]
 * ids are returned when the id space is already taken.
 */
fun allocateOneTimePreKeyIds(start: Int, count: Int, used: Set<Int>): List<Int> {
    if (count <= 0) return emptyList()
    val ids = ArrayList<Int>(count)
    val taken = HashSet<Int>(used)
    var cursor = if (start in 1..ONE_TIME_PRE_KEY_ID_MAX) start else 1
    var scanned = 0
    while (ids.size < count && scanned < ONE_TIME_PRE_KEY_ID_MAX) {
        if (taken.add(cursor)) ids.add(cursor)
        scanned++
        cursor = if (cursor >= ONE_TIME_PRE_KEY_ID_MAX) 1 else cursor + 1
    }
    return ids
}

/** The id to issue after [last], wrapping to 1 at the end of the range. */
fun nextOneTimePreKeyId(last: Int): Int =
    if (last >= ONE_TIME_PRE_KEY_ID_MAX) 1 else last + 1
