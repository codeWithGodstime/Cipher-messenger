package com.example.messenger

import com.example.messenger.crypto.padPlaintext
import com.example.messenger.crypto.signalCiphertextType
import com.example.messenger.crypto.unpadPlaintext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePaddingTest {
    @Test
    fun padsToTheNextBlockAndRoundTrips() {
        val message = "hello".toByteArray()
        val padded = padPlaintext(message)
        assertEquals(160, padded.size)
        assertEquals(0x80.toByte(), padded[message.size])
        assertArrayEquals(message, unpadPlaintext(padded))
    }

    @Test
    fun aFullBlockStillGainsAnother() {
        val message = ByteArray(160) { 1 }
        val padded = padPlaintext(message)
        assertEquals(320, padded.size)
        assertArrayEquals(message, unpadPlaintext(padded))
    }

    @Test
    fun recognizesASignalCiphertextVersionByte() {
        // High nibble is the session version. Low nibble is the format version.
        assertEquals(4, signalCiphertextType(byteArrayOf(0x44)))
        assertEquals(4, signalCiphertextType(byteArrayOf(0x34)))
        assertEquals(3, signalCiphertextType(byteArrayOf(0x33)))
        assertNull(signalCiphertextType(byteArrayOf(0x32)))
        assertNull(signalCiphertextType(byteArrayOf(0x22)))
        assertNull(signalCiphertextType(byteArrayOf()))
    }
}
