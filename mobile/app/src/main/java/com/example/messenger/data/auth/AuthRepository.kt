package com.example.messenger.data.auth

import com.example.messenger.data.api.ApiErrorBody
import com.example.messenger.data.api.AuthRequest
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.ProfileUpdateRequest
import com.example.messenger.data.api.Session
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface AuthCall {
    data class Success(val session: Session) : AuthCall
    data class Failed(val message: String) : AuthCall
}

sealed interface ProfileCall {
    data class Success(val profile: Profile) : ProfileCall
    data class Failed(val message: String) : ProfileCall
}

class AuthRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun login(username: String): AuthCall = call(::authFailed) {
        val response = api.login(AuthRequest(username))
        if (!response.isSuccessful) {
            AuthCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            AuthCall.Success(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    suspend fun signup(username: String): AuthCall = call(::authFailed) {
        val response = api.signup(AuthRequest(username))
        if (!response.isSuccessful) {
            AuthCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            AuthCall.Success(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    suspend fun updateDisplayName(accessToken: String, displayName: String): ProfileCall =
        call(::profileFailed) {
            val response = api.updateProfile(
                authorization = "Bearer $accessToken",
                body = ProfileUpdateRequest(displayName),
            )
            if (!response.isSuccessful) {
                ProfileCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
            } else {
                ProfileCall.Success(json.decodeFromString(response.body()?.string().orEmpty()))
            }
        }

    private suspend fun <T> call(failed: (String) -> T, block: suspend () -> T): T {
        return try {
            block()
        } catch (_: IOException) {
            failed("Can't reach the server. Check the API address and your connection.")
        } catch (_: SerializationException) {
            failed("The server returned a response the app could not read.")
        }
    }
}

private fun authFailed(message: String) = AuthCall.Failed(message)

private fun profileFailed(message: String) = ProfileCall.Failed(message)

internal fun errorMessage(json: Json, code: Int, raw: String?): String {
    val parsed = raw?.let { runCatching { json.decodeFromString<ApiErrorBody>(it) }.getOrNull() }
    if (parsed != null && parsed.message.isNotBlank()) {
        return if (parsed.retryAfter > 0) {
            "${parsed.message} Try again in ${parsed.retryAfter}s."
        } else {
            parsed.message
        }
    }
    return when (code) {
        400 -> "Check the username and try again."
        404 -> "User does not exist."
        409 -> "Username is already taken."
        else -> "Something went wrong."
    }
}
