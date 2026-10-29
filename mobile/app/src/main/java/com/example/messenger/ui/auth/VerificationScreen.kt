package com.example.messenger.ui.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.messenger.ui.components.KeypadIconButton
import com.example.messenger.ui.components.NumericKeypad
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VerifyPhase {
    Waiting,
    Checking,
    Verified,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    phoneLabel: String,
    code: String,
    onCodeChange: (String) -> Unit,
    phase: VerifyPhase,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    onEditPhone: () -> Unit,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
    resendGeneration: Int = 0,
    statusMessage: String? = null,
    onStatusShown: () -> Unit = {},
) {
    var secondsLeft by remember(resendGeneration) { mutableIntStateOf(42) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val spacing = CipherTheme.spacing
    val entryLocked = phase == VerifyPhase.Checking || phase == VerifyPhase.Verified

    LaunchedEffect(secondsLeft) {
        if (secondsLeft == 0) return@LaunchedEffect
        delay(1_000)
        secondsLeft -= 1
    }

    LaunchedEffect(phase) {
        if (phase != VerifyPhase.Verified) return@LaunchedEffect
        delay(650)
        onVerified()
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Enter code",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = spacing.sm)
                            .size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShieldBadge(modifier = Modifier.padding(top = spacing.md))
            Text(
                text = "Enter code",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = spacing.lg),
            )
            Row(
                modifier = Modifier.padding(top = spacing.xs, bottom = spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sent to",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = phoneLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onEditPhone),
                )
            }

            OtpRow(code = code)
            Spacer(Modifier.height(spacing.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (secondsLeft > 0) {
                    Text(
                        text = "Resend code in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  0:${secondsLeft.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Resend code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onResend),
                    )
                }
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "Call me",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        scope.launch {
                            snackbarHostState.showSnackbar("Calling $phoneLabel is not connected in this build.")
                        }
                    },
                )
            }

            Spacer(Modifier.weight(1f))
            NumericKeypad(
                onDigit = { digit ->
                    if (code.length < 6 && !entryLocked) {
                        val next = code + digit
                        onCodeChange(next)
                        if (next.length == 6) onSubmit(next)
                    }
                },
                onBackspace = {
                    if (code.isNotEmpty() && !entryLocked) {
                        onCodeChange(code.dropLast(1))
                    }
                },
                keyHeight = 56.dp,
                showLetters = true,
                filledBackspace = false,
                digitStyle = MaterialTheme.typography.headlineMedium,
                letterColor = MaterialTheme.colorScheme.onSurfaceVariant,
                keyCorner = 16.dp,
                containerAlpha = 0.7f,
                leading = {
                    KeypadIconButton(
                        icon = Icons.Filled.ContentPaste,
                        contentDescription = "Paste code",
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty().filter { it.isDigit() }.take(6)
                            if (pasted.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No digits on the clipboard.")
                                }
                            } else if (!entryLocked) {
                                onCodeChange(pasted)
                                if (pasted.length == 6) onSubmit(pasted)
                            }
                        },
                    )
                },
                modifier = Modifier.padding(bottom = spacing.sm),
            )
        }
    }
}

@Composable
private fun ShieldBadge(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val ring = MaterialTheme.colorScheme.primaryFixedDim
    val glow = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = glow, radius = size.minDimension / 2f)
            drawCircle(
                color = primary.copy(alpha = 0.12f),
                radius = size.minDimension * 0.45f,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                ),
            )
            drawCircle(
                color = secondary,
                radius = size.minDimension * 0.375f,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())),
                ),
            )
            drawCircle(
                color = ring,
                radius = size.minDimension * 0.275f,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun OtpRow(code: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val cellWidth = (maxWidth - gap * 5) / 6
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(6) { index ->
                OtpCell(
                    digit = code.getOrNull(index),
                    active = index == code.length && code.length < 6,
                    modifier = Modifier.size(width = cellWidth, height = cellWidth * 1.28f),
                )
            }
        }
    }
}

@Composable
private fun OtpCell(
    digit: Char?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val background = when {
        digit != null -> MaterialTheme.colorScheme.surfaceContainerHigh
        active -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Box(
        modifier = modifier
            .zIndex(if (active) 1f else 0f)
            .scale(if (active) 1.05f else 1f)
            .then(
                if (active) {
                    Modifier.shadow(6.dp, shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            active -> Caret()
            else -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun Caret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VerificationPreview() {
    var code by remember { mutableStateOf("") }
    MessengerTheme {
        VerificationScreen(
            phoneLabel = "+234 801 234 5678",
            code = code,
            onCodeChange = { code = it },
            phase = VerifyPhase.Waiting,
            onSubmit = {},
            onResend = {},
            onBack = {},
            onEditPhone = {},
            onVerified = {},
        )
    }
}
