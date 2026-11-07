package com.example.messenger.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals an attachment with AES-256-GCM.
 * The key, nonce, and digest are small enough to travel inside a Signal message.
 * Only [Sealed.ciphertext] is uploaded.
 */
object MediaCipher {
    const val MAX_PLAINTEXT_BYTES = (32 * 1024 * 1024) - 16

    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val DIGEST_HEX_CHARS = 64

    private val random = SecureRandom()

    data class Sealed(
        val ciphertext: ByteArray,
        val key: ByteArray,
        val nonce: ByteArray,
        val digestHex: String,
    )

    fun seal(plaintext: ByteArray, attachmentId: String): Sealed {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Plaintext is larger than $MAX_PLAINTEXT_BYTES bytes."
        }
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val ciphertext = crypt(Cipher.ENCRYPT_MODE, plaintext, key, nonce, attachmentId)
        return Sealed(
            ciphertext = ciphertext,
            key = key,
            nonce = nonce,
            digestHex = hex(sha256(ciphertext)),
        )
    }

    fun open(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        attachmentId: String,
        digestHex: String,
    ): ByteArray {
        val actual = sha256(ciphertext)
        val expected = decodeHex(digestHex)
        if (expected == null || !MessageDigest.isEqual(actual, expected)) {
            throw IllegalArgumentException("Ciphertext digest does not match.")
        }
        return crypt(Cipher.DECRYPT_MODE, ciphertext, key, nonce, attachmentId)
    }

    private fun crypt(
        mode: Int,
        input: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        attachmentId: String,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(attachmentId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(input)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String {
        val alphabet = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (index in bytes.indices) {
            val value = bytes[index].toInt() and 0xff
            out[index * 2] = alphabet[value ushr 4]
            out[index * 2 + 1] = alphabet[value and 0x0f]
        }
        return String(out)
    }

    private fun decodeHex(hex: String): ByteArray? {
        if (hex.length != DIGEST_HEX_CHARS) return null
        val out = ByteArray(DIGEST_HEX_CHARS / 2)
        for (index in out.indices) {
            val high = hexNibble(hex[index * 2])
            val low = hexNibble(hex[index * 2 + 1])
            if (high < 0 || low < 0) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private fun hexNibble(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }
}
