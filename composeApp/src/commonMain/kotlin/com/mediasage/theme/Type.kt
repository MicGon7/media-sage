package com.mediasage.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.lora
import mediasage.composeapp.generated.resources.lora_italic
import mediasage.composeapp.generated.resources.playfair_display
import mediasage.composeapp.generated.resources.playfair_display_italic
import org.jetbrains.compose.resources.Font

val PlayfairDisplayFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.playfair_display, FontWeight.Normal),
        Font(Res.font.playfair_display, FontWeight.Medium),
        Font(Res.font.playfair_display, FontWeight.SemiBold),
        Font(Res.font.playfair_display, FontWeight.Bold),
        Font(Res.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.playfair_display_italic, FontWeight.Bold, FontStyle.Italic),
    )

val LoraFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.lora, FontWeight.Normal),
        Font(Res.font.lora, FontWeight.Medium),
        Font(Res.font.lora, FontWeight.SemiBold),
        Font(Res.font.lora, FontWeight.Bold),
        Font(Res.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.lora_italic, FontWeight.Bold, FontStyle.Italic),
    )

@Composable
fun mediaSageTypography(
    headlineFont: FontFamily = PlayfairDisplayFamily,
    bodyFont: FontFamily = LoraFamily,
    scale: Float = 1f,
): Typography {
    fun scaledSp(baseSp: Int) = (baseSp * scale).sp
    return Typography(
        displayLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(57),
            lineHeight = scaledSp(64),
        ),
        displayMedium = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(45),
            lineHeight = scaledSp(52),
        ),
        displaySmall = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = scaledSp(36),
            lineHeight = scaledSp(44),
        ),
        headlineLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaledSp(32),
            lineHeight = scaledSp(40),
        ),
        headlineMedium = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaledSp(28),
            lineHeight = scaledSp(36),
        ),
        headlineSmall = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaledSp(24),
            lineHeight = scaledSp(32),
        ),
        titleLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(22),
            lineHeight = scaledSp(28),
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(16),
            lineHeight = scaledSp(24),
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(14),
            lineHeight = scaledSp(20),
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp(17),
            lineHeight = scaledSp(26),
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp(14),
            lineHeight = scaledSp(20),
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = scaledSp(12),
            lineHeight = scaledSp(16),
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(14),
            lineHeight = scaledSp(20),
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(12),
            lineHeight = scaledSp(16),
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = scaledSp(11),
            lineHeight = scaledSp(16),
            letterSpacing = 0.5.sp,
        ),
    )
}
