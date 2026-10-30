package com.example.messenger.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    displayName: String,
    phoneLabel: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatViewModel = viewModel(
        factory = remember(sessionStore) { ChatViewModelFactory(sessionStore) },
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
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
        },
        bottomBar = {
            CipherBottomBar(
                selected = selectedTab,
                onSelect = { next ->
                    overlay = "none"
                    viewModel.leaveConversation()
                    tabName = next.name
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                openChatId != null -> {
                    val thread = viewModel.threads.firstOrNull { it.id == openChatId }
                    if (thread == null) {
                        overlay = "none"
                        viewModel.leaveConversation()
                    } else {
                        ConversationScreen(
                            thread = thread,
                            messages = viewModel.messages[thread.id].orEmpty(),
                            onBack = {
                                overlay = "none"
                                viewModel.leaveConversation()
                            },
                            onSend = { body -> viewModel.sendMessage(thread.id, body) },
                            onTogglePin = { viewModel.togglePin(thread.id) },
                            onNotice = ::notice,
                        )
                    }
                }
                overlay == "new" -> NewChatScreen(
                    contacts = viewModel.peerContacts,
                    onBack = { overlay = "none" },
                    onOpenPeer = { peerUserId ->
                        viewModel.startChatWithPeer(peerUserId) { conversationId ->
                            openChat(conversationId)
                        }
                    },
                    onNotice = ::notice,
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
                            onNewChat = { overlay = "new" },
                        )
                    }
                }
                tab == HomeTab.Calls -> CallsPlaceholder()
                tab == HomeTab.Settings -> SettingsScreen(
                    displayName = displayName,
                    phoneLabel = phoneLabel,
                    onNotice = ::notice,
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
