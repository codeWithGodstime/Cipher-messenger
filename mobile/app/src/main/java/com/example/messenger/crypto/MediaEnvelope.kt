package com.example.messenger.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Short plaintext the UI encrypts with [com.example.messenger.data.chat.ChatCrypto].
 * The record-separator prefix keeps a normal message from parsing as media.
 */
object MediaMessages {
    const val PREFIX = "\u001emedia1\n"

    fun encode(
        kind: String,
        mime: String,
        name: String,
        size: Long,
        attachmentId: String,
        key: ByteArray,
        nonce: ByteArray,
        digestHex: String,
    ): String {
        val wire = MediaEnvelopeWire(
            v = 1,
            kind = kind,
            mime = mime,
            name = sanitizeMediaName(name),
            size = size,
            id = attachmentId,
            // Basic encoder keeps '=' padding and does not insert line breaks.
            key = Base64.getEncoder().encodeToString(key),
            nonce = Base64.getEncoder().encodeToString(nonce),
            digest = digestHex,
        )
        return PREFIX + json.encodeToString(MediaEnvelopeWire.serializer(), wire)
    }

    /** Null when [PREFIX] is absent or the JSON fields cannot be read. */
    fun parse(plaintext: String): MediaEnvelope? {
        if (!plaintext.startsWith(PREFIX)) return null
        val wire = try {
            json.decodeFromString(
                MediaEnvelopeWire.serializer(),
                plaintext.substring(PREFIX.length),
            )
        } catch (_: Exception) {
            return null
        }
        if (wire.v != 1 || wire.size < 0) return null
        val key = try {
            Base64.getDecoder().decode(wire.key)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val nonce = try {
            Base64.getDecoder().decode(wire.nonce)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (key.size != 32 || nonce.size != 12) return null
        return MediaEnvelope(
            kind = wire.kind,
            mime = wire.mime,
            name = wire.name,
            size = wire.size,
            attachmentId = wire.id,
            key = key,
            nonce = nonce,
            digestHex = wire.digest,
        )
    }

    fun snippet(plaintext: String): String {
        val media = parse(plaintext) ?: return plaintext
        return when (media.kind) {
            "image" -> "Photo"
            "video" -> "Video"
            "audio" -> "Audio"
            "file" -> media.name.trim().ifBlank { "Document" }
            else -> "Attachment"
        }
    }
}

data class MediaEnvelope(
    val kind: String,
    val mime: String,
    val name: String,
    val size: Long,
    val attachmentId: String,
    val key: ByteArray,
    val nonce: ByteArray,
    val digestHex: String,
)

internal fun sanitizeMediaName(name: String): String {
    val withoutBreaks = name.replace("\r", "").replace("\n", "")
    val slash = withoutBreaks.lastIndexOf('/')
    val backslash = withoutBreaks.lastIndexOf('\\')
    val separator = maxOf(slash, backslash)
    val segment = if (separator >= 0) withoutBreaks.substring(separator + 1) else withoutBreaks
    val trimmed = segment.trim()
    val capped = if (trimmed.length <= 80) trimmed else trimmed.substring(0, 80)
    return capped.ifEmpty { "file" }
}

@Serializable
private data class MediaEnvelopeWire(
    val v: Int,
    val kind: String,
    val mime: String,
    val name: String,
    val size: Long,
    val id: String,
    val key: String,
    val nonce: String,
    val digest: String,
)

private val json = Json { prettyPrint = false }
