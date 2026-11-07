package com.example.messenger

import com.example.messenger.crypto.MediaCipher
import com.example.messenger.crypto.MediaMessages
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.AEADBadTagException

class MediaCipherTest {
    @Test
    fun sealAndOpenRoundTrip() {
        val plaintext = "a photo".toByteArray()
        val sealed = MediaCipher.seal(plaintext, ID)
        assertEquals(32, sealed.key.size)
        assertEquals(12, sealed.nonce.size)
        assertEquals(plaintext.size + 16, sealed.ciphertext.size)
        assertEquals(sha256Hex(sealed.ciphertext), sealed.digestHex)
        assertEquals(sealed.digestHex, sealed.digestHex.lowercase())
        assertArrayEquals(
            plaintext,
            MediaCipher.open(sealed.ciphertext, sealed.key, sealed.nonce, ID, sealed.digestHex),
        )

        val empty = MediaCipher.seal(ByteArray(0), ID)
        assertEquals(16, empty.ciphertext.size)
        assertArrayEquals(
            ByteArray(0),
            MediaCipher.open(empty.ciphertext, empty.key, empty.nonce, ID, empty.digestHex),
        )
    }

    @Test
    fun flippedCiphertextByteFails() {
        val sealed = MediaCipher.seal("hello".toByteArray(), ID)
        val flipped = sealed.ciphertext.copyOf()
        flipped[0] = (flipped[0].toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            MediaCipher.open(flipped, sealed.key, sealed.nonce, ID, sha256Hex(flipped))
        }
    }

    @Test
    fun wrongAttachmentIdFails() {
        val sealed = MediaCipher.seal("hello".toByteArray(), ID)
        assertThrows(AEADBadTagException::class.java) {
            MediaCipher.open(
                sealed.ciphertext,
                sealed.key,
                sealed.nonce,
                "22222222-2222-2222-2222-222222222222",
                sealed.digestHex,
            )
        }
    }

    @Test
    fun wrongDigestFails() {
        val sealed = MediaCipher.seal("hello".toByteArray(), ID)
        val wrong = sealed.digestHex.replaceRange(
            0,
            1,
            if (sealed.digestHex[0] == 'a') "b" else "a",
        )
        assertThrows(IllegalArgumentException::class.java) {
            MediaCipher.open(sealed.ciphertext, ByteArray(32), sealed.nonce, "other", wrong)
        }
    }

    @Test
    fun encodeAndParseRoundTrip() {
        val key = ByteArray(32) { index -> (index + 3).toByte() }
        val nonce = ByteArray(12) { index -> (index + 9).toByte() }
        val digest = "ab".repeat(32)
        val encoded = MediaMessages.encode(
            kind = "image",
            mime = "image/jpeg",
            name = "photo.jpg",
            size = 12L,
            attachmentId = ID,
            key = key,
            nonce = nonce,
            digestHex = digest,
        )
        val keyEncoded = Base64.getEncoder().encodeToString(key)
        assertTrue(keyEncoded.endsWith("="))
        assertTrue(encoded.startsWith(MediaMessages.PREFIX))
        assertFalse(encoded.substring(MediaMessages.PREFIX.length).contains('\n'))
        assertTrue(encoded.contains("\"v\":1"))
        assertTrue(encoded.contains(keyEncoded))

        val parsed = MediaMessages.parse(encoded)
        assertEquals("image", parsed!!.kind)
        assertEquals("image/jpeg", parsed.mime)
        assertEquals("photo.jpg", parsed.name)
        assertEquals(12L, parsed.size)
        assertEquals(ID, parsed.attachmentId)
        assertArrayEquals(key, parsed.key)
        assertArrayEquals(nonce, parsed.nonce)
        assertEquals(digest, parsed.digestHex)
    }

    @Test
    fun encodeSanitizesTheFileName() {
        val encoded = MediaMessages.encode(
            kind = "file",
            mime = "application/pdf",
            name = "C:\\docs\\reports\\q3.pdf\r\n",
            size = 4L,
            attachmentId = ID,
            key = ByteArray(32),
            nonce = ByteArray(12),
            digestHex = "cd".repeat(32),
        )
        assertEquals("q3.pdf", MediaMessages.parse(encoded)!!.name)
        val longName = "folder/" + "a".repeat(90)
        val capped = MediaMessages.encode(
            kind = "file",
            mime = "text/plain",
            name = longName,
            size = 1L,
            attachmentId = ID,
            key = ByteArray(32),
            nonce = ByteArray(12),
            digestHex = "cd".repeat(32),
        )
        assertEquals("a".repeat(80), MediaMessages.parse(capped)!!.name)
        val blank = MediaMessages.encode(
            kind = "file",
            mime = "text/plain",
            name = "   \n",
            size = 1L,
            attachmentId = ID,
            key = ByteArray(32),
            nonce = ByteArray(12),
            digestHex = "cd".repeat(32),
        )
        assertEquals("file", MediaMessages.parse(blank)!!.name)
    }

    @Test
    fun plainTextIsNotMedia() {
        assertNull(MediaMessages.parse("hello"))
        assertNull(MediaMessages.parse("this media file is not an envelope"))
        assertEquals("hello", MediaMessages.snippet("hello"))
        assertEquals(
            "this media file is not an envelope",
            MediaMessages.snippet("this media file is not an envelope"),
        )
        assertNull(MediaMessages.parse(MediaMessages.PREFIX + "not-json"))
    }

    @Test
    fun snippetLabels() {
        assertEquals("Photo", MediaMessages.snippet(raw("image", "a.jpg")))
        assertEquals("Video", MediaMessages.snippet(raw("video", "a.mp4")))
        assertEquals("Audio", MediaMessages.snippet(raw("audio", "a.m4a")))
        assertEquals("notes.txt", MediaMessages.snippet(raw("file", "notes.txt")))
        assertEquals("Document", MediaMessages.snippet(raw("file", "")))
        assertEquals("Document", MediaMessages.snippet(raw("file", "   ")))
        assertEquals("Attachment", MediaMessages.snippet(raw("sticker", "x")))
    }

    private fun raw(kind: String, name: String): String {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val nonce = Base64.getEncoder().encodeToString(ByteArray(12) { 8 })
        return MediaMessages.PREFIX +
            """{"v":1,"kind":"$kind","mime":"application/octet-stream","name":"$name","size":1,"id":"$ID","key":"$key","nonce":"$nonce","digest":"${"ab".repeat(32)}"}"""
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val alphabet = "0123456789abcdef"
        val out = CharArray(digest.size * 2)
        for (index in digest.indices) {
            val value = digest[index].toInt() and 0xff
            out[index * 2] = alphabet[value ushr 4]
            out[index * 2 + 1] = alphabet[value and 0x0f]
        }
        return String(out)
    }

    private companion object {
        const val ID = "11111111-1111-1111-1111-111111111111"
    }
}
