@file:OptIn(ExperimentalFoundationApi::class)

package com.example.messenger.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Keeps a focused field above the keyboard by scrolling, without shrinking the screen.
 * The keyboard still draws over whatever sits below the field.
 */
@Composable
fun AboveKeyboard(
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    val spec = remember { OverlayBringIntoViewSpec() }
    spec.imePx = imePx
    spec.marginPx = with(density) { 12.dp.toPx() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@Composable
fun Modifier.revealAboveKeyboard(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val imePx = WindowInsets.ime.getBottom(LocalDensity.current)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, imePx) {
        if (focused && imePx > 0) requester.bringIntoView()
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}

private class OverlayBringIntoViewSpec : BringIntoViewSpec {
    var imePx: Int = 0
    var marginPx: Float = 0f

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visibleBottom = (containerSize - imePx).coerceAtLeast(0f)
        val limit = (visibleBottom - marginPx).coerceAtLeast(0f)
        val childBottom = offset + size
        return when {
            offset < 0f -> offset
            childBottom > limit -> childBottom - limit
            else -> 0f
        }
    }
}
