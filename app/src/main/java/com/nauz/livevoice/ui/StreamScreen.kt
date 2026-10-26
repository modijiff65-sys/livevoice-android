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
  screenMode: Boolean,
  onToggleScreenMode: (Boolean) -> Unit,
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
  val isLive = state.status == StreamController.Status.LIVE || state.status == StreamController.Status.RETRYING
  var previewView by remember { mutableStateOf<OpenGlView?>(null) }

  Box(Modifier.fillMaxSize().background(Palette.Background)) {
    if (screenMode) {
      Box(Modifier.fillMaxSize().background(Palette.Hero), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          IconSquare("▣", active = true)
          Text("Screen broadcast", color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
          Text("Share your screen with the same real-time voice processing. Android will ask you to confirm what to share when you go live.", color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
      }
    } else if (permissionsGranted) {
      AndroidView(factory = { context -> OpenGlView(context).also { previewView = it } }, modifier = Modifier.fillMaxSize())
      LaunchedEffect(previewView, settings.quality, settings.portrait) { previewView?.let { onPreview(it) } }
    } else {
      Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
        Text("Camera and microphone permission is needed to go live", color = Palette.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
      }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.68f), Color.Transparent, Color.Black.copy(.9f)))))

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Live", color = Palette.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text("Voice", color = Palette.Purple, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        if (isLive) {
          LiveBadge(if (state.status == StreamController.Status.RETRYING) "RECONNECTING" else "LIVE")
          Spacer(Modifier.width(8.dp))
          StatusPill("${state.bitrateKbps} kbps")
        } else {
          StatusPill(if (screenMode) "SCREEN" else "CAMERA")
        }
      }

      if (!isLive) {
        Spacer(Modifier.weight(1f))
        HeroHomeCard(onGoLive = onGoLive)
      } else {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          LiveTimer(state.startedAt)
        }
        Spacer(Modifier.weight(1f))
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Broadcast", color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
              Spacer(Modifier.weight(1f))
              Text(if (screenMode) "Screen" else "Camera", color = Palette.TextSecondary, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              EffectChip("Camera", !screenMode, { onToggleScreenMode(false) }, Modifier.weight(1f))
              EffectChip("Screen", screenMode, { onToggleScreenMode(true) }, Modifier.weight(1f))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(VoicePreset.ALL) { item ->
                EffectChip(item.label, item.id == preset.id, { onSelectPreset(item) })
              }
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          PillButton(if (muted) "Unmute" else "Mute", onToggleMute, Modifier.weight(1f), muted, Palette.LiveRed)
          if (!screenMode) PillButton("Flip", onSwitchCamera, Modifier.weight(1f))
          if (!screenMode) PillButton(if (flashOn) "Light off" else "Flash", onToggleFlash, Modifier.weight(1f), flashOn, Palette.Blue)
          if (isLive) PillButton("Chat", onOpenChat, Modifier.weight(1f), true, Palette.Blue)
        }

        GradientButton(if (isLive) "STOP STREAM" else if (screenMode) "SHARE SCREEN & GO LIVE" else "GO LIVE", if (isLive) onStop else onGoLive, Modifier.fillMaxWidth(), state.status != StreamController.Status.CONNECTING && permissionsGranted)
        if (!isLive) Text("YouTube • Ready to stream", color = Palette.TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        state.message?.let { Text(it, color = Palette.LiveRed, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
      }
    }

    if (isLive) LevelMeter(level, Modifier.align(Alignment.CenterStart).padding(start = 7.dp).width(7.dp).height(150.dp))
  }
}

@Composable
private fun HeroHomeCard(onGoLive: () -> Unit) {
  GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
    Box(Modifier.fillMaxWidth().background(Palette.Hero).padding(18.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconSquare("◉", active = true)
          Spacer(Modifier.width(12.dp))
          Column {
            Text("Ready to Go Live?", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("Start streaming with your camera or screen.", color = Palette.TextSecondary, fontSize = 11.sp)
          }
        }
        GradientButton("GO LIVE  →", onGoLive, Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable private fun StatusPill(text: String) = Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Palette.Surface.copy(.82f)).padding(horizontal = 9.dp, vertical = 6.dp)) { Text(text, color = Palette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

@Composable private fun LiveTimer(startedAt: Long) {
  val elapsed by produceState(0L, startedAt) {
    if (startedAt <= 0) return@produceState
    while (true) { value = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0); delay(1000) }
  }
  Text("%02d:%02d".format(elapsed / 60, elapsed % 60), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
