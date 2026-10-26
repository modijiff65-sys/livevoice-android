package com.nauz.livevoice.ui

import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nauz.livevoice.AppContainer
import com.nauz.livevoice.audio.VoicePreset
import com.nauz.livevoice.data.Quality
import com.nauz.livevoice.data.SettingsRepository
import com.nauz.livevoice.data.StreamSettings
import com.nauz.livevoice.stream.StreamController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
  private val repo: SettingsRepository,
  private val stream: StreamController
) : ViewModel() {

  val settings: StateFlow<StreamSettings> = repo.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, StreamSettings())

  val streamState: StateFlow<StreamController.State> = stream.state
  val level: StateFlow<Float> = stream.level

  private val _streamKey = MutableStateFlow(repo.streamKey())
  val streamKey: StateFlow<String> = _streamKey.asStateFlow()

  private val _preset = MutableStateFlow(VoicePreset.byId("normal"))
  val preset: StateFlow<VoicePreset> = _preset.asStateFlow()

  private val _muted = MutableStateFlow(false)
  val muted: StateFlow<Boolean> = _muted.asStateFlow()

  private val _flashOn = MutableStateFlow(false)
  val flashOn: StateFlow<Boolean> = _flashOn.asStateFlow()

  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  init {
    viewModelScope.launch {
      val saved = repo.settings.first()
      applyPreset(resolvePreset(saved))
    }
  }

  fun attachPreview(view: SurfaceView, quality: Quality, portrait: Boolean) {
    stream.attachPreview(view, quality, portrait)
  }

  fun updateConfig(quality: Quality, portrait: Boolean) {
    stream.updateConfig(quality, portrait)
  }

  fun goLive() {
    val current = settings.value
    val key = _streamKey.value
    if (key.isBlank()) {
      _message.value = "Add your YouTube stream key in Settings first"
      return
    }
    stream.goLive(current.rtmpUrl, key)
  }

  fun stopLive() {
    stream.stop()
    _muted.value = false
    _flashOn.value = false
  }

  fun toggleMute() {
    _muted.value = stream.toggleMute()
  }

  fun switchCamera() {
    stream.switchCamera()
  }

  fun toggleFlash() {
    _flashOn.value = stream.toggleFlash()
  }

  fun selectPreset(preset: VoicePreset) {
    applyPreset(preset)
    viewModelScope.launch {
      repo.setEffect(preset.id)
      if (preset.id == "custom") repo.setCustom(preset.pitch, preset.formant)
    }
  }

  fun setCustomPitch(pitch: Float, formant: Float) {
    val preset = VoicePreset.custom(pitch, formant)
    applyPreset(preset)
    viewModelScope.launch {
      repo.setEffect("custom")
      repo.setCustom(pitch, formant)
    }
  }

  fun saveStreamKey(key: String) {
    repo.saveStreamKey(key.trim())
    _streamKey.value = key.trim()
    _message.value = "Stream key saved"
  }

  fun clearStreamKey() {
    repo.clearStreamKey()
    _streamKey.value = ""
    _message.value = "Stream key deleted"
  }

  fun saveRtmpUrl(url: String) {
    viewModelScope.launch { repo.setRtmpUrl(url) }
  }

  fun setQuality(quality: Quality) {
    viewModelScope.launch { repo.setQuality(quality) }
  }

  fun setPortrait(portrait: Boolean) {
    viewModelScope.launch { repo.setPortrait(portrait) }
  }

  fun finishOnboarding() {
    viewModelScope.launch { repo.setOnboarded(true) }
  }

  fun clearMessage() {
    _message.value = null
  }

  fun showMessage(text: String) {
    _message.value = text
  }

  private fun applyPreset(preset: VoicePreset) {
    _preset.value = preset
    stream.setPreset(preset)
  }

  private fun resolvePreset(settings: StreamSettings): VoicePreset =
    if (settings.effectId == "custom") {
      VoicePreset.custom(settings.customPitch, settings.customFormant)
    } else {
      VoicePreset.byId(settings.effectId)
    }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          MainViewModel(container.settings, container.stream) as T
      }
  }
}
