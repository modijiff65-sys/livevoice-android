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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauz.livevoice.data.Quality
import com.nauz.livevoice.data.StreamSettings
import com.nauz.livevoice.ui.theme.Palette

@Composable
fun SettingsScreen(
  settings: StreamSettings,
  streamKey: String,
  onSaveUrl: (String) -> Unit,
  onSaveKey: (String) -> Unit,
  onClearKey: () -> Unit,
  onQuality: (Quality) -> Unit,
  onOrientation: (Boolean) -> Unit,
  onMessage: (String) -> Unit
) {
  val context = LocalContext.current
  var urlInput by remember { mutableStateOf(settings.rtmpUrl) }
  var keyInput by remember { mutableStateOf(streamKey) }
  var keyVisible by remember { mutableStateOf(false) }
  var showGuide by remember { mutableStateOf(false) }

  LaunchedEffect(settings.rtmpUrl) { urlInput = settings.rtmpUrl }
  LaunchedEffect(streamKey) { keyInput = streamKey }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Palette.Background)
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Settings",
      color = Palette.TextPrimary,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(text = "YouTube RTMP")
        OutlinedTextField(
          value = urlInput,
          onValueChange = { urlInput = it },
          label = { Text("RTMP URL") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = keyInput,
          onValueChange = { keyInput = it },
          label = { Text("Reusable stream key") },
          singleLine = true,
          visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton(
            text = if (keyVisible) "Hide key" else "Show key",
            onClick = { keyVisible = !keyVisible },
            modifier = Modifier.weight(1f)
          )
          PillButton(
            text = "How to get it",
            onClick = { showGuide = true },
            modifier = Modifier.weight(1f)
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton(
            text = "Save",
            onClick = {
              if (!urlInput.trim().startsWith("rtmp")) {
                onMessage("RTMP URL must start with rtmp:// or rtmps://")
              } else if (keyInput.isBlank()) {
                onMessage("Stream key cannot be empty")
              } else {
                onSaveUrl(urlInput.trim())
                onSaveKey(keyInput.trim())
              }
            },
            active = true,
            modifier = Modifier.weight(1f)
          )
          PillButton(
            text = "Delete key",
            onClick = {
              keyInput = ""
              onClearKey()
            },
            accent = Palette.LiveRed,
            modifier = Modifier.weight(1f)
          )
        }
        Text(
          text = "The key is stored encrypted with the Android Keystore, is masked here and is never written to logs.",
          color = Palette.TextSecondary,
          fontSize = 11.sp
        )
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(text = "Video quality")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Quality.entries.forEach { quality ->
            PillButton(
              text = quality.label,
              onClick = { onQuality(quality) },
              active = quality == settings.quality,
              modifier = Modifier.weight(1f)
            )
          }
        }
        Text(
          text = "Higher quality needs a stronger upload connection. 720p is the safe default for mobile data.",
          color = Palette.TextSecondary,
          fontSize = 11.sp
        )
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(text = "Orientation")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          PillButton(
            text = "Portrait",
            onClick = { onOrientation(true) },
            active = settings.portrait,
            modifier = Modifier.weight(1f)
          )
          PillButton(
            text = "Landscape",
            onClick = { onOrientation(false) },
            active = !settings.portrait,
            modifier = Modifier.weight(1f)
          )
        }
        Text(
          text = "Changing quality or orientation restarts the camera preview. Stop the stream before changing it.",
          color = Palette.TextSecondary,
          fontSize = 11.sp
        )
      }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = "About")
        Text(
          text = "LiveVoice 1.0 - RTMP streaming with a real time voice changer. Camera and mic stay inside this app, so the changed voice is exactly what your viewers hear.",
          color = Palette.TextSecondary,
          fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = "No Google login, no YouTube API, no data collection. Only your stream key is stored, encrypted.",
          color = Palette.TextSecondary,
          fontSize = 12.sp
        )
      }
    }
  }

  if (showGuide) {
    AlertDialog(
      onDismissRequest = { showGuide = false },
      confirmButton = {
        TextButton(onClick = {
          showGuide = false
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/"))
          runCatching { context.startActivity(intent) }
            .onFailure { onMessage("Open studio.youtube.com in a browser") }
        }) { Text("Open YouTube Studio") }
      },
      dismissButton = {
        TextButton(onClick = { showGuide = false }) { Text("Close") }
      },
      title = { Text("Get your reusable stream key") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("1. Open YouTube Studio on a computer and choose Create > Go live.")
          Text("2. Pick the Stream tab and copy the Stream key. Do not regenerate it, so the same key keeps working.")
          Text("3. Paste it here once. LiveVoice loads it automatically every time after that.")
          Text(
            "Stream URL stays rtmp://a.rtmp.youtube.com/live2 unless YouTube shows a different ingest server.",
            fontSize = 12.sp
          )
        }
      }
    )
  }
}
