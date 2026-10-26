package com.nauz.livevoice.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauz.livevoice.audio.VoicePreset
import com.nauz.livevoice.audio.VoiceTester
import com.nauz.livevoice.ui.theme.Palette
import java.io.File

@Composable
fun EffectsScreen(
  preset: VoicePreset,
  onSelect: (VoicePreset) -> Unit,
  onCustomChange: (Float, Float) -> Unit,
  onMessage: (String) -> Unit
) {
  val context = LocalContext.current
  val tester = remember { VoiceTester() }
  val handler = remember { Handler(Looper.getMainLooper()) }
  var testing by remember { mutableStateOf(false) }
  var playing by remember { mutableStateOf(false) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(Unit) {
    onDispose { tester.cancel(); player?.let { runCatching { it.release() } } }
  }

  Column(Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.weight(1f)) {
        Text("Voice", color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Studio", color = Palette.Purple, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
      }
      MiniStat("ACTIVE", preset.label, Modifier)
    }

    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Voice Presets", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f)); Text("${VoicePreset.ALL.size} effects", color = Palette.TextMuted, fontSize = 10.sp)
        }
        VoicePreset.ALL.chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item -> PresetTile(item, item.id == preset.id, Modifier.weight(1f)) { onSelect(item) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Fine Tune", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("LIVE", color = Palette.Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Text("Pitch  ${"%.1f".format(preset.pitch)}", color = Palette.TextSecondary, fontSize = 11.sp)
        Slider(value = preset.pitch, onValueChange = { onCustomChange(it, preset.formant) }, valueRange = -12f..12f)
        Text("Formant  ${"%.2f".format(preset.formant)}x", color = Palette.TextSecondary, fontSize = 11.sp)
        Slider(value = preset.formant, onValueChange = { onCustomChange(preset.pitch, it) }, valueRange = .7f..1.4f)
        Text("Adjust pitch and character without changing the stream controls.", color = Palette.TextMuted, fontSize = 10.sp)
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Voice Preview", color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          Text("Record a short local test. It is not streamed.", color = Palette.TextMuted, fontSize = 10.sp)
        }
        PillButton(when { testing -> "Recording"; playing -> "Playing"; else -> "Test 5s" }, {
          if (testing || playing) return@PillButton
          testing = true
          tester.start(context, preset, 5) { result ->
            handler.post {
              testing = false
              result.onSuccess { file ->
                val created = playFile(context, file) { handler.post { playing = false; player = null } }
                if (created == null) { playing = false; onMessage("Could not play the test recording") } else { playing = true; player = created }
              }.onFailure { onMessage(it.message ?: "Test recording failed") }
            }
          }
        }, active = testing || playing)
      }
    }
  }
}

@Composable
private fun PresetTile(preset: VoicePreset, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val shape = RoundedCornerShape(15.dp)
  Box(modifier.height(82.dp).clip(shape).background(if (selected) Palette.AccentSoft else Palette.SurfaceHigh).border(1.dp, if (selected) Palette.Purple else Palette.Stroke, shape).clickable(onClick = onClick)) {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
      Text(if (preset.id == "custom") "✦" else "◉", color = if (selected) Palette.Cyan else Palette.Purple, fontSize = 16.sp)
      Text(preset.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      Text(describe(preset), color = Palette.TextMuted, fontSize = 8.sp, maxLines = 1)
    }
  }
}

private fun describe(preset: VoicePreset): String {
  if (preset.id == "custom") return "Your own pitch and formant"
  val parts = mutableListOf<String>()
  if (preset.pitch != 0f) parts += "${if (preset.pitch > 0) "+" else ""}${preset.pitch.toInt()} st"
  if (preset.formant != 1f) parts += "formant ${"%.2f".format(preset.formant)}"
  if (preset.ringHz > 0f) parts += "ring ${preset.ringHz.toInt()} Hz"
  if (preset.echoMs > 0) parts += "echo ${preset.echoMs} ms"
  if (preset.drive > 1.01f) parts += "drive ${"%.1f".format(preset.drive)}"
  if (preset.bandpass) parts += "radio band"
  return if (parts.isEmpty()) "Clean, unchanged voice" else parts.joinToString(" · ")
}

private fun playFile(context: android.content.Context, file: File, onDone: () -> Unit): MediaPlayer? {
  return runCatching {
    MediaPlayer.create(context, Uri.fromFile(file))?.apply {
      setOnCompletionListener {
        it.release()
        onDone()
      }
      start()
    }
  }.getOrNull()
}
