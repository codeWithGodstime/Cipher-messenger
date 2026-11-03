package com.example.messenger.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

private enum class ChatFilter {
    All,
    Unread,
    Online,
}

@Composable
fun ChatListScreen(
    threads: List<ChatThread>,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(ChatFilter.All.name) }
    var filterMenu by rememberSaveable { mutableStateOf(false) }
    val filter = ChatFilter.valueOf(filterName)
    val visible = threads.filter { thread ->
        val matchesQuery = query.isBlank() ||
            thread.title.contains(query, ignoreCase = true) ||
            thread.snippet.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            ChatFilter.All -> true
            ChatFilter.Unread -> thread.unread > 0
            ChatFilter.Online -> thread.online
        }
        matchesQuery && matchesFilter
    }
    val pinned = visible.filter { it.pinned }
    val recent = visible.filter { it.inRecent }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.gutter),
        ) {
            Row(
                modifier = Modifier.padding(top = spacing.sm, bottom = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CipherSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search encrypted chats...",
                    modifier = Modifier.weight(1f),
                )
                Box {
                    Surface(
                        onClick = { filterMenu = true },
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Filter chats",
                                tint = if (filter == ChatFilter.All) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = filterMenu,
                        onDismissRequest = { filterMenu = false },
                    ) {
                        ChatFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    filterName = option.name
                                    filterMenu = false
                                },
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Pinned",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "${pinned.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "pinned-row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            pinned.forEach { thread ->
                                PinnedCard(thread = thread, onClick = { onOpenChat(thread.id) })
                            }
                        }
                    }
                }
                item(key = "recent-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent Messages",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "All secure",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                if (recent.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (threads.isEmpty()) {
                                "No conversations yet. Tap the new chat button to search by username."
                            } else {
                                "No chats match that search."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = spacing.lg),
                        )
                    }
                } else {
                    items(recent, key = { it.id }) { thread ->
                        ChatRow(thread = thread, onClick = { onOpenChat(thread.id) })
                    }
                }
            }
        }
        NewChatFab(
            onClick = onNewChat,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = spacing.gutter, bottom = spacing.lg),
        )
    }
}

@Composable
private fun PinnedCard(
    thread: ChatThread,
    onClick: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    Surface(
        onClick = onClick,
        modifier = Modifier.width(144.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                CipherAvatar(
                    initials = thread.initials,
                    tone = if (thread.id == "elena") AvatarTone.Teal else thread.tone,
                    size = 40.dp,
                    contentDescription = thread.title,
                )
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = thread.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.sm),
            )
            Text(
                text = thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                when (thread.pinnedMark) {
                    PinnedMark.Lock -> Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Encrypted",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                    PinnedMark.Read -> Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    thread: ChatThread,
    onClick: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            CipherAvatar(
                initials = thread.initials,
                tone = thread.tone,
                size = 48.dp,
                online = thread.online,
                contentDescription = thread.title,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = thread.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (thread.unread > 0) FontWeight.Medium else FontWeight.Normal,
                        color = if (thread.unread > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (thread.voice) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = thread.snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = thread.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ReceiptMark(thread = thread)
                }
            }
        }
    }
}

@Composable
private fun ReceiptMark(thread: ChatThread) {
    when {
        thread.unread > 0 -> Box(
            modifier = Modifier
                .padding(start = CipherTheme.spacing.sm)
                .size(20.dp)
                .clip(CircleShape)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = thread.unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        thread.receipt == Receipt.Read -> Icon(
            imageVector = Icons.Filled.DoneAll,
            contentDescription = "Read",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(start = CipherTheme.spacing.sm)
                .size(18.dp),
        )
        thread.receipt == Receipt.Sent -> Icon(
            imageVector = Icons.Filled.Done,
            contentDescription = "Sent",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(start = CipherTheme.spacing.sm)
                .size(18.dp),
        )
    }
}

@Composable
private fun NewChatFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .size(16.dp)
                .clip(CircleShape)
                .padding(0.dp),
        ) {
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryFixed,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.surface,
                ),
            ) {}
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun ChatListPreview() {
    MessengerTheme {
        ChatListScreen(
            threads = CipherSamples.threads,
            onOpenChat = {},
            onNewChat = {},
        )
    }
}
