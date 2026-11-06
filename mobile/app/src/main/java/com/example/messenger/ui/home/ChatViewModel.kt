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
import com.example.messenger.data.api.WsUserHit
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenRefresher
import com.example.messenger.data.chat.ChatCall
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.MessengerSocket
import com.example.messenger.data.chat.UserSearchFrame
import com.example.messenger.data.chat.acceptUserSearch
import com.example.messenger.data.chat.asContact
import com.example.messenger.data.chat.asContactEntry
import com.example.messenger.data.chat.formatChatTime
import com.example.messenger.data.chat.normalizeUsernameQuery
import com.example.messenger.data.chat.toCipherMessage
import com.example.messenger.data.chat.toThread
import com.example.messenger.data.chat.toneFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ChatViewModel(
    private val sessionStore: SessionStore,
    private val tokens: TokenRefresher,
    private val authRepository: AuthRepository,
    private val repository: ChatRepository,
    private val socket: MessengerSocket,
) : ViewModel() {
    var threads by mutableStateOf<List<ChatThread>>(emptyList())
        private set
    var messages by mutableStateOf<Map<String, List<CipherMessage>>>(emptyMap())
        private set
    var savedContacts by mutableStateOf<List<ContactEntry>>(emptyList())
        private set
    var listLoading by mutableStateOf(false)
        private set
    var contactsLoading by mutableStateOf(false)
        private set
    var connectBusy by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var activeConversationId by mutableStateOf<String?>(null)
        private set
    var loggingOut by mutableStateOf(false)
        private set
    var usernameResults by mutableStateOf<List<ContactEntry>>(emptyList())
        private set
    var usernameSearchLoading by mutableStateOf(false)
        private set
    var usernameSearchError by mutableStateOf<String?>(null)
        private set

    private val loggedOutEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val loggedOut: SharedFlow<Unit> = loggedOutEvents.asSharedFlow()

    private val logoutJobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var userId: String = sessionStore.userId.orEmpty()
    private val usernameQueries = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var pendingUsernameQuery = ""
    private var searchGeneration = 0
    private var latestSearchId = ""
    private val loadedConversations = mutableSetOf<String>()
    private val localPins = mutableMapOf<String, Boolean>()

    val peerContacts: List<ContactEntry>
        get() {
            val savedIds = savedContacts.map { it.id }.toSet()
            return threads
                .filter { it.peerId.isNotBlank() && it.peerId !in savedIds }
                .distinctBy { it.peerId }
                .map { it.asContact() }
        }

    init {
        tokens.start(viewModelScope)
        socket.start(viewModelScope)
        viewModelScope.launch {
            socket.incoming.collect(::onIncomingMessage)
        }
        viewModelScope.launch {
            socket.userSearch.collect(::onUserSearch)
        }
        viewModelScope.launch {
            usernameQueries.debounce(300.milliseconds).collect { query ->
                if (query != pendingUsernameQuery) return@collect
                val id = nextSearchId()
                latestSearchId = id
                usernameResults = emptyList()
                usernameSearchError = null
                usernameSearchLoading = true
                if (!socket.sendUserSearch(query, id)) {
                    if (latestSearchId != id) return@collect
                    usernameSearchLoading = false
                    usernameSearchError = "Can't search right now. Check your connection."
                }
            }
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
            refreshContacts()
        }
    }

    override fun onCleared() {
        tokens.stop()
        socket.stop()
        super.onCleared()
    }

    fun consumeNotice() {
        notice = null
    }

    fun consumeListError() {
        listError = null
    }

    fun logout() {
        if (loggingOut) return
        loggingOut = true
        val token = sessionStore.accessToken
        socket.stop()
        tokens.signOut()
        loggedOutEvents.tryEmit(Unit)
        if (!token.isNullOrBlank()) {
            logoutJobs.launch { authRepository.logout(token) }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                listError = "Sign in again to load chats."
                return@launch
            }
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
        viewModelScope.launch {
            if (socket.sendMessage(conversationId, trimmed)) {
                return@launch
            }
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to send messages."
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

    fun onUsernameQuery(raw: String) {
        val query = normalizeUsernameQuery(raw)
        pendingUsernameQuery = query
        if (query.isEmpty()) {
            val id = nextSearchId()
            latestSearchId = id
            usernameResults = emptyList()
            usernameSearchLoading = false
            usernameSearchError = null
            socket.sendUserSearch("", id)
            return
        }
        usernameResults = emptyList()
        usernameSearchLoading = true
        usernameSearchError = null
        usernameQueries.tryEmit(query)
    }

    fun clearUsernameSearch() {
        pendingUsernameQuery = ""
        latestSearchId = ""
        usernameResults = emptyList()
        usernameSearchLoading = false
        usernameSearchError = null
    }

    fun refreshContacts() {
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
            contactsLoading = savedContacts.isEmpty()
            when (val result = repository.listContacts(token)) {
                is ChatCall.Ok -> {
                    savedContacts = result.value.map { it.asContactEntry() }
                    contactsLoading = false
                }
                is ChatCall.Failed -> {
                    contactsLoading = false
                    notice = result.message
                }
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
        openConversation(peerUserId = peer, username = null, onOpened = onOpened)
    }

    fun startChatWithUsername(rawUsername: String, onOpened: (String) -> Unit) {
        val username = rawUsername.trim().lowercase().removePrefix("@")
        if (username.isEmpty()) {
            notice = "Enter a username to start a chat."
            return
        }
        openConversation(peerUserId = null, username = username, onOpened = onOpened)
    }

    fun addContactByUsername(
        rawUsername: String,
        startChat: Boolean,
        onOpened: (String) -> Unit,
        onSaved: (() -> Unit)? = null,
    ) {
        val username = rawUsername.trim().lowercase().removePrefix("@")
        if (username.isEmpty()) {
            notice = "Enter a username to add a contact."
            return
        }
        if (connectBusy) return
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to add a contact."
                return@launch
            }
            connectBusy = true
            when (val result = repository.addContact(token, username = username)) {
                is ChatCall.Ok -> {
                    val entry = result.value.asContactEntry()
                    savedContacts = listOf(entry) + savedContacts.filterNot { it.id == entry.id }
                    connectBusy = false
                    if (startChat) {
                        openConversation(peerUserId = entry.id, username = null, onOpened = onOpened)
                    } else {
                        notice = "Added ${entry.name}."
                        onSaved?.invoke()
                    }
                }
                is ChatCall.Failed -> {
                    connectBusy = false
                    notice = result.message
                }
            }
        }
    }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to remove a contact."
                return@launch
            }
            when (val result = repository.deleteContact(token, contactId)) {
                is ChatCall.Ok -> {
                    savedContacts = savedContacts.filterNot { it.contactId == contactId }
                }
                is ChatCall.Failed -> notice = result.message
            }
        }
    }

    private fun openConversation(
        peerUserId: String?,
        username: String?,
        onOpened: (String) -> Unit,
    ) {
        if (connectBusy) return
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to start a chat."
                return@launch
            }
            connectBusy = true
            when (val result = repository.openConversation(token, peerUserId = peerUserId, username = username)) {
                is ChatCall.Ok -> {
                    upsertConversation(result.value)
                    connectBusy = false
                    onOpened(result.value.id)
                }
                is ChatCall.Failed -> {
                    connectBusy = false
                    notice = result.message
                }
            }
        }
    }

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
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

    private fun nextSearchId(): String = (++searchGeneration).toString()

    private fun onUserSearch(frame: UserSearchFrame) {
        if (!acceptUserSearch(frame.requestId, latestSearchId)) return
        if (frame.query != pendingUsernameQuery) return
        usernameSearchLoading = false
        val code = frame.error
        if (!code.isNullOrBlank()) {
            usernameResults = emptyList()
            usernameSearchError = usernameSearchMessage(code)
            return
        }
        usernameSearchError = null
        usernameResults = frame.users.map { it.asPerson() }
    }

    private fun WsUserHit.asPerson(): ContactEntry {
        val name = displayName.ifBlank { username }.ifBlank { "User" }
        return ContactEntry(
            id = id,
            name = name,
            initials = initialsFor(name),
            tone = toneFor(id),
            detail = "@$username",
            online = false,
            encrypted = true,
        )
    }

    private fun usernameSearchMessage(code: String): String = when (code) {
        "search_failed" -> "Couldn't search usernames. Try again."
        "validation_error" -> "That search isn't valid."
        else -> "Couldn't search usernames."
    }

    private suspend fun ensureUserId() {
        if (userId.isNotBlank()) return
        val token = requireAccessToken() ?: return
        when (val result = repository.getProfile(token)) {
            is ChatCall.Ok -> {
                sessionStore.rememberProfile(result.value)
                userId = result.value.id
            }
            is ChatCall.Failed -> listError = result.message
        }
    }

    private suspend fun requireAccessToken(): String? = tokens.ensureFreshAccessToken()
}

class ChatViewModelFactory(
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val bound = ApiClient.bind(sessionStore)
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                sessionStore = sessionStore,
                tokens = bound.tokenRefresher,
                authRepository = ApiClient.repository,
                repository = bound.chatRepository,
                socket = ApiClient.messengerSocket(sessionStore),
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
