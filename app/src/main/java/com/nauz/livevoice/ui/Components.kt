package com.nauz.livevoice.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauz.livevoice.ui.theme.Palette

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp), contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
  Box(modifier = modifier.clip(shape).background(Palette.Surface).border(1.dp, Palette.Stroke, shape).padding(contentPadding)) { content() }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val transition = rememberInfiniteTransition(label = "cta")
  val pulse by transition.animateFloat(1f, if (enabled) 1.015f else 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "scale")
  Box(modifier = modifier.scale(pulse).height(54.dp).clip(RoundedCornerShape(17.dp)).background(if (enabled) Palette.Accent else Palette.SurfaceHigh).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
    Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
  }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, accent: Color = Palette.Purple) {
  val shape = RoundedCornerShape(13.dp)
  Box(modifier = modifier.height(42.dp).clip(shape).background(if (active) accent.copy(alpha = .2f) else Palette.SurfaceHigh).border(1.dp, if (active) accent.copy(alpha = .75f) else Palette.Stroke, shape).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
    Text(text, color = if (active) Color.White else Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
fun EffectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(13.dp)
  Box(modifier = modifier.height(42.dp).clip(shape).background(if (selected) Palette.AccentSoft else Palette.SurfaceHigh).border(1.dp, if (selected) Palette.Purple else Palette.Stroke, shape).clickable(onClick = onClick).padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
    Text(label, color = if (selected) Color.White else Palette.TextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
  }
}

@Composable
fun LiveBadge(text: String, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "live")
  val alpha by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dot")
  Row(modifier = modifier.clip(RoundedCornerShape(9.dp)).background(Palette.LiveRed.copy(alpha = .16f)).padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.LiveRed.copy(alpha = alpha)))
    Text(text, color = Palette.LiveRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
  }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
  val bars = 18
  Canvas(modifier) {
    val slot = size.width / bars
    val barWidth = slot * .55f
    for (i in 0 until bars) {
      val threshold = i.toFloat() / bars
      val on = level > threshold
      val h = size.height * (.25f + .75f * threshold)
      drawRoundRect(if (on) if (threshold > .78f) Palette.LiveRed else Palette.Purple else Palette.SurfaceHigh, Offset(i * slot, (size.height - h) / 2f), Size(barWidth, h), CornerRadius(barWidth / 2, barWidth / 2))
    }
  }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text.uppercase(), modifier, color = Palette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

@Composable fun DividerLine(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(Palette.Stroke)) }
@Composable fun RowSpacer(width: Int) { Box(Modifier.width(width.dp)) }
@Composable fun ColumnSpacer(height: Int) { Box(Modifier.height(height.dp)) }

@Composable
fun IconSquare(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
  Box(modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (active) Palette.AccentSoft else Palette.SurfaceHigh).border(1.dp, if (active) Palette.Purple else Palette.Stroke, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
    Text(text, fontSize = 19.sp, color = Color.White)
  }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier.clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(value, color = Palette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(label, color = Palette.TextMuted, fontSize = 9.sp)
  }
}
