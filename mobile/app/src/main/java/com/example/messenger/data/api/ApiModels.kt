package com.example.messenger.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val username: String)

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
data class Profile(
    val id: String,
    val username: String,
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

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class ApiMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ApiConversation(
    val id: String,
    val peer: Profile,
    @SerialName("last_message") val lastMessage: ApiMessage? = null,
    val unread: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ConversationList(
    val conversations: List<ApiConversation>,
)

@Serializable
data class OpenConversationRequest(
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
)

@Serializable
data class AddContactRequest(
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
)

@Serializable
data class ApiContact(
    val id: String,
    val peer: Profile,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ContactList(
    val contacts: List<ApiContact>,
)

@Serializable
data class MessageList(
    val messages: List<ApiMessage>,
)

@Serializable
data class SendMessageRequest(
    val body: String,
)

@Serializable
data class CreateIdentityKeyRequest(
    @SerialName("public_key") val publicKey: String,
)

@Serializable
data class WsUserHit(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
)

@Serializable
data class WsEnvelope(
    val type: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    val body: String? = null,
    val message: ApiMessage? = null,
    val error: String? = null,
    val query: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val users: List<WsUserHit>? = null,
)
