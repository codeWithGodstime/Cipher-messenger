package com.example.messenger.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.ui.components.AboveKeyboard
import com.example.messenger.ui.components.CipherPrimaryButton
import com.example.messenger.ui.components.revealAboveKeyboard
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

@Composable
fun UsernameAuthScreen(
    mode: AuthMode,
    username: String,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    statusMessage: String? = null,
    onStatusShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val spacing = CipherTheme.spacing
    val title = if (mode == AuthMode.Login) "Welcome back" else "Create your username"
    val subtitle = if (mode == AuthMode.Login) {
        "Enter the username for your Cipher account."
    } else {
        "Choose a unique handle. Letters, digits, and underscores only."
    }
    val action = if (mode == AuthMode.Login) {
        if (submitting) "Signing in…" else "Sign in"
    } else {
        if (submitting) "Creating…" else "Create account"
    }
    val ready = username.trim().length >= 3 && !submitting

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        AboveKeyboard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.gutter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "256-BIT TLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { next ->
                        onUsernameChange(
                            next.lowercase()
                                .filter { it.isLetterOrDigit() || it == '_' }
                                .take(32),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .revealAboveKeyboard(),
                    singleLine = true,
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                        )
                    },
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
                        onDone = { if (ready) onContinue() },
                    ),
                )
            }

            CipherPrimaryButton(
                text = action,
                onClick = onContinue,
                enabled = ready,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.size(spacing.md))
        }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun UsernameSignupPreview() {
    MessengerTheme {
        UsernameAuthScreen(
            mode = AuthMode.Signup,
            username = "alex",
            onUsernameChange = {},
            onBack = {},
            onContinue = {},
        )
    }
}
