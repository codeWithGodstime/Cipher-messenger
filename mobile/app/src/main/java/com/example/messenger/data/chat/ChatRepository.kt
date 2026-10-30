package com.example.messenger.data.chat

import com.example.messenger.data.api.AddContactRequest
import com.example.messenger.data.api.ApiContact
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.api.ContactList
import com.example.messenger.data.api.ConversationList
import com.example.messenger.data.api.MessageList
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.api.OpenConversationRequest
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.SendMessageRequest
import com.example.messenger.data.auth.errorMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface ChatCall<out T> {
    data class Ok<T>(val value: T) : ChatCall<T>
    data class Failed(val message: String) : ChatCall<Nothing>
}

class ChatRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun getProfile(accessToken: String): ChatCall<Profile> = call {
        val response = api.getProfile(bearer(accessToken))
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    suspend fun listConversations(accessToken: String): ChatCall<List<ApiConversation>> = call {
        val response = api.listConversations(bearer(accessToken))
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            val body = json.decodeFromString<ConversationList>(response.body()?.string().orEmpty())
            ChatCall.Ok(body.conversations)
        }
    }

    suspend fun listContacts(accessToken: String): ChatCall<List<ApiContact>> = call {
        val response = api.listContacts(bearer(accessToken))
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            val body = json.decodeFromString<ContactList>(response.body()?.string().orEmpty())
            ChatCall.Ok(body.contacts)
        }
    }

    suspend fun addContact(
        accessToken: String,
        peerUserId: String? = null,
        username: String? = null,
    ): ChatCall<ApiContact> = call {
        val response = api.addContact(
            authorization = bearer(accessToken),
            body = AddContactRequest(userId = peerUserId, username = username),
        )
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    suspend fun deleteContact(accessToken: String, contactId: String): ChatCall<Unit> = call {
        val response = api.deleteContact(bearer(accessToken), contactId)
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(Unit)
        }
    }

    suspend fun lookupUser(accessToken: String, username: String): ChatCall<Profile> = call {
        val response = api.lookupUser(bearer(accessToken), username)
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    suspend fun openConversation(
        accessToken: String,
        peerUserId: String? = null,
        username: String? = null,
    ): ChatCall<ApiConversation> =
        call {
            val response = api.openConversation(
                authorization = bearer(accessToken),
                body = OpenConversationRequest(userId = peerUserId, username = username),
            )
            if (!response.isSuccessful) {
                ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
            } else {
                ChatCall.Ok(json.decodeFromString(response.body()?.string().orEmpty()))
            }
        }

    suspend fun listMessages(
        accessToken: String,
        conversationId: String,
        before: String? = null,
        limit: Int = 50,
    ): ChatCall<List<ApiMessage>> = call {
        val response = api.listMessages(
            authorization = bearer(accessToken),
            conversationId = conversationId,
            before = before,
            limit = limit,
        )
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            val body = json.decodeFromString<MessageList>(response.body()?.string().orEmpty())
            ChatCall.Ok(body.messages)
        }
    }

    suspend fun sendMessage(
        accessToken: String,
        conversationId: String,
        body: String,
    ): ChatCall<ApiMessage> = call {
        val response = api.sendMessage(
            authorization = bearer(accessToken),
            conversationId = conversationId,
            body = SendMessageRequest(body),
        )
        if (!response.isSuccessful) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(json.decodeFromString(response.body()?.string().orEmpty()))
        }
    }

    private suspend fun <T> call(block: suspend () -> ChatCall<T>): ChatCall<T> {
        return try {
            block()
        } catch (_: IOException) {
            ChatCall.Failed("Can't reach the server. Check the API address and your connection.")
        } catch (_: SerializationException) {
            ChatCall.Failed("The server returned a response the app could not read.")
        }
    }

    private fun bearer(accessToken: String) = "Bearer $accessToken"
}
