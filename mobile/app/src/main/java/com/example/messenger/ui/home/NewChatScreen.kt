package com.example.messenger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

@Composable
fun NewChatScreen(
    contacts: List<ContactEntry>,
    recentPeers: List<ContactEntry>,
    onBack: () -> Unit,
    onOpenPeer: (peerUserId: String) -> Unit,
    onOpenUsername: (username: String) -> Unit,
    onNewContact: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val visibleContacts = contacts.filter { it.matches(query) }
    val visiblePeers = recentPeers.filter { it.matches(query) }
    val trimmedQuery = query.trim()
    val compactUsername = trimmedQuery.removePrefix("@").lowercase().filter { !it.isWhitespace() }
    val looksLikeUsername = compactUsername.length >= 3 &&
        compactUsername.first().isLetter() &&
        compactUsername.all { it.isLetterOrDigit() || it == '_' }
    val canStartById = trimmedQuery.isNotEmpty() &&
        !looksLikeUsername &&
        visibleContacts.none { it.id.equals(trimmedQuery, ignoreCase = true) } &&
        visiblePeers.none { it.id.equals(trimmedQuery, ignoreCase = true) }
    val canStartByUsername = trimmedQuery.isNotEmpty() && looksLikeUsername
    val contactCount = contacts.size
    val subtitle = when {
        contactCount == 0 && recentPeers.isEmpty() -> "Add a contact or start with a username"
        contactCount == 0 -> "${recentPeers.size} recent peers"
        else -> "$contactCount contacts"
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xs, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column {
                    Text(
                        text = "New chat",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row {
                IconButton(onClick = { searchFocus.requestFocus() }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search contacts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onNotice("More contact actions are not connected in this build.") }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ActionCard(
                        icon = Icons.Filled.Group,
                        iconBackground = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "New group",
                        subtitle = "Up to 1,000 secure participants",
                        onClick = { onNotice("New groups are not connected in this build.") },
                    )
                    ActionCard(
                        icon = Icons.Filled.PersonAdd,
                        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = "New contact",
                        subtitle = "Add by username",
                        onClick = onNewContact,
                    )
                    ActionCard(
                        icon = Icons.Filled.Campaign,
                        iconBackground = MaterialTheme.colorScheme.tertiaryFixed,
                        iconTint = MaterialTheme.colorScheme.onTertiaryFixed,
                        title = "New encrypted broadcast",
                        subtitle = "Send secure messages to multiple lists",
                        onClick = { onNotice("Broadcasts are not connected in this build.") },
                    )
                }
            }
            item(key = "search") {
                CipherSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search contacts or username",
                    focusRequester = searchFocus,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.md),
                )
            }
            if (canStartById) {
                item(key = "start-by-id") {
                    ActionCard(
                        icon = Icons.Filled.Hub,
                        iconBackground = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "Chat with user",
                        subtitle = trimmedQuery,
                        onClick = { onOpenPeer(trimmedQuery) },
                    )
                }
            }
            if (canStartByUsername) {
                item(key = "start-by-username") {
                    ActionCard(
                        icon = Icons.Filled.Dialpad,
                        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = "Chat by username",
                        subtitle = "@$compactUsername",
                        onClick = { onOpenUsername(compactUsername) },
                    )
                }
            }
            item(key = "dial") {
                DialBanner(
                    onDial = onNewContact,
                    modifier = Modifier.padding(bottom = spacing.md),
                )
            }
            if (visibleContacts.isNotEmpty()) {
                item(key = "contacts-label") { SectionLabel("Contacts") }
                items(visibleContacts, key = { "contact-${it.id}" }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onOpenPeer(contact.id) },
                        onCall = { onNotice("Calling ${contact.name} is not connected in this build.") },
                    )
                }
            }
            if (visiblePeers.isNotEmpty()) {
                item(key = "peers-label") { SectionLabel("Recent peers") }
                items(visiblePeers, key = { "peer-${it.id}" }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onOpenPeer(contact.id) },
                        onCall = { onNotice("Calling ${contact.name} is not connected in this build.") },
                    )
                }
            }
            item(key = "footer") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(end = spacing.xs)
                            .size(16.dp),
                    )
                    Text(
                        text = "All contacts and messages are secured with quantum-resistant encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun DialBanner(
    onDial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.10f),
                        scheme.surfaceContainerLow,
                        scheme.secondary.copy(alpha = 0.10f),
                    ),
                ),
            )
            .padding(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Dialpad,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = "Direct Secure Dial",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Text(
                    text = "Connect via Cipher ID or username",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            onClick = onDial,
            shape = CircleShape,
            color = scheme.primary,
        ) {
            Text(
                text = "Dial",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimary,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
            )
        }
    }
}

@Composable
private fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = CipherTheme.spacing.sm, start = CipherTheme.spacing.xs),
    )
}

@Composable
private fun ContactRow(
    contact: ContactEntry,
    onClick: () -> Unit,
    onCall: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                CipherAvatar(
                    initials = contact.initials,
                    tone = contact.tone,
                    size = 48.dp,
                    online = contact.online && !contact.group,
                    icon = if (contact.group) Icons.Filled.Hub else null,
                    contentDescription = contact.name,
                )
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (contact.encrypted) Icons.Filled.Lock else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = if (contact.encrypted) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(end = spacing.xs)
                                .size(14.dp),
                        )
                        Text(
                            text = contact.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (contact.encrypted) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (contact.group) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            } else {
                IconButton(onClick = onCall) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Voice call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ContactEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return name.contains(query, ignoreCase = true) ||
        detail.contains(query, ignoreCase = true) ||
        id.contains(query, ignoreCase = true)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun NewChatPreview() {
    MessengerTheme {
        NewChatScreen(
            contacts = CipherSamples.frequent,
            recentPeers = CipherSamples.directory,
            onBack = {},
            onOpenPeer = {},
            onOpenUsername = {},
            onNewContact = {},
            onNotice = {},
        )
    }
}
