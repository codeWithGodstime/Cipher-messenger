package com.example.messenger.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.example.messenger.crypto.AccountDeviceData
import com.example.messenger.crypto.DeviceProtocolStore
import com.example.messenger.crypto.IdentityStore
import com.example.messenger.crypto.LocalPreKeyStore
import com.example.messenger.crypto.MediaCipher
import com.example.messenger.crypto.MediaEnvelope
import com.example.messenger.crypto.MediaFileCache
import com.example.messenger.crypto.MediaMessages
import com.example.messenger.crypto.MessageCipher
import com.example.messenger.crypto.SentPlaintextStore
import com.example.messenger.crypto.signalCiphertextType
import com.example.messenger.data.api.ApiClient
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.data.api.WsUserHit
import com.example.messenger.data.auth.AuthRepository
import com.example.messenger.data.auth.ProfileCall
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.data.auth.TokenRefresher
import com.example.messenger.data.chat.ChatCall
import com.example.messenger.data.chat.ChatCrypto
import com.example.messenger.data.chat.ChatRepository
import com.example.messenger.data.chat.CryptoCall
import com.example.messenger.data.chat.MediaRepository
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
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
    private val appContext: Context,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val mediaCache = MediaFileCache(appContext)
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
    var savingName by mutableStateOf(false)
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
    private val mediaDownloads = Mutex()
    private val downloadingIds = mutableSetOf<String>()

    val peerContacts: List<ContactEntry>
        get() {
            val savedIds = savedContacts.map { it.id }.toSet()
            return threads
                .filter { it.peerId.isNotBlank() && it.peerId !in savedIds }
                .distinctBy { it.peerId }
                .map { it.asContact() }
        }

    init {
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

    fun updateDisplayName(raw: String, onSaved: (String) -> Unit) {
        if (savingName) return
        val name = raw.trim()
        if (name.isEmpty() || name.length > 64) {
            notice = "Display name must be 1 to 64 characters."
            return
        }
        viewModelScope.launch {
            val token = requireAccessToken() ?: run {
                notice = "Sign in again before changing your name."
                return@launch
            }
            savingName = true
            try {
                when (val result = authRepository.updateDisplayName(token, name)) {
                    is ProfileCall.Success -> {
                        sessionStore.rememberProfile(result.profile)
                        onSaved(result.profile.displayName)
                    }
                    is ProfileCall.Failed -> notice = result.message
                }
            } finally {
                savingName = false
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

    fun sendMedia(conversationId: String, uri: Uri, kind: String) {
        viewModelScope.launch {
            deliverMedia(conversationId) { readUri(uri, kind) }
        }
    }

    fun sendVoiceFile(conversationId: String, file: File) {
        viewModelScope.launch {
            deliverMedia(conversationId) { readVoice(file) }
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
                val revealed = revealMessages(result.value, peerId)
                val history = revealed.map { it.message }
                val merged = mergeHistory(messages[conversationId].orEmpty(), history)
                messages = messages + (conversationId to merged)
                loadedConversations += conversationId
                scheduleMedia(conversationId, revealed)
                history
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

    private suspend fun revealMessages(apiMessages: List<ApiMessage>, peerId: String): List<RevealedMessage> {
        val ordered = ArrayList<RevealedMessage>(apiMessages.size)
        for (message in apiMessages.asReversed()) {
            ordered += revealMessage(message, peerId)
        }
        return ordered
    }

    private fun mergeHistory(live: List<CipherMessage>, history: List<CipherMessage>): List<CipherMessage> {
        val seen = history.map { it.id }.toSet()
        val sentAttachments = history.mapNotNull { it.attachment?.attachmentId }.toSet()
        return history + live.filter { message ->
            message.id !in seen &&
                !(message.sending && message.attachment?.attachmentId in sentAttachments)
        }
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
        val revealed = revealMessage(message, peerId)
        val cipher = withPendingPath(revealed.message, current)
        val pendingId = pendingIdFor(current, revealed.envelope?.attachmentId)
        val next = if (pendingId == null) {
            current + cipher
        } else {
            current.map { if (it.id == pendingId) cipher else it }
        }
        messages = messages + (message.conversationId to next)
        scheduleMedia(message.conversationId, listOf(revealed))
        val viewing = activeConversationId == message.conversationId
        updateThreadFromMessage(message, cipher.body, bumpUnread = !viewing && !cipher.outgoing)
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

    private suspend fun deliverMedia(
        conversationId: String,
        read: suspend () -> PreparedMedia,
    ) {
        val peerId = threads.firstOrNull { it.id == conversationId }?.peerId.orEmpty()
        if (peerId.isBlank() || userId.isBlank()) {
            notice = "Can't encrypt this chat yet."
            return
        }
        val token = requireAccessToken() ?: run {
            notice = "Sign in again to send messages."
            return
        }
        val prepared = read()
        val ready = when (prepared) {
            is PreparedMedia.Failed -> {
                notice = prepared.message
                return
            }
            is PreparedMedia.Ready -> prepared
        }
        val attachmentId = UUID.randomUUID().toString()
        val cached = try {
            withContext(Dispatchers.IO) {
                mediaCache.write(attachmentId, ready.name, ready.bytes)
            }
        } catch (error: Exception) {
            notice = "Couldn't save this attachment on this device."
            return
        }
        val localId = "local-$attachmentId"
        showPending(conversationId, pendingMedia(localId, attachmentId, ready, cached.absolutePath))
        val sealed = try {
            withContext(Dispatchers.IO) { MediaCipher.seal(ready.bytes, attachmentId) }
        } catch (error: Exception) {
            dropPending(conversationId, localId)
            notice = "Couldn't encrypt this attachment."
            return
        }
        val stored = when (val uploaded = mediaRepository.upload(token, conversationId, attachmentId, sealed.ciphertext)) {
            is ChatCall.Failed -> {
                dropPending(conversationId, localId)
                notice = uploaded.message
                return
            }
            is ChatCall.Ok -> uploaded.value
        }
        if (!stored.digest.equals(sealed.digestHex, ignoreCase = true)) {
            dropPending(conversationId, localId)
            notice = "The server stored different bytes than this device encrypted."
            return
        }
        val envelope = MediaMessages.encode(
            kind = ready.kind,
            mime = ready.mime,
            name = ready.name,
            size = ready.bytes.size.toLong(),
            attachmentId = attachmentId,
            key = sealed.key,
            nonce = sealed.nonce,
            digestHex = sealed.digestHex,
        )
        val signalBody = when (val encrypted = crypto.encrypt(userId, token, peerId, envelope)) {
            is CryptoCall.Ok -> encrypted.body
            is CryptoCall.Failed -> {
                dropPending(conversationId, localId)
                notice = encrypted.message
                return
            }
        }
        if (socket.sendMessage(conversationId, signalBody)) {
            return
        }
        when (val result = repository.sendMessage(token, conversationId, signalBody)) {
            is ChatCall.Ok -> onIncomingMessage(result.value)
            is ChatCall.Failed -> {
                dropPending(conversationId, localId)
                notice = result.message
            }
        }
    }

    private fun pendingMedia(
        localId: String,
        attachmentId: String,
        ready: PreparedMedia.Ready,
        path: String,
    ): CipherMessage {
        val label = when (ready.kind) {
            "image" -> "Photo"
            "video" -> "Video"
            "audio" -> "Audio"
            else -> ready.name.trim().ifBlank { "Document" }
        }
        return CipherMessage(
            id = localId,
            outgoing = true,
            body = label,
            time = clockLabel(),
            dayLabel = "Today",
            sending = true,
            attachment = ChatAttachment(
                kind = ready.kind,
                mime = ready.mime,
                name = ready.name,
                sizeBytes = ready.bytes.size.toLong(),
                attachmentId = attachmentId,
                localPath = path,
            ),
        )
    }

    private fun showPending(conversationId: String, pending: CipherMessage) {
        val current = messages[conversationId].orEmpty()
        messages = messages + (conversationId to (current + pending))
        threads = threads.map { thread ->
            if (thread.id != conversationId) {
                thread
            } else {
                thread.copy(snippet = pending.body, timeLabel = pending.time, inRecent = true)
            }
        }
    }

    private fun dropPending(conversationId: String, localId: String) {
        val current = messages[conversationId].orEmpty()
        val remaining = current.filterNot { it.id == localId }
        if (remaining.size == current.size) return
        messages = messages + (conversationId to remaining)
        val last = remaining.lastOrNull() ?: return
        threads = threads.map { thread ->
            if (thread.id != conversationId) thread else thread.copy(snippet = last.body, timeLabel = last.time)
        }
    }

    private fun pendingIdFor(current: List<CipherMessage>, attachmentId: String?): String? {
        if (attachmentId.isNullOrBlank()) return null
        return current.firstOrNull { it.sending && it.attachment?.attachmentId == attachmentId }?.id
    }

    private fun withPendingPath(message: CipherMessage, current: List<CipherMessage>): CipherMessage {
        val attachment = message.attachment ?: return message
        if (!attachment.localPath.isNullOrBlank()) return message
        val path = current.firstOrNull {
            it.sending && it.attachment?.attachmentId == attachment.attachmentId
        }?.attachment?.localPath ?: return message
        return message.copy(attachment = attachment.copy(localPath = path))
    }

    private suspend fun readUri(uri: Uri, kind: String): PreparedMedia = withContext(Dispatchers.IO) {
        val mime = appContext.contentResolver.getType(uri) ?: mimeFor(kind)
        val name = displayName(uri)
        val stream = try {
            appContext.contentResolver.openInputStream(uri)
        } catch (error: Exception) {
            null
        } ?: return@withContext PreparedMedia.Failed("Couldn't read this attachment.")
        val bytes = try {
            readCapped(stream)
        } catch (error: Exception) {
            return@withContext PreparedMedia.Failed("Couldn't read this attachment.")
        } ?: return@withContext PreparedMedia.Failed("This attachment is too large to send.")
        PreparedMedia.Ready(bytes, kind, mime, name)
    }

    private suspend fun readVoice(file: File): PreparedMedia = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext PreparedMedia.Failed("Couldn't read the voice note.")
        val bytes = try {
            readCapped(file.inputStream())
        } catch (error: Exception) {
            return@withContext PreparedMedia.Failed("Couldn't read the voice note.")
        } ?: return@withContext PreparedMedia.Failed("This attachment is too large to send.")
        file.delete()
        PreparedMedia.Ready(bytes, "audio", "audio/mp4", "Voice message.m4a")
    }

    private fun readCapped(input: InputStream): ByteArray? {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            val max = MediaCipher.MAX_PLAINTEXT_BYTES.toLong()
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                total += count.toLong()
                if (total > max) return null
                out.write(chunk, 0, count)
            }
            return out.toByteArray()
        }
    }

    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
        if (!queried.isNullOrBlank()) return queried
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "file"
    }

    private fun mimeFor(kind: String): String = when (kind) {
        "image" -> "image/jpeg"
        "video" -> "video/mp4"
        "audio" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private suspend fun revealMessage(message: ApiMessage, peerId: String): RevealedMessage {
        val plain = plainBody(message, peerId)
        val base = message.toCipherMessage(userId)
        val envelope = MediaMessages.parse(plain) ?: return RevealedMessage(base.copy(body = plain))
        val cached = withContext(Dispatchers.IO) { mediaCache.existing(envelope.attachmentId) }
        val attachment = ChatAttachment(
            kind = envelope.kind,
            mime = envelope.mime,
            name = envelope.name,
            sizeBytes = envelope.size,
            attachmentId = envelope.attachmentId,
            localPath = cached?.absolutePath,
        )
        return RevealedMessage(
            message = base.copy(body = MediaMessages.snippet(plain), attachment = attachment),
            envelope = envelope,
        )
    }

    private fun scheduleMedia(conversationId: String, revealed: List<RevealedMessage>) {
        for (item in revealed) {
            val envelope = item.envelope ?: continue
            if (item.message.attachment?.localPath != null) continue
            if (!downloadingIds.add(envelope.attachmentId)) continue
            val messageId = item.message.id
            viewModelScope.launch {
                try {
                    fetchMedia(conversationId, messageId, envelope)
                } finally {
                    downloadingIds.remove(envelope.attachmentId)
                }
            }
        }
    }

    private suspend fun fetchMedia(conversationId: String, messageId: String, envelope: MediaEnvelope) {
        mediaDownloads.withLock {
            val current = messages[conversationId].orEmpty().firstOrNull { it.id == messageId }
            if (current?.attachment?.localPath != null) return@withLock
            val cached = withContext(Dispatchers.IO) { mediaCache.existing(envelope.attachmentId) }
            if (cached != null) {
                assignLocalPath(conversationId, messageId, cached.absolutePath)
                return@withLock
            }
            val token = requireAccessToken() ?: run {
                notice = "Sign in again to load this attachment."
                return@withLock
            }
            val ciphertext = when (val result = mediaRepository.download(token, conversationId, envelope.attachmentId)) {
                is ChatCall.Failed -> {
                    notice = result.message
                    return@withLock
                }
                is ChatCall.Ok -> result.value
            }
            val file = try {
                withContext(Dispatchers.IO) {
                    val actual = sha256Hex(ciphertext)
                    if (!actual.equals(envelope.digestHex, ignoreCase = true)) return@withContext null
                    val plain = MediaCipher.open(
                        ciphertext,
                        envelope.key,
                        envelope.nonce,
                        envelope.attachmentId,
                        envelope.digestHex,
                    )
                    mediaCache.write(envelope.attachmentId, envelope.name, plain)
                }
            } catch (error: Exception) {
                notice = "Couldn't open this attachment."
                return@withLock
            }
            if (file == null) {
                notice = "The encrypted file didn't match this message."
                return@withLock
            }
            assignLocalPath(conversationId, messageId, file.absolutePath)
        }
    }

    private fun assignLocalPath(conversationId: String, messageId: String, path: String) {
        val current = messages[conversationId] ?: return
        var changed = false
        val updated = current.map { message ->
            val attachment = message.attachment
            if (message.id != messageId || attachment == null || !attachment.localPath.isNullOrBlank()) {
                message
            } else {
                changed = true
                message.copy(attachment = attachment.copy(localPath = path))
            }
        }
        if (changed) messages = messages + (conversationId to updated)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val alphabet = "0123456789abcdef".toCharArray()
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            hex[index * 2] = alphabet[value ushr 4]
            hex[index * 2 + 1] = alphabet[value and 0x0f]
        }
        return String(hex)
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
        return MediaMessages.snippet(body)
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

private class RevealedMessage(
    val message: CipherMessage,
    val envelope: MediaEnvelope? = null,
)

private sealed interface PreparedMedia {
    class Ready(
        val bytes: ByteArray,
        val kind: String,
        val mime: String,
        val name: String,
    ) : PreparedMedia

    class Failed(val message: String) : PreparedMedia
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
                appContext = appContext.applicationContext,
                mediaRepository = bound.mediaRepository,
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
