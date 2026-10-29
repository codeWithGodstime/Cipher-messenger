package com.example.messenger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing is not part of [androidx.compose.material3.MaterialTheme].
 * Color, type, and shape are. Spacing is published through a CompositionLocal
 * so screens still read it the same way they read the rest of the theme:
 * one value, provided at the root, available to every child.
 *
 * The scale is an 8.dp grid, matching the Cipher tokens:
 * xs 4, sm 8, md 16, lg 24, xl 32. Gutters and screen margins are 16.dp.
 */
data class CipherSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val gutter: Dp = 16.dp,
)

val LocalCipherSpacing = staticCompositionLocalOf { CipherSpacing() }

/**
 * Read spacing from the current composition. Call this inside a composable
 * that sits under [MessengerTheme].
 */
object CipherTheme {
    val spacing: CipherSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalCipherSpacing.current
}
