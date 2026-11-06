package com.example.messenger.crypto

import android.util.Base64
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

/**
 * Public keys used to open one Signal session.
 * [oneTimePreKeyId] is null when the peer has no one-time prekey left.
 */
class RemotePreKeyBundle(
    val identityKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyId: Int?,
    val oneTimePreKeyPublic: ByteArray?,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: ByteArray,
    val kyberPreKeySignature: ByteArray,
)

/**
 * Encrypts and decrypts 1:1 messages for one account.
 * Callers serialize access. A session is opened from [RemotePreKeyBundle] once,
 * then later messages use the stored ratchet.
 */
class MessageCipher(
    private val store: DeviceProtocolStore,
) {
    fun hasIdentity(): Boolean = store.hasIdentity()

    fun hasSession(peerUserId: String): Boolean = store.containsSession(signalAddress(peerUserId))

    fun establish(localUserId: String, peerUserId: String, remote: RemotePreKeyBundle) {
        val bundle = PreKeyBundle(
            SIGNAL_REGISTRATION_ID,
            SIGNAL_DEVICE_ID,
            remote.oneTimePreKeyId ?: PreKeyBundle.NULL_PRE_KEY_ID,
            remote.oneTimePreKeyPublic?.let { ECPublicKey(it) },
            remote.signedPreKeyId,
            ECPublicKey(remote.signedPreKeyPublic),
            remote.signedPreKeySignature,
            IdentityKey(remote.identityKey),
            remote.kyberPreKeyId,
            KEMPublicKey(remote.kyberPreKeyPublic),
            remote.kyberPreKeySignature,
        )
        SessionBuilder(store, signalAddress(peerUserId), signalAddress(localUserId)).process(bundle)
    }

    fun encrypt(localUserId: String, peerUserId: String, plaintext: String): String {
        val cipher = SessionCipher(store, signalAddress(localUserId), signalAddress(peerUserId))
        val padded = padPlaintext(plaintext.toByteArray(Charsets.UTF_8))
        val message = cipher.encrypt(padded)
        return Base64.encodeToString(message.serialize(), Base64.NO_WRAP)
    }

    fun decrypt(localUserId: String, peerUserId: String, ciphertext: String): String {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        val cipher = SessionCipher(store, signalAddress(localUserId), signalAddress(peerUserId))
        val padded = when (signalCiphertextType(bytes)) {
            SIGNAL_PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(bytes))
            SIGNAL_WHISPER_TYPE -> cipher.decrypt(SignalMessage(bytes))
            else -> error("Not a Signal ciphertext")
        }
        return unpadPlaintext(padded).toString(Charsets.UTF_8)
    }
}
