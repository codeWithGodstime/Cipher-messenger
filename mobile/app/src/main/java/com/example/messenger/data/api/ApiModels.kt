package com.example.messenger.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtpRequest(val phone: String)

@Serializable
data class VerifyRequest(val phone: String, val code: String)

@Serializable
data class ProfileUpdateRequest(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class ApiErrorBody(
    val error: String,
    val message: String,
    @SerialName("retry_after") val retryAfter: Int = 0,
)

@Serializable
data class OtpPending(
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class Profile(
    val id: String,
    val phone: String,
    @SerialName("display_name") val displayName: String,
    val about: String = "",
    @SerialName("profile_photo") val profilePhoto: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    val created: Boolean,
    val profile: Profile,
)
