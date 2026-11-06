package com.example.messenger.data.keys

import com.example.messenger.data.api.CreateIdentityKeyRequest
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.api.OneTimePreKeyCount
import com.example.messenger.data.api.OneTimePreKeyRequest
import com.example.messenger.data.api.PreKeyBundle
import com.example.messenger.data.api.UploadKyberPreKeyRequest
import com.example.messenger.data.api.UploadOneTimePreKeysRequest
import com.example.messenger.data.api.UploadSignedPreKeyRequest
import com.example.messenger.data.auth.errorMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

sealed interface KeyCall {
    data object Ok : KeyCall
    data class Failed(val message: String, val status: Int = 0) : KeyCall
}

sealed interface OneTimePreKeyCountCall {
    data class Success(val count: Int) : OneTimePreKeyCountCall
    data class Failed(val message: String) : OneTimePreKeyCountCall
}

sealed interface PreKeyBundleCall {
    data class Success(val bundle: PreKeyBundle) : PreKeyBundleCall
    data class Failed(val message: String) : PreKeyBundleCall
}

class KeyRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun publishIdentityKey(accessToken: String, publicKey: String): KeyCall {
        return publish {
            api.createIdentityKey(
                authorization = bearer(accessToken),
                body = CreateIdentityKeyRequest(publicKey),
            )
        }
    }

    suspend fun publishSignedPreKey(
        accessToken: String,
        keyId: Int,
        publicKey: String,
        signature: String,
    ): KeyCall {
        return publish {
            api.uploadSignedPreKey(
                authorization = bearer(accessToken),
                body = UploadSignedPreKeyRequest(
                    keyId = keyId,
                    publicKey = publicKey,
                    signature = signature,
                ),
            )
        }
    }

    suspend fun publishKyberPreKey(
        accessToken: String,
        keyId: Int,
        publicKey: String,
        signature: String,
    ): KeyCall {
        return publish {
            api.uploadKyberPreKey(
                authorization = bearer(accessToken),
                body = UploadKyberPreKeyRequest(
                    keyId = keyId,
                    publicKey = publicKey,
                    signature = signature,
                ),
            )
        }
    }

    suspend fun publishOneTimePreKeys(
        accessToken: String,
        preKeys: List<OneTimePreKeyRequest>,
    ): KeyCall {
        return publish {
            api.uploadOneTimePreKeys(
                authorization = bearer(accessToken),
                body = UploadOneTimePreKeysRequest(preKeys),
            )
        }
    }

    /** How many one-time prekeys the server still holds for the signed-in user. */
    suspend fun oneTimePreKeyCount(accessToken: String): OneTimePreKeyCountCall {
        return try {
            val response = api.oneTimePreKeyCount(authorization = bearer(accessToken))
            if (!response.isSuccessful) {
                OneTimePreKeyCountCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
            } else {
                val count = json.decodeFromString<OneTimePreKeyCount>(
                    response.body()?.string().orEmpty(),
                )
                OneTimePreKeyCountCall.Success(count.count)
            }
        } catch (_: IOException) {
            OneTimePreKeyCountCall.Failed("Can't reach the server. Check the API address and your connection.")
        } catch (_: SerializationException) {
            OneTimePreKeyCountCall.Failed("The server returned a response the app could not read.")
        }
    }

    /**
     * Claims the peer's identity key, signed prekey, and one one-time prekey.
     * The server removes that one-time prekey, so call this once per new session.
     */
    suspend fun claimPreKeyBundle(accessToken: String, userId: String): PreKeyBundleCall {
        return try {
            val response = api.claimPreKeyBundle(
                authorization = bearer(accessToken),
                userId = userId,
            )
            if (!response.isSuccessful) {
                PreKeyBundleCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
            } else {
                PreKeyBundleCall.Success(json.decodeFromString(response.body()?.string().orEmpty()))
            }
        } catch (_: IOException) {
            PreKeyBundleCall.Failed("Can't reach the server. Check the API address and your connection.")
        } catch (_: SerializationException) {
            PreKeyBundleCall.Failed("The server returned a response the app could not read.")
        }
    }

    private suspend fun publish(call: suspend () -> Response<ResponseBody>): KeyCall {
        return try {
            val response = call()
            if (!response.isSuccessful) {
                KeyCall.Failed(
                    message = errorMessage(json, response.code(), response.errorBody()?.string()),
                    status = response.code(),
                )
            } else {
                KeyCall.Ok
            }
        } catch (_: IOException) {
            KeyCall.Failed("Can't reach the server. Check the API address and your connection.")
        }
    }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"
}
