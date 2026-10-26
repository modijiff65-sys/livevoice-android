package com.nauz.livevoice.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.nauz.livevoice.data.Quality
import com.nauz.livevoice.data.StreamSettings
import com.nauz.livevoice.ui.theme.Palette

@Composable
fun SettingsScreen(settings: StreamSettings, streamKey: String, onSaveUrl: (String) -> Unit, onSaveKey: (String) -> Unit, onClearKey: () -> Unit, onQuality: (Quality) -> Unit, onOrientation: (Boolean) -> Unit, onMessage: (String) -> Unit) {
  val context = LocalContext.current
  var urlInput by remember { mutableStateOf(settings.rtmpUrl) }
  var keyInput by remember { mutableStateOf(streamKey) }
  var keyVisible by remember { mutableStateOf(false) }
  var showGuide by remember { mutableStateOf(false) }
  LaunchedEffect(settings.rtmpUrl) { urlInput = settings.rtmpUrl }
  LaunchedEffect(streamKey) { keyInput = streamKey }

  Column(Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Settings", color = Palette.TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    Text("Control your broadcast, quality and secure stream connection.", color = Palette.TextSecondary, fontSize = 12.sp)

    GlassCard(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { Column(Modifier.weight(1f)) { Text("LiveVoice", color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Streaming profile", color = Palette.TextMuted, fontSize = 10.sp) }; MiniStat("VERSION", "1.1") }
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        SectionTitle("YouTube / RTMP")
        OutlinedTextField(urlInput, { urlInput = it }, label = { Text("RTMP URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(keyInput, { keyInput = it }, label = { Text("Stream key") }, singleLine = true, visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PillButton(if (keyVisible) "Hide key" else "Show key", { keyVisible = !keyVisible }, Modifier.weight(1f))
          PillButton("How to get it", { showGuide = true }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GradientButton("SAVE CONNECTION", {
            if (!urlInput.trim().startsWith("rtmp")) onMessage("RTMP URL must start with rtmp:// or rtmps://")
            else if (keyInput.isBlank()) onMessage("Stream key cannot be empty")
            else { onSaveUrl(urlInput.trim()); onSaveKey(keyInput.trim()); onMessage("Connection saved") }
          }, Modifier.weight(1f))
          PillButton("Delete", { keyInput = ""; onClearKey() }, Modifier.weight(.55f), accent = Palette.LiveRed)
        }
        Text("Encrypted on this device. Never shown in logs.", color = Palette.TextMuted, fontSize = 9.sp)
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Stream quality")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Quality.entries.forEach { q -> PillButton(q.label, { onQuality(q) }, Modifier.weight(1f), q == settings.quality) } }
        Text("720p is the balanced mobile default. Higher quality needs more upload bandwidth.", color = Palette.TextMuted, fontSize = 9.sp)
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Orientation")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PillButton("Portrait", { onOrientation(true) }, Modifier.weight(1f), settings.portrait)
          PillButton("Landscape", { onOrientation(false) }, Modifier.weight(1f), !settings.portrait)
        }
      }
    }

    GlassCard(Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("About LiveVoice", color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Real-time RTMP streaming with camera, screen and voice effects. The changed voice is sent directly into the stream.", color = Palette.TextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
      }
    }
  }

  if (showGuide) AlertDialog(onDismissRequest = { showGuide = false }, confirmButton = { TextButton(onClick = { showGuide = false; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/"))) } }) { Text("Open YouTube Studio") } }, dismissButton = { TextButton(onClick = { showGuide = false }) { Text("Close") } }, title = { Text("Get your stream key") }, text = { Text("Open YouTube Studio → Go Live → Stream, then copy the reusable Stream key and paste it here.") })
}
