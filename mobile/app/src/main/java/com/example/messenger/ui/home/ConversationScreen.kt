package com.example.messenger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

@Composable
fun ConversationScreen(
    thread: ChatThread,
    messages: List<CipherMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onTogglePin: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    var draft by rememberSaveable(thread.id) { mutableStateOf("") }
    var menu by rememberSaveable(thread.id) { mutableStateOf(false) }
    var sheet by rememberSaveable(thread.id) { mutableStateOf(false) }
    val rows = conversationRows(messages)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var composerHeight by remember { mutableStateOf(0.dp) }

    LaunchedEffect(thread.id, rows.size) {
        listState.scrollToItem(rows.size + 1)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConversationHeader(
                thread = thread,
                menuOpen = menu,
                onMenuOpen = { menu = true },
                onMenuDismiss = { menu = false },
                onBack = onBack,
                onTogglePin = {
                    menu = false
                    onTogglePin()
                },
                onNotice = {
                    menu = false
                    onNotice(it)
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = spacing.gutter),
                contentPadding = PaddingValues(bottom = composerHeight),
                verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.Bottom),
            ) {
                item(key = "banner") {
                    EncryptionBanner(modifier = Modifier.padding(top = spacing.md))
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ConversationRow.Day -> DayChip(row.label)
                        is ConversationRow.Bubble -> MessageBubble(message = row.message, thread = thread)
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(spacing.sm)) }
            }
        }
        Composer(
            value = draft,
            onValueChange = { draft = it },
            onAttach = { sheet = true },
            onSend = {
                val body = draft.trim()
                if (body.isEmpty()) return@Composer
                onSend(body)
                draft = ""
            },
            onNotice = onNotice,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .onSizeChanged { size ->
                    composerHeight = with(density) { size.height.toDp() }
                },
        )
        if (sheet) {
            AttachmentSheet(
                onDismiss = { sheet = false },
                onNotice = {
                    sheet = false
                    onNotice(it)
                },
            )
        }
    }
}

@Composable
private fun ConversationHeader(
    thread: ChatThread,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val spacing = CipherTheme.spacing
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    CipherAvatar(
                        initials = thread.initials,
                        tone = thread.tone,
                        size = 40.dp,
                        contentDescription = thread.title,
                    )
                    if (thread.online) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = spacing.xs)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = thread.presence,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (thread.online) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row {
                IconButton(onClick = { onNotice("Voice calls are not connected in this build.") }) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Voice call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onNotice("Video calls are not connected in this build.") }) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Video call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = onMenuOpen) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(
                            text = {
                                Text(if (thread.pinned) "Unpin" else "Pin")
                            },
                            onClick = onTogglePin,
                        )
                        DropdownMenuItem(
                            text = { Text("Search in chat") },
                            onClick = { onNotice("Search in this chat is not connected in this build.") },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    }
}

private sealed interface ConversationRow {
    val key: String

    data class Day(val label: String, val index: Int) : ConversationRow {
        override val key: String = "day:$index"
    }

    data class Bubble(val message: CipherMessage) : ConversationRow {
        override val key: String = message.id
    }
}

private fun conversationRows(messages: List<CipherMessage>): List<ConversationRow> {
    val rows = ArrayList<ConversationRow>(messages.size + 2)
    var previousDay = ""
    var dayIndex = 0
    for (message in messages) {
        val day = message.dayLabel.ifBlank { "Today" }
        if (day != previousDay) {
            rows += ConversationRow.Day(day, dayIndex)
            dayIndex += 1
            previousDay = day
        }
        rows += ConversationRow.Bubble(message)
    }
    return rows
}

@Composable
private fun DayChip(label: String) {
    val spacing = CipherTheme.spacing
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            )
        }
    }
}

@Composable
private fun EncryptionBanner(modifier: Modifier = Modifier) {
    val spacing = CipherTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryFixed.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Messages are end-to-end encrypted. No one outside of this chat can read or listen to them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: CipherMessage,
    thread: ChatThread,
) {
    val spacing = CipherTheme.spacing
    val bubble = if (message.outgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.outgoing) {
            CipherAvatar(
                initials = thread.initials,
                tone = thread.tone,
                size = 28.dp,
                contentDescription = null,
                modifier = Modifier.padding(end = spacing.sm, bottom = spacing.xs),
            )
        }
        Column(
            horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Surface(
                shape = bubble,
                color = if (message.outgoing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shadowElevation = if (message.outgoing) 2.dp else 1.dp,
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.outgoing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.padding(top = spacing.xs, start = spacing.xs, end = spacing.xs),
            ) {
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.outgoing && message.read) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.sm),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttach) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Add attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = { onNotice("Camera is not connected in this build.") }) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onNotice("Voice notes are not connected in this build.") }) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onSend,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val spacing = CipherTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 48.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Share Content",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ShareAction(Icons.Filled.Image, "Photo", MaterialTheme.colorScheme.primaryFixed, MaterialTheme.colorScheme.primary) {
                            onNotice("Photos are not connected in this build.")
                        }
                        ShareAction(Icons.Filled.Description, "Document", MaterialTheme.colorScheme.secondaryFixed, MaterialTheme.colorScheme.secondary) {
                            onNotice("Documents are not connected in this build.")
                        }
                        ShareAction(Icons.Filled.Headphones, "Audio", MaterialTheme.colorScheme.tertiaryFixed, MaterialTheme.colorScheme.tertiary) {
                            onNotice("Audio is not connected in this build.")
                        }
                        ShareAction(Icons.Filled.LocationOn, "Location", MaterialTheme.colorScheme.primaryFixedDim, MaterialTheme.colorScheme.onPrimaryFixed) {
                            onNotice("Location is not connected in this build.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareAction(
    icon: ImageVector,
    label: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CipherTheme.spacing.sm),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = background,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun ConversationPreview() {
    MessengerTheme {
        val thread = CipherSamples.threads.first { it.id == "elena" }
        ConversationScreen(
            thread = thread,
            messages = CipherSamples.messages.getValue(thread.id),
            onBack = {},
            onSend = {},
            onTogglePin = {},
            onNotice = {},
        )
    }
}
