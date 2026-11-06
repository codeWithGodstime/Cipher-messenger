package com.example.messenger.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.messenger.crypto.AccountDeviceData
import com.example.messenger.crypto.DeviceProtocolStore
import com.example.messenger.crypto.IdentityStore
import com.example.messenger.crypto.LocalPreKeyStore
import com.example.messenger.crypto.MessageCipher
import com.example.messenger.crypto.SentPlaintextStore
import com.example.messenger.crypto.signalCiphertextType
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.api.WsUserHit
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenRefresher
import com.example.messenger.data.chat.ChatCall
import com.example.messenger.data.chat.ChatCrypto
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.CryptoCall
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
import com.example.messenger.data.keys.KeyCall
import com.example.messenger.data.keys.KyberPreKeyUpload
import com.example.messenger.data.keys.OneTimePreKeyRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class ChatViewModel(
    private val sessionStore: SessionStore,
    private val tokens: TokenRefresher,
    private val authRepository: AuthRepository,
    private val repository: ChatRepository,
    private val preKeys: OneTimePreKeyRefresh,
    private val kyber: KyberPreKeyUpload,
    private val crypto: ChatCrypto,
    private val socket: MessengerSocket,
    private val accountData: AccountDeviceData,
) : ViewModel() {
    var threads by mutableStateOf<List<ChatThread>>(emptyList())
        private set
    var messages by mutableStateOf<Map<String, List<CipherMessage>>>(emptyMap())
        private set
    var savedContacts by mutableStateOf<List<ContactEntry>>(emptyList())
        private set
    var listLoading by mutableStateOf(true)
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
    private val preKeyRefresh = Mutex()
    private val historyGate = Mutex()

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
            socket.opened.collect {
                refreshOneTimePreKeys()
                publishKyberPreKey()
            }
        }
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

    /** Uploads a new one-time prekey batch when the server has fewer than 10 left. */
    fun refreshOneTimePreKeys() {
        val username = sessionStore.usernameLabel
        if (username.isBlank()) return
        viewModelScope.launch {
            preKeyRefresh.withLock {
                val token = requireAccessToken() ?: return@withLock
                preKeys.refresh(username, token)
            }
        }
    }

    fun logout() {
        if (loggingOut) return
        loggingOut = true
        val token = sessionStore.accessToken
        threads = emptyList()
        messages = emptyMap()
        savedContacts = emptyList()
        loadedConversations.clear()
        localPins.clear()
        activeConversationId = null
        usernameResults = emptyList()
        socket.stop()
        tokens.signOut()
        loggedOutEvents.tryEmit(Unit)
        logoutJobs.launch {
            runCatching { accountData.clear() }
            if (!token.isNullOrBlank()) authRepository.logout(token)
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                listLoading = false
                listError = "Sign in again to load chats."
                return@launch
            }
            listLoading = threads.isEmpty()
            listError = null
            when (val result = repository.listConversations(token)) {
                is ChatCall.Ok -> {
                    val mapped = buildList {
                        for (conversation in result.value) {
                            add(conversation.toPlainThread(token))
                        }
                    }
                    threads = mergePins(mapped)
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
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
            val peerId = threads.firstOrNull { it.id == conversationId }?.peerId.orEmpty()
            val plain = loadPlainMessages(token, conversationId, peerId) ?: return@launch
            val snippet = plain.lastOrNull()?.body ?: return@launch
            threads = threads.map { thread ->
                if (thread.id == conversationId) thread.copy(snippet = readableSnippet(snippet)) else thread
            }
        }
    }

    fun leaveConversation() {
        activeConversationId = null
    }

    fun sendMessage(conversationId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val peerId = threads.firstOrNull { it.id == conversationId }?.peerId.orEmpty()
            if (peerId.isBlank() || userId.isBlank()) {
                notice = "Can't encrypt this chat yet."
                return@launch
            }
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to send messages."
                return@launch
            }
            val ciphertext = when (val encrypted = crypto.encrypt(userId, token, peerId, trimmed)) {
                is CryptoCall.Ok -> encrypted.body
                is CryptoCall.Failed -> {
                    notice = encrypted.message
                    return@launch
                }
            }
            if (socket.sendMessage(conversationId, ciphertext)) {
                return@launch
            }
            when (val result = repository.sendMessage(token, conversationId, ciphertext)) {
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
        if (usernameResults.any { it.id == peer && !it.encrypted }) {
            notice = "This user hasn't published encryption keys yet."
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

    /**
     * Decrypts a conversation from oldest to newest and keeps the plaintext.
     * A later open reuses that copy. Signal ciphertext cannot be decrypted twice.
     */
    private suspend fun loadPlainMessages(
        token: String,
        conversationId: String,
        peerId: String,
    ): List<CipherMessage>? = historyGate.withLock {
        if (userId.isBlank()) ensureUserId()
        if (userId.isBlank()) return@withLock null
        if (conversationId in loadedConversations) {
            return@withLock messages[conversationId].orEmpty()
        }
        when (val result = listAllMessages(token, conversationId)) {
            is ChatCall.Failed -> {
                notice = result.message
                null
            }
            is ChatCall.Ok -> {
                val history = plainMessages(result.value, peerId)
                val merged = mergeHistory(messages[conversationId].orEmpty(), history)
                messages = messages + (conversationId to merged)
                loadedConversations += conversationId
                merged
            }
        }
    }

    private suspend fun listAllMessages(
        token: String,
        conversationId: String,
    ): ChatCall<List<ApiMessage>> {
        val newestFirst = ArrayList<ApiMessage>()
        var before: String? = null
        repeat(MAX_HISTORY_PAGES) {
            when (val page = repository.listMessages(token, conversationId, before, HISTORY_PAGE)) {
                is ChatCall.Failed -> return page
                is ChatCall.Ok -> {
                    if (page.value.isEmpty()) return ChatCall.Ok(newestFirst.distinctBy { it.id })
                    newestFirst += page.value
                    if (page.value.size < HISTORY_PAGE) return ChatCall.Ok(newestFirst.distinctBy { it.id })
                    val oldest = page.value.last().createdAt
                    if (oldest == before) return ChatCall.Ok(newestFirst.distinctBy { it.id })
                    before = oldest
                }
            }
        }
        return ChatCall.Ok(newestFirst.distinctBy { it.id })
    }

    private suspend fun plainMessages(apiMessages: List<ApiMessage>, peerId: String): List<CipherMessage> {
        val ordered = ArrayList<CipherMessage>(apiMessages.size)
        for (message in apiMessages.asReversed()) {
            ordered += message.toCipherMessage(userId).copy(body = plainBody(message, peerId))
        }
        return ordered
    }

    private fun mergeHistory(live: List<CipherMessage>, history: List<CipherMessage>): List<CipherMessage> {
        val seen = history.map { it.id }.toSet()
        return history + live.filter { it.id !in seen }
    }

    private suspend fun onIncomingMessage(message: ApiMessage) {
        val token = requireAccessToken() ?: return
        val peerId = threads.firstOrNull { it.id == message.conversationId }?.peerId
            ?: message.senderId.takeIf { it != userId }.orEmpty()
        val hadIt = messages[message.conversationId].orEmpty().any { it.id == message.id }
        if (message.conversationId !in loadedConversations) {
            loadPlainMessages(token, message.conversationId, peerId)
        }
        val current = messages[message.conversationId].orEmpty()
        val existing = current.firstOrNull { it.id == message.id }
        if (existing != null) {
            val viewing = activeConversationId == message.conversationId
            updateThreadFromMessage(
                message,
                existing.body,
                bumpUnread = !hadIt && !viewing && message.senderId != userId,
            )
            if (threads.none { it.id == message.conversationId }) refreshConversations()
            return
        }
        val plain = plainBody(message, peerId)
        val cipher = message.toCipherMessage(userId).copy(body = plain)
        messages = messages + (message.conversationId to (current + cipher))
        val viewing = activeConversationId == message.conversationId
        updateThreadFromMessage(message, plain, bumpUnread = !viewing && !cipher.outgoing)
        if (threads.none { it.id == message.conversationId }) {
            refreshConversations()
        }
    }

    private fun updateThreadFromMessage(message: ApiMessage, plain: String, bumpUnread: Boolean) {
        val time = formatChatTime(message.createdAt)
        val viewing = activeConversationId == message.conversationId
        threads = threads.map { thread ->
            if (thread.id != message.conversationId) return@map thread
            val unread = when {
                viewing -> 0
                bumpUnread -> thread.unread + 1
                else -> thread.unread
            }
            thread.copy(
                snippet = readableSnippet(plain),
                timeLabel = time,
                voice = false,
                unread = unread,
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

    private suspend fun plainBody(message: ApiMessage, peerId: String): String {
        val outgoing = message.senderId == userId
        val peer = if (outgoing) peerId else message.senderId
        return crypto.reveal(userId, peer, message.body, outgoing)
    }

    private suspend fun ApiConversation.toPlainThread(token: String): ChatThread {
        val thread = toThread(userId)
        val plain = lastMessage?.let { loadPlainMessages(token, id, peer.id) }
        val snippet = plain?.lastOrNull()?.body ?: thread.snippet
        val unread = if (activeConversationId == id) 0 else thread.unread
        return thread.copy(snippet = readableSnippet(snippet), unread = unread)
    }

    private fun readableSnippet(body: String): String {
        if (body.isBlank()) return "End-to-end encrypted"
        val decoded = runCatching { Base64.decode(body, Base64.NO_WRAP) }.getOrNull()
        if (decoded != null && signalCiphertextType(decoded) != null) return "Encrypted message"
        return body
    }

    private fun publishKyberPreKey() {
        val username = sessionStore.usernameLabel
        if (username.isBlank()) return
        viewModelScope.launch {
            val token = requireAccessToken() ?: return@launch
            when (val result = kyber.publishIfNeeded(username, token)) {
                is KeyCall.Failed -> Log.w(TAG, result.message)
                else -> Unit
            }
        }
    }

    private fun WsUserHit.asPerson(): ContactEntry {
        val name = displayName.ifBlank { username }.ifBlank { "User" }
        val ready = !identityKey.isNullOrBlank() && signedPreKey != null && kyberPreKey != null
        return ContactEntry(
            id = id,
            name = name,
            initials = initialsFor(name),
            tone = toneFor(id),
            detail = if (ready) "@$username" else "@$username · keys not ready",
            online = false,
            encrypted = ready,
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

    private companion object {
        const val TAG = "Chat"
        const val HISTORY_PAGE = 100
        const val MAX_HISTORY_PAGES = 20
    }
}

class ChatViewModelFactory(
    private val sessionStore: SessionStore,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val bound = ApiClient.bind(sessionStore)
            val identity = IdentityStore(appContext)
            val preKeyStore = LocalPreKeyStore(appContext)
            val username = sessionStore.usernameLabel
            val sessions = DeviceProtocolStore(appContext, username, identity, preKeyStore)
            val sent = SentPlaintextStore(appContext, username)
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                sessionStore = sessionStore,
                tokens = bound.tokenRefresher,
                authRepository = ApiClient.repository,
                repository = bound.chatRepository,
                preKeys = OneTimePreKeyRefresh(
                    preKeys = preKeyStore,
                    keys = bound.keyRepository,
                ),
                kyber = KyberPreKeyUpload(identity, preKeyStore, bound.keyRepository),
                crypto = ChatCrypto(
                    cipher = MessageCipher(sessions),
                    sent = sent,
                    keys = bound.keyRepository,
                ),
                socket = ApiClient.messengerSocket(sessionStore),
                accountData = AccountDeviceData(username, identity, preKeyStore, sessions, sent),
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
