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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauz.livevoice.ui.theme.Palette

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(20.dp),
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable () -> Unit
) {
  Box(
    modifier = modifier
      .clip(shape)
      .background(Palette.Surface.copy(alpha = 0.86f))
      .border(1.dp, Palette.Stroke, shape)
      .padding(contentPadding)
  ) {
    content()
  }
}

@Composable
fun GradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val transition = rememberInfiniteTransition(label = "pulse")
  val pulse by transition.animateFloat(
    initialValue = 1f,
    targetValue = if (enabled) 1.02f else 1f,
    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    label = "pulseScale"
  )
  Box(
    modifier = modifier
      .scale(if (enabled) pulse else 1f)
      .height(56.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(Palette.Accent)
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun PillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  active: Boolean = false,
  accent: Color = Palette.Purple
) {
  val shape = RoundedCornerShape(14.dp)
  Box(
    modifier = modifier
      .height(44.dp)
      .clip(shape)
      .background(if (active) accent.copy(alpha = 0.24f) else Palette.SurfaceHigh)
      .border(1.dp, if (active) accent else Palette.Stroke, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (active) Color.White else Palette.TextSecondary,
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
fun EffectChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier = modifier
      .height(40.dp)
      .clip(shape)
      .background(if (selected) Palette.Accent else SolidColor(Palette.SurfaceHigh))
      .border(1.dp, if (selected) Color.Transparent else Palette.Stroke, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (selected) Color.White else Palette.TextSecondary,
      fontSize = 13.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
    )
  }
}

@Composable
fun LiveBadge(text: String, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "live")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "dotAlpha"
  )
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(Palette.LiveRed.copy(alpha = 0.16f))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(Palette.LiveRed.copy(alpha = alpha))
    )
    Text(
      text = text,
      color = Palette.LiveRed,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
  val bars = 18
  Canvas(modifier = modifier) {
    val slot = size.width / bars
    val barWidth = slot * 0.55f
    for (i in 0 until bars) {
      val threshold = i.toFloat() / bars
      val on = level > threshold
      val barHeight = size.height * (0.25f + 0.75f * threshold)
      drawRoundRect(
        color = if (on) {
          if (threshold > 0.75f) Palette.LiveRed else Palette.Purple
        } else {
          Palette.SurfaceHigh
        },
        topLeft = Offset(i * slot, (size.height - barHeight) / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
      )
    }
  }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text.uppercase(),
    modifier = modifier,
    color = Palette.TextSecondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp
  )
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(Palette.Stroke)
  )
}

@Composable
fun RowSpacer(width: Int) {
  Box(modifier = Modifier.width(width.dp))
}

@Composable
fun ColumnSpacer(height: Int) {
  Column(modifier = Modifier.height(height.dp)) {}
}
