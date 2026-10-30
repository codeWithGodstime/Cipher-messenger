package com.example.messenger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.messenger.ui.auth.Countries
import com.example.messenger.ui.auth.formatNational
import com.example.messenger.ui.components.CipherPrimaryButton
import com.example.messenger.ui.components.NumericKeypad
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

@Composable
fun NewContactScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (phoneE164: String) -> Unit,
    onSaveAndChat: (phoneE164: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    var digits by rememberSaveable { mutableStateOf("") }
    val country = Countries.first()
    val complete = digits.length == country.digitCount
    val e164 = country.dialCode + digits.filter { it.isDigit() }

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
                    text = "Find someone already on Cipher by phone",
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
                text = "Phone number",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(
                        text = "${country.flag} ${country.dialCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatNational(digits, country).ifEmpty { "Enter number" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (digits.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "They must already have a Cipher account with this number.",
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
                onClick = { onSaveAndChat(e164) },
                enabled = complete && !busy,
                icon = Icons.AutoMirrored.Filled.Chat,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            CipherPrimaryButton(
                text = "Save contact only",
                onClick = { onSave(e164) },
                enabled = complete && !busy,
                icon = Icons.Filled.PersonAdd,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            NumericKeypad(
                onDigit = { digit ->
                    if (digits.length < country.digitCount) {
                        digits += digit
                    }
                },
                onBackspace = {
                    if (digits.isNotEmpty()) {
                        digits = digits.dropLast(1)
                    }
                },
                modifier = Modifier.padding(top = spacing.xs),
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
