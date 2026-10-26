package com.nauz.livevoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
  val Background = Color(0xFF0D0D12)
  val Surface = Color(0xFF1A1A24)
  val SurfaceHigh = Color(0xFF23232F)
  val Purple = Color(0xFF7C4DFF)
  val Pink = Color(0xFFFF4081)
  val LiveRed = Color(0xFFFF1744)
  val TextPrimary = Color(0xFFF5F5FA)
  val TextSecondary = Color(0xFF9E9EAF)
  val Stroke = Color(0x1AFFFFFF)

  val Accent: Brush = Brush.horizontalGradient(listOf(Purple, Pink))
}

private val LiveVoiceColors = darkColorScheme(
  primary = Palette.Purple,
  onPrimary = Color.White,
  primaryContainer = Palette.Purple,
  onPrimaryContainer = Color.White,
  secondary = Palette.Pink,
  onSecondary = Color.White,
  background = Palette.Background,
  onBackground = Palette.TextPrimary,
  surface = Palette.Surface,
  onSurface = Palette.TextPrimary,
  surfaceVariant = Palette.SurfaceHigh,
  onSurfaceVariant = Palette.TextSecondary,
  outline = Palette.Stroke,
  error = Palette.LiveRed,
  onError = Color.White
)

private val LiveVoiceType = Typography(
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    letterSpacing = (-0.2).sp
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp
  ),
  bodySmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp
  )
)

@Composable
fun LiveVoiceTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = LiveVoiceColors, typography = LiveVoiceType, content = content)
}
