package com.example.messenger.data.chat

import android.util.Base64
import android.util.Log
import com.example.messenger.crypto.MessageCipher
import com.example.messenger.crypto.RemotePreKeyBundle
import com.example.messenger.crypto.SentPlaintextStore
import com.example.messenger.crypto.signalCiphertextType
import com.example.messenger.data.api.PreKeyBundle
import com.example.messenger.data.keys.KeyRepository
import com.example.messenger.data.keys.PreKeyBundleCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CryptoCall {
    data class Ok(val body: String) : CryptoCall
    data class Failed(val message: String) : CryptoCall
}

/**
 * Turns plaintext into the ciphertext stored by the server, and back.
 * Sends and decrypts share one lock so two messages cannot advance the same session at once.
 */
class ChatCrypto(
    private val cipher: MessageCipher,
    private val sent: SentPlaintextStore,
    private val keys: KeyRepository,
) {
    private val gate = Mutex()

    suspend fun encrypt(
        localUserId: String,
        accessToken: String,
        peerUserId: String,
        plaintext: String,
    ): CryptoCall = gate.withLock {
        withContext(Dispatchers.IO) {
            if (plaintext.length > MAX_PLAINTEXT_CHARS) {
                return@withContext CryptoCall.Failed("Message is too long.")
            }
            if (!cipher.hasIdentity()) {
                return@withContext CryptoCall.Failed("This device has no identity key. Sign in again.")
            }
            try {
                if (!cipher.hasSession(peerUserId)) {
                    when (val claimed = keys.claimPreKeyBundle(accessToken, peerUserId)) {
                        is PreKeyBundleCall.Failed -> return@withContext CryptoCall.Failed(claimed.message)
                        is PreKeyBundleCall.Success -> cipher.establish(
                            localUserId,
                            peerUserId,
                            claimed.bundle.toRemoteBundle(),
                        )
                    }
                }
                val ciphertext = cipher.encrypt(localUserId, peerUserId, plaintext)
                sent.remember(ciphertext, plaintext)
                CryptoCall.Ok(ciphertext)
            } catch (error: Exception) {
                Log.w(TAG, "encrypt failed", error)
                CryptoCall.Failed("Couldn't encrypt this message.")
            }
        }
    }

    suspend fun reveal(
        localUserId: String,
        peerUserId: String,
        body: String,
        outgoing: Boolean,
    ): String = gate.withLock {
        withContext(Dispatchers.IO) {
            if (outgoing) {
                sent.find(body)?.let { return@withContext it }
            }
            val bytes = runCatching { Base64.decode(body, Base64.NO_WRAP) }.getOrNull()
            if (bytes == null || signalCiphertextType(bytes) == null) return@withContext body
            if (outgoing || peerUserId.isBlank() || localUserId.isBlank()) {
                return@withContext ENCRYPTED_PLACEHOLDER
            }
            try {
                cipher.decrypt(localUserId, peerUserId, body)
            } catch (error: Exception) {
                Log.w(TAG, "decrypt failed", error)
                UNREADABLE_PLACEHOLDER
            }
        }
    }

    private fun PreKeyBundle.toRemoteBundle(): RemotePreKeyBundle {
        val oneTime = preKey
        return RemotePreKeyBundle(
            identityKey = decode(identityKey),
            signedPreKeyId = signedPreKey.keyId,
            signedPreKeyPublic = decode(signedPreKey.publicKey),
            signedPreKeySignature = decode(signedPreKey.signature),
            oneTimePreKeyId = oneTime?.keyId,
            oneTimePreKeyPublic = oneTime?.let { decode(it.publicKey) },
            kyberPreKeyId = kyberPreKey.keyId,
            kyberPreKeyPublic = decode(kyberPreKey.publicKey),
            kyberPreKeySignature = decode(kyberPreKey.signature),
        )
    }

    private fun decode(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)

    private companion object {
        const val TAG = "ChatCrypto"
        const val MAX_PLAINTEXT_CHARS = 4000
        const val ENCRYPTED_PLACEHOLDER = "Encrypted message"
        const val UNREADABLE_PLACEHOLDER = "Couldn't decrypt this message."
    }
}
