package com.example.messenger

import com.example.messenger.data.api.ApiContact
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.api.OpenConversationRequest
import com.example.messenger.data.api.Profile
import com.example.messenger.data.api.WsEnvelope
import com.example.messenger.data.chat.MessengerSocket
import com.example.messenger.data.chat.asContactEntry
import com.example.messenger.data.chat.toCipherMessage
import com.example.messenger.data.chat.toThread
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conversationMapsPeerTitleAndSnippet() {
        val conversation = ApiConversation(
            id = "c1",
            peer = Profile(
                id = "u2",
                username = "elena",
                displayName = "Elena",
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            ),
            lastMessage = ApiMessage(
                id = "m1",
                conversationId = "c1",
                senderId = "u2",
                body = "Ship it",
                createdAt = "2026-10-30T10:42:00Z",
            ),
            unread = 2,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-10-30T10:42:00Z",
        )
        val thread = conversation.toThread(currentUserId = "u1")
        assertEquals("c1", thread.id)
        assertEquals("u2", thread.peerId)
        assertEquals("Elena", thread.title)
        assertEquals("Ship it", thread.snippet)
        assertEquals(2, thread.unread)
    }

    @Test
    fun messageOutgoingUsesCurrentUserId() {
        val message = ApiMessage(
            id = "m1",
            conversationId = "c1",
            senderId = "u1",
            body = "Hello",
            createdAt = "2026-10-30T10:42:00Z",
        )
        val cipher = message.toCipherMessage(currentUserId = "u1")
        assertTrue(cipher.outgoing)
        assertEquals("Hello", cipher.body)

        val incoming = message.copy(senderId = "u2").toCipherMessage(currentUserId = "u1")
        assertFalse(incoming.outgoing)
    }

    @Test
    fun wsEnvelopeParsesMessageCreated() {
        val frame = """
            {
              "type": "message.created",
              "message": {
                "id": "m1",
                "conversation_id": "c1",
                "sender_id": "u2",
                "body": "Hi",
                "created_at": "2026-10-30T10:42:00Z"
              }
            }
        """.trimIndent()
        val envelope = json.decodeFromString(WsEnvelope.serializer(), frame)
        assertEquals(MessengerSocket.TYPE_MESSAGE_CREATED, envelope.type)
        assertEquals("Hi", envelope.message?.body)
        assertEquals("c1", envelope.message?.conversationId)
    }

    @Test
    fun wsUrlSwitchesScheme() {
        assertEquals(
            "ws://10.0.2.2:8080/ws",
            MessengerSocket.wsUrlFromApiBase("http://10.0.2.2:8080/"),
        )
        assertEquals(
            "wss://api.example.com/ws",
            MessengerSocket.wsUrlFromApiBase("https://api.example.com"),
        )
    }

    @Test
    fun contactMapsPeerNameAndUsername() {
        val contact = ApiContact(
            id = "ct1",
            peer = Profile(
                id = "u2",
                username = "ada",
                displayName = "Ada",
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            ),
            createdAt = "2026-10-30T08:00:00Z",
        )
        val entry = contact.asContactEntry()
        assertEquals("u2", entry.id)
        assertEquals("ct1", entry.contactId)
        assertEquals("Ada", entry.name)
        assertEquals("@ada", entry.detail)
    }

    @Test
    fun openConversationRequestOmitsNullPeerFields() {
        val byUsername = json.encodeToString(
            OpenConversationRequest.serializer(),
            OpenConversationRequest(username = "ada"),
        )
        assertTrue(byUsername.contains("\"username\":\"ada\""))
        assertFalse(byUsername.contains("user_id"))

        val byId = json.encodeToString(
            OpenConversationRequest.serializer(),
            OpenConversationRequest(userId = "u2"),
        )
        assertTrue(byId.contains("\"user_id\":\"u2\""))
        assertFalse(byId.contains("username"))
        assertNull(OpenConversationRequest().userId)
    }
}
