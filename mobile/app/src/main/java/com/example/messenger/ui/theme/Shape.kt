package com.example.messenger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cipher shape scale, from the design system's `rounded` tokens.
 *
 * Material maps these onto [androidx.compose.material3.MaterialTheme.shapes]:
 * extraSmall, small, medium, large, extraLarge. Components pick a step
 * (`MaterialTheme.shapes.medium`) instead of hard-coding a corner radius.
 *
 * Pills are not a step on this scale. Buttons that should be fully rounded
 * pass `RoundedCornerShape(50)` themselves. That is the design's `full` token.
 *
 * | Token    | Radius | Material step   | Used for                          |
 * |----------|--------|-----------------|-----------------------------------|
 * | sm       | 4.dp   | extraSmall      | micro padding, tiny chips         |
 * | default  | 8.dp   | small           | compact controls                  |
 * | md       | 12.dp  | medium          | inputs, cards, profile button     |
 * | lg       | 16.dp  | large           | keypad wells                      |
 * | xl       | 24.dp  | extraLarge      | bottom sheets, large panels       |
 */
val CipherShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
