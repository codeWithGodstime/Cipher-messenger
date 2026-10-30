package com.example.messenger.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.chat.ChatCall
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.MessengerSocket
import com.example.messenger.data.chat.asContact
import com.example.messenger.data.chat.formatChatTime
import com.example.messenger.data.chat.toCipherMessage
import com.example.messenger.data.chat.toThread
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sessionStore: SessionStore,
    private val repository: ChatRepository,
    private val socket: MessengerSocket,
) : ViewModel() {
    var threads by mutableStateOf<List<ChatThread>>(emptyList())
        private set
    var messages by mutableStateOf<Map<String, List<CipherMessage>>>(emptyMap())
        private set
    var listLoading by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var activeConversationId by mutableStateOf<String?>(null)
        private set

    private var userId: String = sessionStore.userId.orEmpty()
    private val loadedConversations = mutableSetOf<String>()
    private val localPins = mutableMapOf<String, Boolean>()

    val peerContacts: List<ContactEntry>
        get() = threads
            .filter { it.peerId.isNotBlank() }
            .distinctBy { it.peerId }
            .map { it.asContact() }

    init {
        socket.start(viewModelScope)
        viewModelScope.launch {
            socket.incoming.collect(::onIncomingMessage)
        }
        viewModelScope.launch {
            socket.errors.collect { code ->
                notice = when (code) {
                    "validation_error" -> "Message could not be sent."
                    "send_failed" -> "Message failed to send."
                    else -> "Connection issue ($code)."
                }
            }
        }
        viewModelScope.launch {
            ensureUserId()
            refreshConversations()
        }
    }

    override fun onCleared() {
        socket.stop()
        super.onCleared()
    }

    fun consumeNotice() {
        notice = null
    }

    fun consumeListError() {
        listError = null
    }

    fun refreshConversations() {
        val token = sessionStore.accessToken ?: run {
            listError = "Sign in again to load chats."
            return
        }
        viewModelScope.launch {
            listLoading = threads.isEmpty()
            listError = null
            when (val result = repository.listConversations(token)) {
                is ChatCall.Ok -> {
                    threads = mergePins(result.value.map { it.toThread(userId) })
                    listLoading = false
                }
                is ChatCall.Failed -> {
                    listLoading = false
                    listError = result.message
                }
            }
        }
    }

    fun openConversation(conversationId: String) {
        activeConversationId = conversationId
        threads = threads.map { thread ->
            if (thread.id == conversationId) thread.copy(unread = 0) else thread
        }
        if (conversationId in loadedConversations && messages.containsKey(conversationId)) {
            return
        }
        loadMessages(conversationId)
    }

    fun leaveConversation() {
        activeConversationId = null
    }

    fun sendMessage(conversationId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val token = sessionStore.accessToken ?: run {
            notice = "Sign in again to send messages."
            return
        }
        viewModelScope.launch {
            if (socket.sendMessage(conversationId, trimmed)) {
                return@launch
            }
            when (val result = repository.sendMessage(token, conversationId, trimmed)) {
                is ChatCall.Ok -> onIncomingMessage(result.value)
                is ChatCall.Failed -> notice = result.message
            }
        }
    }

    fun togglePin(conversationId: String) {
        val current = threads.firstOrNull { it.id == conversationId } ?: return
        val pinned = !current.pinned
        localPins[conversationId] = pinned
        threads = threads.map { thread ->
            if (thread.id == conversationId) {
                thread.copy(pinned = pinned, inRecent = thread.inRecent || !pinned)
            } else {
                thread
            }
        }
    }

    fun startChatWithPeer(peerUserId: String, onOpened: (String) -> Unit) {
        val peer = peerUserId.trim()
        if (peer.isEmpty()) {
            notice = "Enter a user id to start a chat."
            return
        }
        threads.firstOrNull { it.peerId == peer }?.let { existing ->
            onOpened(existing.id)
            return
        }
        val token = sessionStore.accessToken ?: run {
            notice = "Sign in again to start a chat."
            return
        }
        viewModelScope.launch {
            when (val result = repository.openConversation(token, peer)) {
                is ChatCall.Ok -> {
                    upsertConversation(result.value)
                    onOpened(result.value.id)
                }
                is ChatCall.Failed -> notice = result.message
            }
        }
    }

    private fun loadMessages(conversationId: String) {
        val token = sessionStore.accessToken ?: return
        viewModelScope.launch {
            when (val result = repository.listMessages(token, conversationId)) {
                is ChatCall.Ok -> {
                    // API returns newest first; UI wants oldest → newest.
                    val ordered = result.value.asReversed().map { it.toCipherMessage(userId) }
                    messages = messages + (conversationId to ordered)
                    loadedConversations += conversationId
                }
                is ChatCall.Failed -> notice = result.message
            }
        }
    }

    private fun onIncomingMessage(message: ApiMessage) {
        val cipher = message.toCipherMessage(userId)
        val existing = messages[message.conversationId].orEmpty()
        if (existing.any { it.id == cipher.id }) {
            updateThreadFromMessage(message, bumpUnread = false)
            return
        }
        messages = messages + (message.conversationId to (existing + cipher))
        loadedConversations += message.conversationId
        val viewing = activeConversationId == message.conversationId
        updateThreadFromMessage(message, bumpUnread = !viewing && !cipher.outgoing)
        if (threads.none { it.id == message.conversationId }) {
            refreshConversations()
        }
    }

    private fun updateThreadFromMessage(message: ApiMessage, bumpUnread: Boolean) {
        val time = formatChatTime(message.createdAt)
        threads = threads.map { thread ->
            if (thread.id != message.conversationId) return@map thread
            thread.copy(
                snippet = message.body,
                timeLabel = time,
                voice = false,
                unread = if (bumpUnread) thread.unread + 1 else 0,
                receipt = if (message.senderId == userId) Receipt.Sent else thread.receipt,
                inRecent = true,
            )
        }
    }

    private fun upsertConversation(conversation: ApiConversation) {
        val mapped = conversation.toThread(userId).let { thread ->
            val pinned = localPins[thread.id] ?: thread.pinned
            thread.copy(pinned = pinned)
        }
        threads = listOf(mapped) + threads.filterNot { it.id == mapped.id }
    }

    private fun mergePins(remote: List<ChatThread>): List<ChatThread> {
        return remote.map { thread ->
            val pinned = localPins[thread.id] ?: thread.pinned
            thread.copy(pinned = pinned)
        }
    }

    private suspend fun ensureUserId() {
        if (userId.isNotBlank()) return
        val token = sessionStore.accessToken ?: return
        when (val result = repository.getProfile(token)) {
            is ChatCall.Ok -> {
                sessionStore.rememberProfile(result.value)
                userId = result.value.id
            }
            is ChatCall.Failed -> listError = result.message
        }
    }

}

class ChatViewModelFactory(
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                sessionStore = sessionStore,
                repository = ApiClient.chatRepository,
                socket = ApiClient.messengerSocket(sessionStore),
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
