package com.nauz.livevoice.ui

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nauz.livevoice.audio.VoicePreset
import com.nauz.livevoice.data.StreamSettings
import com.nauz.livevoice.stream.StreamController
import com.nauz.livevoice.ui.theme.Palette
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.delay

@Composable
fun StreamScreen(
  settings: StreamSettings,
  state: StreamController.State,
  level: Float,
  preset: VoicePreset,
  permissionsGranted: Boolean,
  muted: Boolean,
  flashOn: Boolean,
  onPreview: (SurfaceView) -> Unit,
  onGoLive: () -> Unit,
  onStop: () -> Unit,
  onToggleMute: () -> Unit,
  onSwitchCamera: () -> Unit,
  onToggleFlash: () -> Unit,
  onSelectPreset: (VoicePreset) -> Unit,
  onOpenChat: () -> Unit,
  onOpenSettings: () -> Unit
) {
  val isLive = state.status == StreamController.Status.LIVE ||
    state.status == StreamController.Status.RETRYING

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

    if (permissionsGranted) {
      var previewView by remember { mutableStateOf<OpenGlView?>(null) }
      AndroidView(
        factory = { context -> OpenGlView(context).also { previewView = it } },
        modifier = Modifier.fillMaxSize()
      )
      LaunchedEffect(previewView, settings.quality, settings.portrait) {
        previewView?.let { onPreview(it) }
      }
    } else {
      Box(
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Camera and microphone permission is needed to go live",
          color = Palette.TextSecondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(32.dp)
        )
      }
    }

    // Dark gradient so overlay text stays readable on any camera frame.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.55f),
            0.28f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.85f)
          )
        )
    )

    // Top status row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isLive) {
        LiveBadge(text = if (state.status == StreamController.Status.RETRYING) "RECONNECTING" else "LIVE")
        Spacer(Modifier.width(10.dp))
        LiveTimer(startedAt = state.startedAt)
        Spacer(Modifier.width(10.dp))
        StatusPill("${state.bitrateKbps} kbps")
      } else {
        Text(
          text = "LiveVoice",
          color = Palette.TextPrimary,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        StatusPill("${settings.quality.label} · ${if (settings.portrait) "Portrait" else "Landscape"}")
      }
      Spacer(Modifier.weight(1f))
      PillButton(text = "Settings", onClick = onOpenSettings)
    }

    if (isLive) {
      LevelMeter(
        level = level,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 16.dp)
          .width(8.dp)
          .height(160.dp)
      )
    }

    // Bottom controls
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SectionTitle(text = "Voice effect")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VoicePreset.ALL) { item ->
              val selected = item.id == preset.id
              EffectChip(
                label = item.label,
                selected = selected,
                onClick = { onSelectPreset(item) }
              )
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
          text = if (muted) "Unmute" else "Mute",
          onClick = onToggleMute,
          active = muted,
          accent = Palette.LiveRed
        )
        PillButton(text = "Flip", onClick = onSwitchCamera)
        PillButton(
          text = if (flashOn) "Light off" else "Light",
          onClick = onToggleFlash,
          active = flashOn
        )
        if (isLive) {
          PillButton(text = "Chat", onClick = onOpenChat)
        }
      }

      if (isLive) {
        GradientButton(text = "STOP STREAM", onClick = onStop)
      } else {
        GradientButton(
          text = if (state.status == StreamController.Status.CONNECTING) "CONNECTING..." else "GO LIVE",
          onClick = onGoLive,
          enabled = state.status != StreamController.Status.CONNECTING && permissionsGranted
        )
      }

      state.message?.let { message ->
        Text(
          text = message,
          color = Palette.LiveRed,
          fontSize = 13.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun StatusPill(text: String) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(Palette.Surface.copy(alpha = 0.72f))
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(text = text, color = Palette.TextSecondary, fontSize = 12.sp)
  }
}

@Composable
private fun LiveTimer(startedAt: Long) {
  val elapsed by produceState(0L, startedAt) {
    if (startedAt <= 0L) {
      value = 0L
      return@produceState
    }
    while (true) {
      value = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
      delay(1000L)
    }
  }
  val minutes = elapsed / 60
  val seconds = elapsed % 60
  Text(
    text = "%02d:%02d".format(minutes, seconds),
    color = Palette.TextPrimary,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold
  )
}
