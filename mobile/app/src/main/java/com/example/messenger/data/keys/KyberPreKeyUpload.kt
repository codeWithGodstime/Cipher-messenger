package com.example.messenger.data.keys

import android.util.Log
import com.example.messenger.crypto.IdentityStore
import com.example.messenger.crypto.LocalPreKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads the device Kyber prekey when the server does not have it yet.
 * A session cannot start until this key is published.
 */
class KyberPreKeyUpload(
    private val identity: IdentityStore,
    private val preKeys: LocalPreKeyStore,
    private val keys: KeyRepository,
) {
    /**
     * Null when there is nothing to upload.
     * [KeyCall.Ok] when this call stored the key.
     */
    suspend fun publishIfNeeded(username: String, accessToken: String): KeyCall? {
        if (username.isBlank() || accessToken.isBlank()) return null
        val identityKey = withContext(Dispatchers.IO) {
            runCatching { identity.find(username) }.getOrNull()
        } ?: return null
        val pending = withContext(Dispatchers.IO) {
            runCatching { preKeys.prepareKyber(username, identityKey) }
        }.getOrElse { error ->
            Log.w(TAG, "Kyber prekey generation failed", error)
            return KeyCall.Failed("Couldn't create your Kyber prekey on this device.")
        } ?: return null
        return when (val uploaded = keys.publishKyberPreKey(
            accessToken,
            pending.keyId,
            pending.publicKey,
            pending.signature,
        )) {
            KeyCall.Ok -> {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { preKeys.confirmKyber(username) }.isSuccess
                }
                if (!saved) {
                    KeyCall.Failed("Couldn't store your Kyber prekey on this device.")
                } else {
                    KeyCall.Ok
                }
            }
            is KeyCall.Failed -> uploaded
        }
    }

    private companion object {
        const val TAG = "KyberPreKey"
    }
}
