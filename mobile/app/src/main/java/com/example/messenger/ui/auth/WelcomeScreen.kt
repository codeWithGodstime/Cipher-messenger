package com.example.messenger.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.ui.components.CipherMark
import com.example.messenger.ui.components.CipherPrimaryButton
import com.example.messenger.ui.components.CipherTonalButton
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme

private data class Notice(val title: String, val body: String)

@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var notice by remember { mutableStateOf<Notice?>(null) }
    val spacing = CipherTheme.spacing

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(bottom = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader()
            Text(
                text = "Cipher",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Private, effortless messaging.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs, bottom = spacing.lg),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PillarCard(
                    icon = Icons.Filled.EnhancedEncryption,
                    iconBackground = MaterialTheme.colorScheme.primaryFixed,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "End-to-end encrypted",
                )
                PillarCard(
                    icon = Icons.Filled.HistoryToggleOff,
                    iconBackground = MaterialTheme.colorScheme.secondaryFixedDim.copy(alpha = 0.4f),
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Zero data logs",
                )
                PillarCard(
                    icon = Icons.Filled.Code,
                    iconBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Open source core",
                )
            }
            CipherPrimaryButton(
                text = "Create account",
                onClick = onCreateAccount,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
            )
            Spacer(Modifier.size(spacing.sm))
            CipherTonalButton(
                text = "Sign in",
                onClick = onSignIn,
                icon = Icons.Filled.VpnKey,
            )
            LegalLine(
                onTerms = {
                    notice = Notice(
                        title = "Terms of Service",
                        body = "Your identity and messages stay on your devices. Cipher does not claim ownership of what you send.",
                    )
                },
                onPrivacy = {
                    notice = Notice(
                        title = "Privacy Policy",
                        body = "No ad beacons, no device fingerprinting, and no cloud message logs. Registration uses your username only to identify your account.",
                    )
                },
                modifier = Modifier.padding(top = spacing.md),
            )
        }
    }

    notice?.let { current ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = {
                Text(text = current.title, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(text = current.body, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun BrandHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CipherTheme.spacing.lg, bottom = CipherTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(y = (-8).dp)
                .blur(48.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .offset(x = 72.dp, y = 24.dp)
                .blur(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), CircleShape),
        )
        CipherMark()
    }
}

@Composable
private fun PillarCard(
    icon: ImageVector,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(CipherTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(CipherTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LegalLine(
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text = buildAnnotatedString {
        append("By continuing, you agree to Cipher's ")
        withLink(
            LinkAnnotation.Clickable(
                tag = "terms",
                styles = linkStyle,
                linkInteractionListener = { _ -> onTerms() },
            ),
        ) {
            append("Terms of Service")
        }
        append(" and ")
        withLink(
            LinkAnnotation.Clickable(
                tag = "privacy",
                styles = linkStyle,
                linkInteractionListener = { _ -> onPrivacy() },
            ),
        ) {
            append("Privacy Policy")
        }
        append(".")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomePreview() {
    MessengerTheme {
        WelcomeScreen(onCreateAccount = {}, onSignIn = {})
    }
}
