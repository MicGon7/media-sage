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
): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = 45.sp,
            lineHeight = 52.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = headlineFont,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}
