package com.example.messenger.data.keys

import com.example.messenger.data.api.CreateIdentityKeyRequest
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.auth.errorMessage
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface KeyCall {
    data object Ok : KeyCall
    data class Failed(val message: String) : KeyCall
}

class KeyRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun publishIdentityKey(accessToken: String, publicKey: String): KeyCall {
        return try {
            val response = api.createIdentityKey(
                authorization = "Bearer $accessToken",
                body = CreateIdentityKeyRequest(publicKey),
            )
            if (!response.isSuccessful) {
                KeyCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
            } else {
                KeyCall.Ok
            }
        } catch (_: IOException) {
            KeyCall.Failed("Can't reach the server. Check the API address and your connection.")
        }
    }
}
