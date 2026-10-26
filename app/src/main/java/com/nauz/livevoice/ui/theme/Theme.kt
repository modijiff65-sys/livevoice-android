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
  val Background = Color(0xFF050713)
  val Background2 = Color(0xFF090D1D)
  val Surface = Color(0xFF0D1427)
  val SurfaceHigh = Color(0xFF151E35)
  val SurfaceSoft = Color(0xFF10182D)
  val Purple = Color(0xFF8B5CFF)
  val Violet = Color(0xFF5C4DFF)
  val Blue = Color(0xFF20A7FF)
  val Cyan = Color(0xFF22D3EE)
  val Pink = Color(0xFFE45BFF)
  val LiveRed = Color(0xFFFF3B4D)
  val Green = Color(0xFF36D98A)
  val TextPrimary = Color(0xFFF7F8FF)
  val TextSecondary = Color(0xFF8D97B2)
  val TextMuted = Color(0xFF5E6985)
  val Stroke = Color(0x1FFFFFFF)
  val StrokeStrong = Color(0x33FFFFFF)
  val Accent: Brush = Brush.horizontalGradient(listOf(Blue, Purple, Pink))
  val AccentSoft: Brush = Brush.horizontalGradient(listOf(Violet, Purple))
  val Hero: Brush = Brush.linearGradient(listOf(Color(0xFF241A68), Color(0xFF121B55), Color(0xFF0B1530)))
}

private val Colors = darkColorScheme(
  primary = Palette.Purple,
  onPrimary = Color.White,
  primaryContainer = Palette.Purple,
  onPrimaryContainer = Color.White,
  secondary = Palette.Blue,
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

private val Type = Typography(
  displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1.2).sp),
  headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
  titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
  titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
  bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
  bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
  bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp),
  labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp),
  labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp)
)

@Composable
fun LiveVoiceTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
