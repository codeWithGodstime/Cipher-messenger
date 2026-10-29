package com.example.messenger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw color tokens from the Cipher design system.
 *
 * These are the hex values from the Stitch theme. They are not what screens
 * should read. A screen asks for a *role* ("primary", "surface container"),
 * and [Theme.kt] decides which token fills that role in light and dark mode.
 *
 * Naming follows Material 3: a color named `onPrimary` is the content color
 * that sits on top of `primary` (usually label text or an icon).
 */

val Primary = Color(0xFF004AC6)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF2563EB)
val OnPrimaryContainer = Color(0xFFEEEFFF)
val PrimaryFixed = Color(0xFFDBE1FF)
val PrimaryFixedDim = Color(0xFFB4C5FF)
val OnPrimaryFixed = Color(0xFF00174B)
val OnPrimaryFixedVariant = Color(0xFF003EA8)
val InversePrimary = Color(0xFFB4C5FF)
val SurfaceTint = Color(0xFF0053DB)

val Secondary = Color(0xFF006A61)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFF86F2E4)
val OnSecondaryContainer = Color(0xFF006F66)
val SecondaryFixed = Color(0xFF89F5E7)
val SecondaryFixedDim = Color(0xFF6BD8CB)
val OnSecondaryFixed = Color(0xFF00201D)
val OnSecondaryFixedVariant = Color(0xFF005049)

val Tertiary = Color(0xFF943700)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFBC4800)
val OnTertiaryContainer = Color(0xFFFFEDE6)
val TertiaryFixed = Color(0xFFFFDBCD)
val TertiaryFixedDim = Color(0xFFFFB596)
val OnTertiaryFixed = Color(0xFF360F00)
val OnTertiaryFixedVariant = Color(0xFF7D2D00)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

val Background = Color(0xFFFAF8FF)
val OnBackground = Color(0xFF191B23)
val Surface = Color(0xFFFAF8FF)
val OnSurface = Color(0xFF191B23)
val SurfaceVariant = Color(0xFFE1E2ED)
val OnSurfaceVariant = Color(0xFF434655)
val SurfaceDim = Color(0xFFD9D9E5)
val SurfaceBright = Color(0xFFFAF8FF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF3F3FE)
val SurfaceContainer = Color(0xFFEDEDF9)
val SurfaceContainerHigh = Color(0xFFE7E7F3)
val SurfaceContainerHighest = Color(0xFFE1E2ED)
val InverseSurface = Color(0xFF2E3039)
val InverseOnSurface = Color(0xFFF0F0FB)
val Outline = Color(0xFF737686)
val OutlineVariant = Color(0xFFC3C6D7)

/**
 * Dark surfaces are a cool charcoal ramp. The design note specifies #121212
 * for the base and says dark elevation comes from lighter surfaces, not shadows.
 */
val DarkBackground = Color(0xFF121212)
val DarkOnBackground = Color(0xFFE6E4EF)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceDim = Color(0xFF121212)
val DarkSurfaceBright = Color(0xFF393A42)
val DarkSurfaceContainerLowest = Color(0xFF0D0D0F)
val DarkSurfaceContainerLow = Color(0xFF1A1B20)
val DarkSurfaceContainer = Color(0xFF1E1F26)
val DarkSurfaceContainerHigh = Color(0xFF282A32)
val DarkSurfaceContainerHighest = Color(0xFF33343D)
val DarkOnSurface = Color(0xFFE6E4EF)
val DarkOnSurfaceVariant = Color(0xFFC3C6D7)
val DarkOutline = Color(0xFF8D90A0)
val DarkOutlineVariant = Color(0xFF434655)
val DarkInverseSurface = Color(0xFFE6E4EF)
val DarkInverseOnSurface = Color(0xFF191B23)

/** Brand cobalt stays the action color in dark mode, so buttons do not flip to a pale tint. */
val DarkPrimary = Color(0xFF2563EB)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF004AC6)
val DarkOnPrimaryContainer = Color(0xFFDBE1FF)

val DarkSecondary = Color(0xFF6BD8CB)
val DarkOnSecondary = Color(0xFF003732)
val DarkSecondaryContainer = Color(0xFF005049)
val DarkOnSecondaryContainer = Color(0xFF89F5E7)

val DarkTertiary = Color(0xFFFFB596)
val DarkOnTertiary = Color(0xFF5A1C00)
val DarkTertiaryContainer = Color(0xFF7D2D00)
val DarkOnTertiaryContainer = Color(0xFFFFDBCD)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
