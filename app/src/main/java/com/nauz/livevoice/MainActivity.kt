package com.nauz.livevoice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nauz.livevoice.stream.LiveService
import com.nauz.livevoice.stream.StreamController
import com.nauz.livevoice.ui.EffectsScreen
import com.nauz.livevoice.ui.GlassCard
import com.nauz.livevoice.ui.MainViewModel
import com.nauz.livevoice.ui.OnboardingScreen
import com.nauz.livevoice.ui.SettingsScreen
import com.nauz.livevoice.ui.StreamScreen
import com.nauz.livevoice.ui.theme.LiveVoiceTheme
import com.nauz.livevoice.ui.theme.Palette
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val container = (application as LiveVoiceApp).container
    setContent {
      LiveVoiceTheme {
        AppRoot(container)
      }
    }
  }
}

@Composable
private fun AppRoot(container: AppContainer) {
  val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val state by viewModel.streamState.collectAsStateWithLifecycle()
  val level by viewModel.level.collectAsStateWithLifecycle()
  val preset by viewModel.preset.collectAsStateWithLifecycle()
  val key by viewModel.streamKey.collectAsStateWithLifecycle()
  val muted by viewModel.muted.collectAsStateWithLifecycle()
  val flashOn by viewModel.flashOn.collectAsStateWithLifecycle()
  val message by viewModel.message.collectAsStateWithLifecycle()

  val context = LocalContext.current
  var permissionsGranted by remember { mutableStateOf(hasStreamPermissions(context)) }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) {
    permissionsGranted = hasStreamPermissions(context)
  }

  val projectionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK && data != null) {
      // The mediaProjection foreground service must be running before the projection is created.
      LiveService.start(context, true)
      viewModel.goLiveScreen(result.resultCode, data)
    } else {
      viewModel.showMessage("Screen sharing permission was cancelled")
    }
  }

  LaunchedEffect(Unit) {
    if (!permissionsGranted) permissionLauncher.launch(requiredPermissions())
  }

  // The foreground service only lives as long as a live session does.
  LaunchedEffect(state.status) {
    when (state.status) {
      StreamController.Status.IDLE, StreamController.Status.ERROR -> LiveService.stop(context)
      else -> Unit
    }
  }

  LaunchedEffect(message) {
    if (message != null) {
      delay(3000)
      viewModel.clearMessage()
    }
  }

  if (!settings.onboarded) {
    OnboardingScreen(onFinish = { viewModel.finishOnboarding() })
    return
  }

  var tab by remember { mutableStateOf(0) }
  val isLive = state.status == StreamController.Status.LIVE ||
    state.status == StreamController.Status.RETRYING ||
    state.status == StreamController.Status.CONNECTING

  Box(modifier = Modifier.fillMaxSize().background(Palette.Background)) {
    when (tab) {
      0 -> StreamScreen(
        settings = settings,
        state = state,
        level = level,
        preset = preset,
        permissionsGranted = permissionsGranted,
        screenMode = settings.screenMode,
        onToggleScreenMode = { enabled ->
          if (isLive) {
            viewModel.showMessage("Stop the stream before switching source")
          } else {
            viewModel.setScreenMode(enabled)
          }
        },
        muted = muted,
        flashOn = flashOn,
        onPreview = { view -> viewModel.attachPreview(view, settings.quality, settings.portrait) },
        onGoLive = {
          if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions())
            viewModel.showMessage("Camera and microphone permission is required")
          } else if (key.isBlank()) {
            tab = 2
            viewModel.showMessage("Add your YouTube stream key first")
          } else if (settings.screenMode) {
            val intent = screenCaptureIntent(context)
            if (intent == null) {
              viewModel.showMessage("Screen capture is not available on this device")
            } else {
              projectionLauncher.launch(intent)
            }
          } else {
            LiveService.start(context, false)
            viewModel.goLive()
          }
        },
        onStop = {
          viewModel.stopLive()
          LiveService.stop(context)
        },
        onToggleMute = { viewModel.toggleMute() },
        onSwitchCamera = { viewModel.switchCamera() },
        onToggleFlash = { viewModel.toggleFlash() },
        onSelectPreset = { viewModel.selectPreset(it) },
        onOpenChat = {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/live_dashboard"))
          runCatching { context.startActivity(intent) }
            .onFailure { viewModel.showMessage("Open YouTube to see your chat") }
        },
        onOpenSettings = { tab = 2 }
      )

      1 -> EffectsScreen(
        preset = preset,
        onSelect = { viewModel.selectPreset(it) },
        onCustomChange = { pitch, formant -> viewModel.setCustomPitch(pitch, formant) },
        onMessage = { viewModel.showMessage(it) }
      )

      else -> SettingsScreen(
        settings = settings,
        streamKey = key,
        onSaveUrl = { viewModel.saveRtmpUrl(it) },
        onSaveKey = { viewModel.saveStreamKey(it) },
        onClearKey = { viewModel.clearStreamKey() },
        onQuality = { viewModel.setQuality(it) },
        onOrientation = { viewModel.setPortrait(it) },
        onMessage = { viewModel.showMessage(it) }
      )
    }

    if (!isLive) {
      Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp)
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            BottomTab("⌂", "Home", tab == 0) { tab = 0 }
            BottomTab("◉", "Studio", tab == 1) { tab = 1 }
            BottomTab("⚙", "Settings", tab == 2) { tab = 2 }
          }
        }
      }
    }

    message?.let { text ->
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = if (isLive) 24.dp else 96.dp)
          .padding(horizontal = 24.dp)
      ) {
        GlassCard {
          Text(text = text, color = Palette.TextPrimary)
        }
      }
    }
  }
}

@Composable
private fun BottomTab(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp)).background(if (selected) Palette.AccentSoft else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(icon, color = if (selected) Color.White else Palette.TextMuted, fontSize = 15.sp)
      Text(label, color = if (selected) Color.White else Palette.TextSecondary, fontSize = 10.sp, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium)
    }
  }
}

private fun screenCaptureIntent(context: Context): Intent? = runCatching {
  val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    // Android 14+ lets the user pick the whole screen or a single app.
    manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
  } else {
    manager.createScreenCaptureIntent()
  }
}.getOrNull()

private fun requiredPermissions(): Array<String> {
  val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    permissions += Manifest.permission.POST_NOTIFICATIONS
  }
  return permissions.toTypedArray()
}

private fun hasStreamPermissions(context: android.content.Context): Boolean {
  val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
  val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
  return camera == PackageManager.PERMISSION_GRANTED && mic == PackageManager.PERMISSION_GRANTED
}
