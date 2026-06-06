package com.mediasage.theme

import androidx.compose.ui.graphics.Color

// Clean editorial palette — white + navy, inspired by Figma designs
val White = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF8F8F8)

val Ink = Color(0xFF1A1A1A)
val InkLight = Color(0xFFF5F0E8)

val Navy = Color(0xFF1B2A4A)
val NavyLight = Color(0xFF3D5A80)
val NavyMuted = Color(0xFF6B85A8)

val Charcoal = Color(0xFF2A2A2A)
val CharcoalLight = Color(0xFFE0E0E0)

val Slate = Color(0xFF6B6B6B)
val SlateLight = Color(0xFFB0B0B0)

val CardBorder = Color(0xFFE0E0E0)

val RuleLine = Color(0xFFD0D0D0)
val RuleLineDark = Color(0xFF404040)

val DarkBackground = Color(0xFF1C1A14)  // dark warm brown, newspaper aesthetic
val DarkSurface = Color(0xFF25221A)     // slightly lighter warm dark for card surfaces

val Error = Color(0xFFB00020)
val ErrorDark = Color(0xFFCF6679)

// Brand accent — pinned reporters, memory quote, today ring
val BrandAmber = Color(0xFFD4A050)
val Accent = BrandAmber
val AccentDark = Color(0xFFB8943F)

// Modern palette (mockup-derived — near-black dark, warm cream light, amber accent)
val ModernAmber = Color(0xFFC8A96E)        // amber accent (from mockup)
val ModernDarkBg = Color(0xFF0F0F1A)       // near-black background (dark)
val ModernDarkCard = Color(0xFF1E1E35)     // dark purple card surface
val ModernDarkBorder = Color(0xFF2A2A4A)   // dark purple border / outline
val ModernDarkMuted = Color(0xFF6A6A8A)    // muted purple-gray text (dark)
val ModernDarkText = Color(0xFFE8E0D0)     // warm off-white text (dark)
val ModernLightCard = Color(0xFFF0EBE0)    // warm cream card surface (light)
val ModernLightBorder = Color(0xFFD8D0C0)  // warm border (light)
val ModernLightMuted = Color(0xFF6A5F52)   // warm brown muted text (light)
val ModernLightText = Color(0xFF1C1510)    // warm near-black text (light)
val NavyDeep = Color(0xFF0F0F1A)           // alias for ModernDarkBg (used in theme)

// Warm palette (Kindle e-reader — warm sepia light, warm dark night mode)
val ReaderSurface = Color(0xFFECE6D8)      // warm cream (light mode background + text in dark mode)
val ReaderPrimary = Color(0xFF5C3D2E)      // warm brown primary (light mode)
val ReaderAmber = BrandAmber               // warm amber accent

// Warm dark — deep sepia backgrounds, cream text (inverted from warm light)
val WarmDarkBg = Color(0xFF1A1208)         // deep saturated sepia background
val WarmDarkSurface = Color(0xFF241A0E)    // warm sepia card surface
val WarmDarkMuted = Color(0xFF9A8A72)      // warm tan — muted secondary text
val WarmDarkBorder = Color(0xFF3D2E1E)     // subtle warm brown border
