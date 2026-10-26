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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.nauz.livevoice.ui.theme.Palette

private data class Page(val title: String, val accent: String, val body: String, val icon: String)
private val pages = listOf(
  Page("Your voice.\nYour style.", "LIVE WITH POWER", "Camera, screen and real-time voice effects in one clean streaming studio.", "◉"),
  Page("One time\nsetup.", "SECURE & SIMPLE", "Save your reusable YouTube stream key once. Your setup is ready whenever you are.", "▣"),
  Page("Go live in\ntwo taps.", "READY TO BROADCAST", "Pick your source, choose a voice and start your broadcast with one polished control surface.", "↗")
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
  var index by remember { mutableStateOf(0) }
  val page = pages[index]
  Box(Modifier.fillMaxSize().background(Palette.Background)) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1D1751), Palette.Background, Palette.Background))))
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Bottom) {
      Box(Modifier.fillMaxWidth().height(390.dp).clip(RoundedCornerShape(30.dp)).background(Palette.Hero), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(28.dp)) {
          Box(Modifier.size(90.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) { Text(page.icon, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
          Text("LiveVoice", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Text(page.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 34.sp)
          Text(page.accent, color = Palette.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
          Text(page.body, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
      }
      Spacer(Modifier.height(24.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        pages.indices.forEach { i -> Box(Modifier.size(if (i == index) 24.dp else 7.dp, 7.dp).clip(CircleShape).background(if (i == index) Palette.Purple else Palette.SurfaceHigh)) }
        Spacer(Modifier.weight(1f))
        Text("${index + 1} / ${pages.size}", color = Palette.TextMuted, fontSize = 10.sp)
      }
      Spacer(Modifier.height(18.dp))
      GradientButton(if (index == pages.lastIndex) "GET STARTED  →" else "NEXT  →", { if (index == pages.lastIndex) onFinish() else index++ }, Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      Text("No Google login • No YouTube API • Your stream key stays encrypted", color = Palette.TextMuted, fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
      Spacer(Modifier.height(8.dp))
    }
  }
}
