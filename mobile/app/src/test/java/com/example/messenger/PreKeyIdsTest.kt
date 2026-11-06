package com.example.messenger

import com.example.messenger.crypto.ONE_TIME_PRE_KEY_ID_MAX
import com.example.messenger.crypto.allocateOneTimePreKeyIds
import com.example.messenger.crypto.nextOneTimePreKeyId
import org.junit.Assert.assertEquals
import org.junit.Test

class PreKeyIdsTest {
    @Test
    fun allocatesAFreshRun() {
        assertEquals(listOf(1, 2, 3), allocateOneTimePreKeyIds(1, 3, emptySet()))
    }

    @Test
    fun skipsIdsStillStored() {
        assertEquals(listOf(100, 102, 103), allocateOneTimePreKeyIds(100, 3, setOf(101)))
    }

    @Test
    fun wrapsAndSkipsTheUsedId() {
        assertEquals(
            listOf(ONE_TIME_PRE_KEY_ID_MAX, 1),
            allocateOneTimePreKeyIds(ONE_TIME_PRE_KEY_ID_MAX, 2, setOf(2)),
        )
        assertEquals(1, nextOneTimePreKeyId(ONE_TIME_PRE_KEY_ID_MAX))
    }
}
