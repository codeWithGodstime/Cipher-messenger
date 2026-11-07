package com.example.messenger.data.chat

import com.example.messenger.data.api.AttachmentStored
import com.example.messenger.data.api.MessengerApi
import com.example.messenger.data.auth.errorMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MediaRepository(
    private val api: MessengerApi,
    private val json: Json,
) {
    suspend fun upload(
        accessToken: String,
        conversationId: String,
        attachmentId: String,
        ciphertext: ByteArray,
    ): ChatCall<AttachmentStored> = call {
        val response = api.uploadAttachment(
            authorization = bearer(accessToken),
            conversationId = conversationId,
            attachmentId = attachmentId,
            body = ciphertext.toRequestBody("application/octet-stream".toMediaType()),
        )
        if (response.code() != 200 && response.code() != 201) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            ChatCall.Ok(
                json.decodeFromString(
                    AttachmentStored.serializer(),
                    response.body()?.string().orEmpty(),
                ),
            )
        }
    }

    suspend fun download(
        accessToken: String,
        conversationId: String,
        attachmentId: String,
    ): ChatCall<ByteArray> = call {
        val response = api.downloadAttachment(
            authorization = bearer(accessToken),
            conversationId = conversationId,
            attachmentId = attachmentId,
        )
        if (response.code() != 200 && response.code() != 201) {
            ChatCall.Failed(errorMessage(json, response.code(), response.errorBody()?.string()))
        } else {
            val bytes = response.body()?.bytes()
            if (bytes == null) {
                ChatCall.Failed("The server returned a response the app could not read.")
            } else {
                ChatCall.Ok(bytes)
            }
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
