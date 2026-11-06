package com.example.messenger.data.keys

import android.util.Log
import com.example.messenger.crypto.LocalPreKeyStore
import com.example.messenger.crypto.ONE_TIME_PRE_KEY_LOW_WATER
import com.example.messenger.data.api.OneTimePreKeyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks the server how many one-time prekeys remain and uploads a new batch
 * when the pool is below [ONE_TIME_PRE_KEY_LOW_WATER]. A batch saved locally
 * but not yet accepted is uploaded again before a new one is generated.
 */
class OneTimePreKeyRefresh(
    private val preKeys: LocalPreKeyStore,
    private val keys: KeyRepository,
) {
    suspend fun refresh(username: String, accessToken: String) {
        if (username.isBlank() || accessToken.isBlank()) return
        val pending = withContext(Dispatchers.IO) {
            runCatching { preKeys.pendingOneTime(username) }.getOrNull()
        }
        val batch = if (pending != null) {
            pending
        } else {
            val published = withContext(Dispatchers.IO) {
                runCatching { preKeys.isPublished(username) }.getOrDefault(false)
            }
            if (!published) return
            val count = when (val result = keys.oneTimePreKeyCount(accessToken)) {
                is OneTimePreKeyCountCall.Success -> result.count
                is OneTimePreKeyCountCall.Failed -> {
                    Log.w(TAG, result.message)
                    return
                }
            }
            if (count >= ONE_TIME_PRE_KEY_LOW_WATER) return
            val staged = withContext(Dispatchers.IO) {
                runCatching { preKeys.stageBatch(username) }
                    .onFailure { error -> Log.w(TAG, "one-time prekey generation failed", error) }
                    .getOrNull()
            } ?: return
            staged
        }
        if (batch.isEmpty()) return
        val uploaded = keys.publishOneTimePreKeys(
            accessToken,
            batch.map { OneTimePreKeyRequest(it.keyId, it.publicKey) },
        )
        when (uploaded) {
            KeyCall.Ok -> confirm(username)
            is KeyCall.Failed -> {
                if (uploaded.status == HTTP_CONFLICT) {
                    val discarded = withContext(Dispatchers.IO) {
                        runCatching { preKeys.discardPending(username) }.isSuccess
                    }
                    if (!discarded) {
                        Log.w(TAG, "Couldn't drop one-time prekey ids the server rejected.")
                    }
                } else {
                    Log.w(TAG, uploaded.message)
                }
            }
        }
    }

    private suspend fun confirm(username: String) {
        val saved = withContext(Dispatchers.IO) {
            runCatching { preKeys.confirmUpload(username) }.isSuccess
        }
        if (!saved) Log.w(TAG, "Couldn't record that one-time prekeys were uploaded.")
    }

    private companion object {
        const val TAG = "PreKeys"
        const val HTTP_CONFLICT = 409
    }
}
