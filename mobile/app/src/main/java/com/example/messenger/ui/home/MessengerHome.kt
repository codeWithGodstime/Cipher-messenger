package com.example.messenger.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.messenger.data.auth.SessionStore
import com.example.messenger.ui.theme.CipherTheme
import kotlinx.coroutines.launch

/**
 * Chats, a conversation, new chat, and settings.
 * Conversation list and messages come from the API; sends go over WebSocket
 * with an HTTP fallback. Pinning stays local to this session.
 */
@Composable
fun MessengerHome(
    sessionStore: SessionStore,
    userId: String,
    displayName: String,
    usernameLabel: String,
    onDisplayNameChanged: (String) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$userId",
        factory = remember(sessionStore, appContext, userId) {
            ChatViewModelFactory(sessionStore, appContext)
        },
    )
    var tabName by rememberSaveable { mutableStateOf(HomeTab.Chats.name) }
    var overlay by rememberSaveable { mutableStateOf("none") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tab = HomeTab.valueOf(tabName)
    val openChatId = overlay.removePrefix("chat:").takeIf { overlay.startsWith("chat:") }
    val browsingChats = overlay != "none" || tab == HomeTab.Chats
    val selectedTab = if (browsingChats && overlay != "none") HomeTab.Chats else tab

    fun notice(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun openChat(id: String) {
        viewModel.openConversation(id)
        overlay = "chat:$id"
    }

    fun popOverlay() {
        when {
            overlay == "contact" -> {
                viewModel.clearUsernameSearch()
                overlay = "new"
            }
            overlay == "new" || overlay.startsWith("chat:") -> {
                overlay = "none"
                viewModel.leaveConversation()
                viewModel.clearUsernameSearch()
            }
        }
    }

    BackHandler(enabled = overlay != "none") {
        popOverlay()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshOneTimePreKeys()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.loggedOut.collect { onLoggedOut() }
    }

    LaunchedEffect(viewModel.notice) {
        val text = viewModel.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeNotice()
    }

    LaunchedEffect(viewModel.listError) {
        val text = viewModel.listError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeListError()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = if (openChatId != null) {
            // The conversation applies the keyboard inset itself so the thread
            // resizes above the composer instead of drawing under the keyboard.
            WindowInsets.safeDrawing
                .exclude(WindowInsets.ime)
                .exclude(WindowInsets.navigationBars)
        } else {
            WindowInsets.safeDrawing.exclude(WindowInsets.ime)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (openChatId == null) {
                CipherBrandBar(
                    onSearch = {
                        overlay = "none"
                        viewModel.leaveConversation()
                        tabName = HomeTab.Chats.name
                    },
                    onProfile = {
                        overlay = "none"
                        viewModel.leaveConversation()
                        tabName = HomeTab.Settings.name
                    },
                )
            }
        },
        bottomBar = {
            if (openChatId == null) {
                CipherBottomBar(
                    selected = selectedTab,
                    onSelect = { next ->
                        overlay = "none"
                        viewModel.leaveConversation()
                        tabName = next.name
                    },
                )
            }
        },
    ) { padding ->
        val density = LocalDensity.current
        val keyboardLift = with(density) {
            val imePx = WindowInsets.ime.getBottom(this)
            val belowPx = padding.calculateBottomPadding().roundToPx()
            (imePx - belowPx).coerceAtLeast(0).toDp()
        }
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                openChatId != null -> {
                    val thread = viewModel.threads.firstOrNull { it.id == openChatId }
                    if (thread == null) {
                        MissingConversation(
                            loading = viewModel.listLoading,
                            onBack = {
                                overlay = "none"
                                viewModel.leaveConversation()
                            },
                        )
                    } else {
                        ConversationScreen(
                            thread = thread,
                            messages = viewModel.messages[thread.id].orEmpty(),
                            onBack = {
                                overlay = "none"
                                viewModel.leaveConversation()
                            },
                            onSend = { body -> viewModel.sendMessage(thread.id, body) },
                            onSendMedia = { uri, kind -> viewModel.sendMedia(thread.id, uri, kind) },
                            onSendVoice = { file -> viewModel.sendVoiceFile(thread.id, file) },
                            onTogglePin = { viewModel.togglePin(thread.id) },
                            onNotice = ::notice,
                        )
                    }
                }
                overlay == "new" -> NewChatScreen(
                    contacts = viewModel.savedContacts,
                    recentPeers = viewModel.peerContacts,
                    onBack = {
                        viewModel.clearUsernameSearch()
                        overlay = "none"
                    },
                    onOpenPeer = { peerUserId ->
                        viewModel.startChatWithPeer(peerUserId) { conversationId ->
                            openChat(conversationId)
                        }
                    },
                    onOpenUsername = { username ->
                        viewModel.startChatWithUsername(username) { conversationId ->
                            openChat(conversationId)
                        }
                    },
                    usernameResults = viewModel.usernameResults,
                    usernameSearchLoading = viewModel.usernameSearchLoading,
                    usernameSearchError = viewModel.usernameSearchError,
                    onUsernameQuery = viewModel::onUsernameQuery,
                    onNewContact = {
                        viewModel.clearUsernameSearch()
                        overlay = "contact"
                    },
                    onNotice = ::notice,
                )
                overlay == "contact" -> NewContactScreen(
                    busy = viewModel.connectBusy,
                    onBack = {
                        viewModel.clearUsernameSearch()
                        overlay = "new"
                    },
                    usernameResults = viewModel.usernameResults,
                    usernameSearchLoading = viewModel.usernameSearchLoading,
                    usernameSearchError = viewModel.usernameSearchError,
                    onUsernameQuery = viewModel::onUsernameQuery,
                    onPickUser = { userId ->
                        viewModel.startChatWithPeer(userId) { conversationId ->
                            openChat(conversationId)
                        }
                    },
                    onSave = { username ->
                        viewModel.addContactByUsername(
                            rawUsername = username,
                            startChat = false,
                            onOpened = {},
                            onSaved = { overlay = "new" },
                        )
                    },
                    onSaveAndChat = { username ->
                        viewModel.addContactByUsername(
                            rawUsername = username,
                            startChat = true,
                            onOpened = { conversationId -> openChat(conversationId) },
                        )
                    },
                )
                tab == HomeTab.Chats -> {
                    when {
                        viewModel.listLoading && viewModel.threads.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> ChatListScreen(
                            threads = viewModel.threads,
                            onOpenChat = ::openChat,
                            onNewChat = {
                                viewModel.clearUsernameSearch()
                                overlay = "new"
                            },
                            modifier = Modifier.padding(bottom = keyboardLift),
                        )
                    }
                }
                tab == HomeTab.Calls -> CallsPlaceholder()
                tab == HomeTab.Settings -> SettingsScreen(
                    displayName = displayName,
                    usernameLabel = usernameLabel,
                    onNotice = ::notice,
                    onSignOut = viewModel::logout,
                    onSaveDisplayName = { name ->
                        viewModel.updateDisplayName(name, onDisplayNameChanged)
                    },
                    signingOut = viewModel.loggingOut,
                    savingName = viewModel.savingName,
                )
            }
        }
    }
}

@Composable
private fun MissingConversation(
    loading: Boolean,
    onBack: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = "This conversation is no longer available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun CallsPlaceholder() {
    val spacing = CipherTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = spacing.md),
        )
        Text(
            text = "Calls",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Voice and video calls will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}
