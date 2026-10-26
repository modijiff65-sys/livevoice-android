package com.nauz.livevoice.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onDispose {
      tester.cancel()
      player?.let { runCatching { it.release() } }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Palette.Background)
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "Voice Effects",
        color = Palette.TextPrimary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Tap a preset to hear it on stream instantly. The audience hears the effect, your phone speaker stays silent.",
        color = Palette.TextSecondary,
        fontSize = 13.sp
      )
    }

    VoicePreset.ALL.chunked(2).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { item ->
          PresetTile(
            preset = item,
            selected = item.id == preset.id,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(item) }
          )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(text = "Custom tuning")
        Text(
          text = "Pitch ${"%.1f".format(preset.pitch)} semitones",
          color = Palette.TextPrimary,
          fontSize = 14.sp
        )
        Slider(
          value = preset.pitch,
          onValueChange = { onCustomChange(it, preset.formant) },
          valueRange = -12f..12f
        )
        Text(
          text = "Formant ${"%.2f".format(preset.formant)}x",
          color = Palette.TextPrimary,
          fontSize = 14.sp
        )
        Slider(
          value = preset.formant,
          onValueChange = { onCustomChange(preset.pitch, it) },
          valueRange = 0.7f..1.4f
        )
        Text(
          text = "Formant makes the voice sound bigger (below 1.0) or smaller (above 1.0) without touching pitch.",
          color = Palette.TextSecondary,
          fontSize = 12.sp
        )
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = "Test before going live")
        Text(
          text = "Records 5 seconds with the selected effect and plays it back on the speaker. Nothing is streamed.",
          color = Palette.TextSecondary,
          fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton(
            text = when {
              testing -> "Recording..."
              playing -> "Playing..."
              else -> "Record 5s test"
            },
            onClick = {
              if (testing || playing) return@PillButton
              testing = true
              tester.start(context, preset, 5) { result ->
                handler.post {
                  testing = false
                  result.onSuccess { file ->
                    val created = playFile(context, file) {
                      handler.post {
                        playing = false
                        player = null
                      }
                    }
                    if (created == null) {
                      playing = false
                      onMessage("Could not play the test recording")
                    } else {
                      playing = true
                      player = created
                    }
                  }.onFailure { error ->
                    onMessage(error.message ?: "Test recording failed")
                  }
                }
              }
            },
            active = testing || playing,
            modifier = Modifier.weight(1f)
          )
          if (testing) {
            PillButton(text = "Cancel", onClick = {
              tester.cancel()
              testing = false
            })
          }
        }
      }
    }

    Text(
      text = "Tip: presets marked with echo and radio work best with headphones for the audience. The app never plays your changed voice back into the mic, so there is no feedback loop.",
      color = Palette.TextSecondary,
      fontSize = 12.sp
    )
  }
}

@Composable
private fun PresetTile(
  preset: VoicePreset,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(18.dp)
  Box(
    modifier = modifier
      .height(84.dp)
      .clip(shape)
      .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
      .clickable(onClick = onClick)
  ) {
    if (selected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Palette.Accent.copy(alpha = 0.22f))
      )
    }
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = preset.label,
        color = if (selected) Color.White else Palette.TextPrimary,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = describe(preset),
        color = Palette.TextSecondary,
        fontSize = 11.sp
      )
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
