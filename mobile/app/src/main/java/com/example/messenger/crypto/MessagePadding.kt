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
 * Lowest and highest session version this client accepts.
 * Version 3 is the pre-Kyber format. Version 4 is the current one.
 */
const val SIGNAL_MIN_MESSAGE_VERSION = 3
const val SIGNAL_MAX_MESSAGE_VERSION = 4

/**
 * Libsignal puts a version byte first. The high nibble is the session version.
 * The low nibble is the ciphertext format version, currently 4.
 * That byte does not say whether the message is a PreKey or a Whisper message.
 *
 * Returns the format version when [bytes] looks like Signal ciphertext.
 * A null result means the bytes should be shown as ordinary text.
 */
fun signalCiphertextType(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
    val header = bytes[0].toInt() and 0xFF
    val messageVersion = header ushr 4
    val formatVersion = header and 0x0F
    if (messageVersion !in SIGNAL_MIN_MESSAGE_VERSION..SIGNAL_MAX_MESSAGE_VERSION) return null
    if (formatVersion !in SIGNAL_MIN_MESSAGE_VERSION..SIGNAL_MAX_MESSAGE_VERSION) return null
    return formatVersion
}
