package com.example.messenger.data.auth

import com.example.messenger.data.api.ApiErrorBody
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.api.OtpPending
import com.example.messenger.data.api.OtpRequest
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.ProfileUpdateRequest
import com.example.messenger.data.api.Session
import com.example.messenger.data.api.VerifyRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException

sealed interface OtpCall {
    data class CodeSent(val expiresInSeconds: Int) : OtpCall
    data class SignedIn(val session: Session) : OtpCall
    data class Failed(val message: String) : OtpCall
}

sealed interface VerifyCall {
    data class Success(val session: Session) : VerifyCall
    data class Failed(val message: String) : VerifyCall
}

sealed interface ProfileCall {
    data class Success(val profile: Profile) : ProfileCall
    data class Failed(val message: String) : ProfileCall
}

class AuthRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun requestOtp(phone: String): OtpCall = call(::otpFailed) {
        val response = api.requestOtp(OtpRequest(phone))
        if (!response.isSuccessful) {
            OtpCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            parseOtpSuccess(json, response.body()?.string().orEmpty())
        }
    }

    suspend fun verify(phone: String, code: String): VerifyCall = call(::verifyFailed) {
        val response = api.verify(VerifyRequest(phone, code))
        if (!response.isSuccessful) {
            VerifyCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            VerifyCall.Success(json.decodeFromString(response.body()?.string().orEmpty()))
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

private fun otpFailed(message: String) = OtpCall.Failed(message)

private fun verifyFailed(message: String) = VerifyCall.Failed(message)

private fun profileFailed(message: String) = ProfileCall.Failed(message)

internal fun parseOtpSuccess(json: Json, body: String): OtpCall {
    val payload = json.parseToJsonElement(body).jsonObject
    return if ("access_token" in payload) {
        OtpCall.SignedIn(json.decodeFromJsonElement(Session.serializer(), payload))
    } else {
        OtpCall.CodeSent(json.decodeFromJsonElement(OtpPending.serializer(), payload).expiresIn)
    }
}

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
        400 -> "Check the phone number and try again."
        401 -> "That code is invalid or expired."
        429 -> "Too many code requests. Try again later."
        503 -> "Code delivery is not available."
        else -> "Something went wrong."
    }
}
