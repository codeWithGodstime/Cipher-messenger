package com.example.messenger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.BuildConfig
import com.example.messenger.ui.components.AboveKeyboard
import com.example.messenger.ui.components.revealAboveKeyboard
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
fun SettingsScreen(
    displayName: String,
    usernameLabel: String,
    onNotice: (String) -> Unit,
    onSignOut: () -> Unit,
    onSaveDisplayName: (String) -> Unit = {},
    signingOut: Boolean = false,
    savingName: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    val scheme = MaterialTheme.colorScheme
    val name = displayName.trim()
    val handle = usernameLabel.trim().removePrefix("@")
    val username = if (handle.isBlank()) "" else "@$handle"
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf(name) }
    LaunchedEffect(savingName, displayName) {
        if (!savingName && editingName && name.isNotEmpty() && name == nameDraft.trim()) {
            editingName = false
        }
    }
    val entries = listOf(
        SettingsEntry("Account", "Security notifications, change username", Icons.Filled.Key, scheme.primary),
        SettingsEntry("Privacy", "Last seen, profile photo, read receipts, app lock", Icons.Filled.Shield, scheme.secondary),
        SettingsEntry("Security", "Encryption verification, device security", Icons.Filled.VerifiedUser, scheme.primary),
        SettingsEntry("Notifications", "Message, group, and call tones", Icons.Filled.Notifications, scheme.tertiary),
        SettingsEntry("Appearance", "Theme: Light / Dark, wallpaper", Icons.Filled.Palette, scheme.primary),
        SettingsEntry("Storage and data", "Network usage, auto-download", Icons.Filled.Storage, scheme.secondary),
        SettingsEntry("Help", "Contact us, privacy policy", Icons.Filled.Help, scheme.tertiary),
    ).filter { entry ->
        query.isBlank() ||
            entry.title.contains(query, ignoreCase = true) ||
            entry.subtitle.contains(query, ignoreCase = true)
    }

    AboveKeyboard {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter)
            .padding(bottom = spacing.xl),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm, bottom = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            IconButton(
                onClick = {
                    searching = !searching
                    if (!searching) query = ""
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search settings",
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
        if (searching) {
            CipherSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search settings",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.md),
            )
        }
        ProfileCard(
            name = name.ifBlank { "Add a display name" },
            nameMuted = name.isBlank(),
            phone = username,
            editing = editingName,
            draft = nameDraft,
            saving = savingName,
            onDraftChange = { nameDraft = it.take(64) },
            onEdit = {
                nameDraft = name
                editingName = true
            },
            onCancel = { editingName = false },
            onSave = {
                val next = nameDraft.trim()
                if (next == name) {
                    editingName = false
                } else {
                    onSaveDisplayName(next)
                }
            },
            onQr = { onNotice("Your QR code is not connected in this build.") },
        )
        Column(
            modifier = Modifier.padding(top = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SignOutRow(
                busy = signingOut,
                onClick = onSignOut,
            )
            if (entries.isEmpty()) {
                Text(
                    text = "No settings match that search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = spacing.md),
                )
            }
            entries.forEach { entry ->
                SettingsRow(entry = entry, onClick = {
                    onNotice("${entry.title} is not connected in this build.")
                })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Cipher v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline,
            )
            Text(
                text = "Quantum-resistant encryption active",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline,
            )
        }
    }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    nameMuted: Boolean,
    phone: String,
    editing: Boolean,
    draft: String,
    saving: Boolean,
    onDraftChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onQr: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.05f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box {
                        CipherAvatar(
                            initials = initialsFor(if (nameMuted) phone.removePrefix("@") else name),
                            tone = AvatarTone.Cobalt,
                            size = 56.dp,
                            contentDescription = name,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(scheme.secondary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Verified",
                                tint = scheme.onSecondary,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            if (editing) {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = onDraftChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .revealAboveKeyboard(),
                                    singleLine = true,
                                    placeholder = { Text("Display name") },
                                    enabled = !saving,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { if (!saving) onSave() },
                                    ),
                                )
                                IconButton(
                                    onClick = onSave,
                                    enabled = !saving && draft.trim().isNotEmpty(),
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Save display name",
                                            tint = scheme.primary,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onCancel,
                                    enabled = !saving,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel",
                                        tint = scheme.error,
                                    )
                                }
                            } else {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (nameMuted) scheme.onSurfaceVariant else scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = onEdit) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit display name",
                                        tint = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.padding(top = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = scheme.secondary,
                                modifier = Modifier
                                    .padding(end = spacing.xs)
                                    .size(14.dp),
                            )
                            Text(
                                text = "End-to-end encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.secondary,
                            )
                        }
                    }
                }
                Surface(
                    onClick = onQr,
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.primaryContainer,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = "Show QR code",
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    entry: SettingsEntry,
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
                .padding(spacing.sm),
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
                        .background(entry.tint.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = entry.tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun SignOutRow(
    busy: Boolean,
    onClick: () -> Unit,
) {
    val spacing = CipherTheme.spacing
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.error.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = scheme.error,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out",
                        tint = scheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (busy) "Logging out…" else "Log out",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.error,
                )
                Text(
                    text = "Clear this device and return to login",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun SettingsPreview() {
    MessengerTheme {
        SettingsScreen(
            displayName = "",
            usernameLabel = "maya",
            onNotice = {},
            onSignOut = {},
        )
    }
}
