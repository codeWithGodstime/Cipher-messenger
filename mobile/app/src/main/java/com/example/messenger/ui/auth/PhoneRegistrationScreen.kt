package com.example.messenger.ui.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.messenger.ui.components.CipherPrimaryButton
import com.example.messenger.ui.components.KeypadIconButton
import com.example.messenger.ui.components.NumericKeypad
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme
import kotlinx.coroutines.launch

@Composable
fun PhoneRegistrationScreen(
    country: Country,
    digits: String,
    onCountryChange: (Country) -> Unit,
    onDigitsChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var numberActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val spacing = CipherTheme.spacing
    val complete = digits.length == country.digitCount
    val filtered = Countries.filter {
        val haystack = "${it.name} ${it.dialCode}".lowercase()
        haystack.contains(query.trim().lowercase())
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
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
                        text = "Your phone number",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "We'll text you a 6-digit verification code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CountryField(
                    country = country,
                    expanded = pickerOpen,
                    onToggle = { pickerOpen = !pickerOpen },
                )
                if (pickerOpen) {
                    CountryList(
                        countries = filtered,
                        query = query,
                        onQueryChange = { query = it },
                        onSelect = {
                            onCountryChange(it)
                            pickerOpen = false
                            query = ""
                        },
                    )
                }

                PhoneField(
                    country = country,
                    digits = digits,
                    active = numberActive,
                    onActivate = { numberActive = true },
                    onClear = { onDigitsChange("") },
                )
            }

            PrivacyBanner()
            Spacer(Modifier.size(spacing.md))
            CipherPrimaryButton(
                text = "Continue",
                onClick = onContinue,
                enabled = complete,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.size(spacing.sm))
            NumericKeypad(
                onDigit = { digit ->
                    numberActive = true
                    if (digits.length < country.digitCount) {
                        onDigitsChange(digits + digit)
                    }
                },
                onBackspace = {
                    numberActive = true
                    if (digits.isNotEmpty()) onDigitsChange(digits.dropLast(1))
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
                        contentDescription = "Paste number",
                        onClick = {
                            val pasted = clipboard.getText()
                                ?.text
                                .orEmpty()
                                .filter { it.isDigit() }
                                .take(country.digitCount)
                            if (pasted.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No digits on the clipboard.")
                                }
                            } else {
                                numberActive = true
                                onDigitsChange(pasted)
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
private fun CountryField(
    country: Country,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = country.flag, style = MaterialTheme.typography.headlineSmall)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Country / Region",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = country.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = country.dialCode,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide countries" else "Show countries",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun CountryList(
    countries: List<Country>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (Country) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp,
    ) {
        Column {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search country or code",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                countries.forEach { country ->
                    Surface(
                        onClick = { onSelect(country) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = country.flag)
                                Text(
                                    text = country.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = country.dialCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneField(
    country: Country,
    digits: String,
    active: Boolean,
    onActivate: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onActivate,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier.border(width = 2.dp, color = accent, shape = shape)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = if (active) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shadowElevation = if (active) 4.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Phone number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = country.dialCode,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatNational(digits, country).ifEmpty { phonePlaceholder(country) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (digits.isEmpty()) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                if (digits.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Clear number",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun phonePlaceholder(country: Country): String {
    if (country.dialCode == "+1") return "(555) 000-0000"
    return "0".repeat(country.digitCount).chunked(3).joinToString(" ")
}

@Composable
private fun PrivacyBanner() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "  End-to-end encrypted · Never shared",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PhonePreview() {
    MessengerTheme {
        PhoneRegistrationScreen(
            country = Countries.first(),
            digits = "555019",
            onCountryChange = {},
            onDigitsChange = {},
            onBack = {},
            onContinue = {},
        )
    }
}
