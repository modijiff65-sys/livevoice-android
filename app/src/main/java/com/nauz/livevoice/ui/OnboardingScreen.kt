package com.nauz.livevoice.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauz.livevoice.ui.theme.Palette

private data class OnboardingPage(val title: String, val body: String)

private val pages = listOf(
  OnboardingPage(
    "Your voice, your rules",
    "LiveVoice streams your camera to YouTube Live and changes your voice before it is encoded. The effect reaches your viewers directly, and nothing is played back on the phone speaker, so there is no echo."
  ),
  OnboardingPage(
    "One time setup",
    "Paste your reusable RTMP stream key from YouTube Studio once. It is stored encrypted on this device and loaded automatically every time. You can edit or delete it later in Settings."
  ),
  OnboardingPage(
    "Go live in two taps",
    "Allow camera and microphone access, pick a voice effect, then tap Go Live. While live you can still switch effects, mute, flip the camera or stop from the notification."
  )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
  var index by remember { mutableStateOf(0) }
  val page = pages[index]

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Palette.Background)
      .padding(24.dp),
    verticalArrangement = Arrangement.Center
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      pages.indices.forEach { i ->
        Box(
          modifier = Modifier
            .height(6.dp)
            .size(width = if (i == index) 28.dp else 10.dp, height = 6.dp)
            .clip(CircleShape)
            .background(if (i == index) Palette.Purple else Palette.SurfaceHigh)
        )
      }
    }

    Spacer(Modifier.height(28.dp))
    Text(
      text = page.title,
      color = Palette.TextPrimary,
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text = page.body,
      color = Palette.TextSecondary,
      fontSize = 15.sp,
      lineHeight = 22.sp,
      textAlign = TextAlign.Start
    )
    Spacer(Modifier.height(36.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      if (index > 0) {
        PillButton(
          text = "Back",
          onClick = { index -= 1 },
          modifier = Modifier.weight(1f)
        )
      }
      GradientButton(
        text = if (index == pages.lastIndex) "GET STARTED" else "NEXT",
        onClick = {
          if (index == pages.lastIndex) onFinish() else index += 1
        },
        modifier = Modifier.weight(2f)
      )
    }

    Spacer(Modifier.height(16.dp))
    Text(
      text = "No Google login. No YouTube API. No account needed.",
      color = Palette.TextSecondary.copy(alpha = 0.7f),
      fontSize = 11.sp,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center
    )
  }
}
