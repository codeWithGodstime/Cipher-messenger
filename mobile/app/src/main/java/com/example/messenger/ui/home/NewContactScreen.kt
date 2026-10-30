package com.example.messenger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.messenger.ui.components.CipherPrimaryButton
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

@Composable
fun NewContactScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (username: String) -> Unit,
    onSaveAndChat: (username: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    var username by rememberSaveable { mutableStateOf("") }
    val ready = username.trim().length >= 3 && !busy

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xs, vertical = spacing.sm),
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
                    text = "New contact",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Find someone already on Cipher by username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = "Username",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { next ->
                    username = next.lowercase()
                        .removePrefix("@")
                        .filter { it.isLetterOrDigit() || it == '_' }
                        .take(32)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = {
                    Text(
                        text = "@",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (ready) onSaveAndChat(username) },
                ),
            )
            Text(
                text = "They must already have a Cipher account with this username.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CipherPrimaryButton(
                text = if (busy) "Connecting…" else "Save & chat",
                onClick = { onSaveAndChat(username) },
                enabled = ready,
                icon = Icons.AutoMirrored.Filled.Chat,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            CipherPrimaryButton(
                text = "Save contact only",
                onClick = { onSave(username) },
                enabled = ready,
                icon = Icons.Filled.PersonAdd,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun NewContactPreview() {
    MessengerTheme {
        NewContactScreen(
            busy = false,
            onBack = {},
            onSave = {},
            onSaveAndChat = {},
        )
    }
}
