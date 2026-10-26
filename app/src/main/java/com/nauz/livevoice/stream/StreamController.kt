package com.nauz.livevoice.stream

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.nauz.livevoice.audio.ProcessedMicSource
import com.nauz.livevoice.audio.VoicePreset
import com.nauz.livevoice.audio.VoiceProcessor
import com.nauz.livevoice.data.Quality
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.rtmp.RtmpStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the RTMP session for the whole app lifetime. The camera and mic sources are created once and
 * reused, so switching a voice effect mid-stream is just a field write inside VoiceProcessor - the
 * encoder and the RTMP connection never notice.
 */
class StreamController(private val context: Context) : ConnectChecker {

  enum class Status { IDLE, CONNECTING, LIVE, RETRYING, ERROR }

  data class State(
    val status: Status = Status.IDLE,
    val message: String? = null,
    val bitrateKbps: Int = 0,
    val startedAt: Long = 0L
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  private val _level = MutableStateFlow(0f)
  val level: StateFlow<Float> = _level.asStateFlow()

  private val processor = VoiceProcessor().apply {
    onLevel = { value -> _level.value = value }
  }
  private val mic = ProcessedMicSource(processor)
  private val camera = Camera2Source(context)
  private val rtmp = RtmpStream(context, this, camera, mic)

  private var prepared = false
  private var preparedQuality: Quality? = null
  private var preparedPortrait = true
  private var previewView: SurfaceView? = null

  var isMuted = false
    private set
  var isFlashOn = false
    private set
  var preset: VoicePreset = VoicePreset.byId("normal")
    private set

  /** Attaches (or re-attaches) the camera preview and prepares the encoders if needed. */
  fun attachPreview(view: SurfaceView, quality: Quality, portrait: Boolean) {
    previewView = view
    if (!prepare(quality, portrait)) {
      _state.value = State(Status.ERROR, "This camera does not support the selected quality")
      return
    }
    runCatching { rtmp.startPreview(view, true) }
      .onFailure { Log.w(TAG, "Preview failed", it) }
  }

  /** Called when quality or orientation changes. Not allowed while live. */
  fun updateConfig(quality: Quality, portrait: Boolean) {
    if (rtmp.isStreaming) return
    if (!prepare(quality, portrait)) {
      _state.value = State(Status.ERROR, "This camera does not support the selected quality")
      return
    }
    previewView?.let { view ->
      runCatching { rtmp.startPreview(view, true) }
        .onFailure { Log.w(TAG, "Preview failed", it) }
    }
  }

  private fun prepare(quality: Quality, portrait: Boolean): Boolean {
    if (prepared && preparedQuality == quality && preparedPortrait == portrait) return true
    if (rtmp.isStreaming) return false
    if (rtmp.isOnPreview) runCatching { rtmp.stopPreview() }
    if (prepared) runCatching { rtmp.release() }
    prepared = false

    val rotation = if (portrait) 90 else 0
    val ok = try {
      rtmp.prepareVideo(quality.width, quality.height, quality.bitrate, FPS, 2, rotation) &&
        rtmp.prepareAudio(SAMPLE_RATE, false, AUDIO_BITRATE, true, true)
    } catch (e: Exception) {
      Log.e(TAG, "prepare failed", e)
      false
    }
    if (ok) {
      prepared = true
      preparedQuality = quality
      preparedPortrait = portrait
    }
    return ok
  }

  fun goLive(url: String, key: String) {
    if (rtmp.isStreaming) return
    val endPoint = buildEndpoint(url, key)
    if (endPoint == null) {
      _state.value = State(Status.ERROR, "Add a valid rtmp:// URL and stream key first")
      return
    }
    if (!prepared) {
      _state.value = State(Status.ERROR, "Camera is not ready yet, try again in a second")
      return
    }
    _state.value = State(Status.CONNECTING)
    rtmp.getStreamClient().setReTries(MAX_RETRIES)
    rtmp.startStream(endPoint)
  }

  fun stop() {
    if (rtmp.isStreaming) runCatching { rtmp.stopStream() }
    _state.value = State()
    _level.value = 0f
  }

  fun toggleMute(): Boolean {
    isMuted = !isMuted
    mic.setMuted(isMuted)
    return isMuted
  }

  fun switchCamera() {
    runCatching { camera.switchCamera() }
      .onFailure { Log.w(TAG, "Camera switch failed", it) }
  }

  fun toggleFlash(): Boolean {
    isFlashOn = if (isFlashOn) {
      runCatching { camera.disableLantern() }
      false
    } else {
      val enabled = runCatching { camera.enableLantern(); true }.getOrDefault(false)
      enabled
    }
    return isFlashOn
  }

  fun setPreset(newPreset: VoicePreset) {
    preset = newPreset
    processor.preset = newPreset
  }

  fun release() {
    stop()
    runCatching { rtmp.release() }
    prepared = false
    previewView = null
  }

  fun buildEndpoint(url: String, key: String): String? {
    val cleanUrl = url.trim()
    val cleanKey = key.trim()
    if (cleanKey.isEmpty()) return null
    if (!cleanUrl.startsWith("rtmp://") && !cleanUrl.startsWith("rtmps://")) return null
    return if (cleanUrl.endsWith("/")) cleanUrl + cleanKey else "$cleanUrl/$cleanKey"
  }

  override fun onConnectionStarted(url: String) {
    scope.launch { _state.value = State(Status.CONNECTING) }
  }

  override fun onConnectionSuccess() {
    scope.launch {
      _state.value = State(Status.LIVE, null, 0, System.currentTimeMillis())
    }
  }

  override fun onConnectionFailed(reason: String) {
    scope.launch {
      val willRetry = runCatching {
        rtmp.getStreamClient().reTry(RETRY_DELAY_MS, reason)
      }.getOrDefault(false)
      if (willRetry) {
        _state.value = State(Status.RETRYING, reason)
      } else {
        runCatching { rtmp.stopStream() }
        _state.value = State(Status.ERROR, reason)
      }
    }
  }

  override fun onDisconnect() {
    scope.launch {
      if (_state.value.status != Status.ERROR) _state.value = State()
    }
  }

  override fun onAuthError() {
    scope.launch {
      runCatching { rtmp.stopStream() }
      _state.value = State(Status.ERROR, "YouTube rejected the stream key")
    }
  }

  override fun onAuthSuccess() = Unit

  override fun onNewBitrate(bitrate: Long) {
    scope.launch {
      _state.value = _state.value.copy(bitrateKbps = (bitrate / 1000).toInt())
    }
  }

  private companion object {
    const val TAG = "StreamController"
    const val FPS = 30
    const val SAMPLE_RATE = 44100
    const val AUDIO_BITRATE = 128 * 1024
    const val MAX_RETRIES = 6
    const val RETRY_DELAY_MS = 4000L
  }
}
