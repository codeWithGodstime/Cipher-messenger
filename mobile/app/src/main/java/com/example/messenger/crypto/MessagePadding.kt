package com.example.messenger.crypto

/** Matches [org.signal.libsignal.protocol.message.CiphertextMessage.WHISPER_TYPE]. */
const val SIGNAL_WHISPER_TYPE = 2

/** Matches [org.signal.libsignal.protocol.message.CiphertextMessage.PREKEY_TYPE]. */
const val SIGNAL_PREKEY_TYPE = 3

private const val PAD_BLOCK = 160

/**
 * Signal padding: 0x80 then zeros, out to the next 160-byte boundary.
 * The padding hides the exact plaintext length.
 */
fun padPlaintext(message: ByteArray): ByteArray {
    val paddedLength = (message.size / PAD_BLOCK + 1) * PAD_BLOCK
    val padded = ByteArray(paddedLength)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    return padded
}

fun unpadPlaintext(message: ByteArray): ByteArray {
    var end = message.size - 1
    while (end >= 0 && message[end] == 0.toByte()) end--
    if (end < 0 || message[end] != 0x80.toByte()) {
        error("Ciphertext padding is invalid")
    }
    return message.copyOf(end)
}

/**
 * Libsignal ciphertext starts with a version nibble and a type nibble.
 * Returns the type when this looks like a Whisper or PreKey message.
 */
fun signalCiphertextType(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
    val header = bytes[0].toInt() and 0xFF
    val version = header ushr 4
    val type = header and 0x0F
    if (version < 3) return null
    return when (type) {
        SIGNAL_WHISPER_TYPE, SIGNAL_PREKEY_TYPE -> type
        else -> null
    }
}
