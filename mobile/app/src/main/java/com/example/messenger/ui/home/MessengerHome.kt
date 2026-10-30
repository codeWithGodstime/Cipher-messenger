package com.example.messenger.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.messenger.ui.theme.CipherTheme
import kotlinx.coroutines.launch

/**
 * Chats, a conversation, new chat, and settings.
 * Threads live in memory. Opening a chat, sending a message, and pinning
 * update that list. Calls and the settings rows have no service behind them yet.
 */
@Composable
fun MessengerHome(
    displayName: String,
    phoneLabel: String,
    modifier: Modifier = Modifier,
) {
    var tabName by rememberSaveable { mutableStateOf(HomeTab.Chats.name) }
    var overlay by rememberSaveable { mutableStateOf("none") }
    var threads by remember { mutableStateOf(CipherSamples.threads) }
    var messages by remember { mutableStateOf(CipherSamples.messages) }
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
        threads = threads.map { thread ->
            if (thread.id == id) thread.copy(unread = 0) else thread
        }
        overlay = "chat:$id"
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
                    tabName = HomeTab.Chats.name
                },
                onProfile = {
                    overlay = "none"
                    tabName = HomeTab.Settings.name
                },
            )
        },
        bottomBar = {
            CipherBottomBar(
                selected = selectedTab,
                onSelect = { next ->
                    overlay = "none"
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
                    val thread = threads.first { it.id == openChatId }
                    ConversationScreen(
                            thread = thread,
                            messages = messages[thread.id].orEmpty(),
                            onBack = { overlay = "none" },
                            onSend = { body ->
                                val time = clockLabel()
                                val message = CipherMessage(
                                    id = "local-${System.currentTimeMillis()}",
                                    outgoing = true,
                                    body = body,
                                    time = time,
                                    read = true,
                                )
                                messages = messages + (thread.id to (messages[thread.id].orEmpty() + message))
                                threads = threads.map { item ->
                                    if (item.id == thread.id) {
                                        item.copy(
                                            snippet = body,
                                            timeLabel = time,
                                            voice = false,
                                            unread = 0,
                                            receipt = Receipt.Read,
                                            inRecent = true,
                                        )
                                    } else {
                                        item
                                    }
                                }
                            },
                            onTogglePin = {
                                threads = threads.map { item ->
                                    if (item.id == thread.id) {
                                        val pinned = !item.pinned
                                        item.copy(
                                            pinned = pinned,
                                            inRecent = item.inRecent || !pinned,
                                        )
                                    } else {
                                        item
                                    }
                                }
                            },
                            onNotice = ::notice,
                        )
                }
                overlay == "new" -> NewChatScreen(
                    onBack = { overlay = "none" },
                    onOpenContact = { contact ->
                        if (threads.none { it.id == contact.id }) {
                            threads = threads + contact.asThread()
                        }
                        openChat(contact.id)
                    },
                    onNotice = ::notice,
                )
                tab == HomeTab.Chats -> ChatListScreen(
                    threads = threads,
                    onOpenChat = ::openChat,
                    onNewChat = { overlay = "new" },
                )
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
