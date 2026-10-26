package com.nauz.livevoice.stream

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import android.view.SurfaceView
import com.nauz.livevoice.audio.ProcessedMicSource
import com.nauz.livevoice.audio.VoicePreset
import com.nauz.livevoice.audio.VoiceProcessor
import com.nauz.livevoice.data.Quality
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.streamclient.StreamBaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the RTMP session for the whole app lifetime. Two capture modes share one voice chain:
 *
 *  - CAMERA: RtmpStream with a custom AudioSource (ProcessedMicSource), the effect runs before the
 *    AAC encoder.
 *  - SCREEN: RtmpDisplay (MediaProjection) which builds its own microphone, so the effect is
 *    plugged in through RootEncoder's CustomAudioEffect hook instead.
 *
 * In both cases the mic audio goes through the same VoiceProcessor, so presets behave identically.
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

  /** Screen mode path: RtmpDisplay applies this effect to the PCM before encoding. */
  private val audioEffect = object : CustomAudioEffect() {
    override fun process(pcmBuffer: ByteArray): ByteArray {
      processor.process(pcmBuffer, pcmBuffer.size)
      return pcmBuffer
    }
  }

  private var display: RtmpDisplay? = null
  private var screenMode = false
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

  /** True while the screen capture pipeline owns the session. */
  fun isScreenMode(): Boolean = screenMode

  // ---------------------------------------------------------------- camera mode

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
    if (rtmp.isStreaming || display?.isStreaming == true) return
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
    if (rtmp.isStreaming || display?.isStreaming == true) return
    val endPoint = buildEndpoint(url, key)
    if (endPoint == null) {
      _state.value = State(Status.ERROR, "Add a valid rtmp:// URL and stream key first")
      return
    }
    if (!prepared) {
      _state.value = State(Status.ERROR, "Camera is not ready yet, try again in a second")
      return
    }
    screenMode = false
    _state.value = State(Status.CONNECTING)
    rtmp.getStreamClient().setReTries(MAX_RETRIES)
    rtmp.startStream(endPoint)
  }

  // ---------------------------------------------------------------- screen mode

  /**
   * Streams the device screen (whole screen, or a single app on Android 14+ where the system
   * picker offers it). The voice effect still applies, so screen streams keep the changed voice.
   */
  fun startScreenStream(resultCode: Int, data: Intent, quality: Quality, url: String, key: String) {
    if (rtmp.isStreaming || display?.isStreaming == true) return
    val endPoint = buildEndpoint(url, key)
    if (endPoint == null) {
      _state.value = State(Status.ERROR, "Add a valid rtmp:// URL and stream key first")
      return
    }
    if (rtmp.isOnPreview) runCatching { rtmp.stopPreview() }

    val capture = display ?: RtmpDisplay(context, true, this).also { created ->
      created.setMediaProjectionCallback(object : MediaProjection.Callback() {
        override fun onStop() {
          scope.launch {
            if (screenMode) {
              runCatching { created.stopStream() }
              screenMode = false
              _state.value = State(Status.IDLE, "Screen sharing stopped")
            }
          }
        }
      })
      created.setCustomAudioEffect(audioEffect)
      display = created
    }

    processor.configure(SAMPLE_RATE, 1)
    val portrait =
      context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val width = if (portrait) quality.height else quality.width
    val height = if (portrait) quality.width else quality.height
    val dpi = context.resources.displayMetrics.densityDpi

    val ok = try {
      capture.prepareVideo(width, height, FPS, quality.bitrate, 0, dpi) &&
        capture.prepareAudio(
          MediaRecorder.AudioSource.MIC, AUDIO_BITRATE, SAMPLE_RATE, false, true, true
        )
    } catch (e: Exception) {
      Log.e(TAG, "screen prepare failed", e)
      false
    }
    if (!ok) {
      _state.value = State(Status.ERROR, "Screen capture is not supported with these settings")
      return
    }

    // MediaProjection only produces a frame when the screen changes, so force a constant frame rate
    // or the stream would look frozen to viewers.
    runCatching { capture.getGlInterface().setForceRender(true, FPS) }
    capture.setIntentResult(resultCode, data)
    screenMode = true
    _state.value = State(Status.CONNECTING)
    capture.getStreamClient().setReTries(MAX_RETRIES)
    capture.startStream(endPoint)
  }

  // ---------------------------------------------------------------- shared controls

  fun stop() {
    if (screenMode) {
      display?.let { if (it.isStreaming) runCatching { it.stopStream() } }
    } else if (rtmp.isStreaming) {
      runCatching { rtmp.stopStream() }
    }
    screenMode = false
    _state.value = State()
    _level.value = 0f
  }

  fun toggleMute(): Boolean {
    isMuted = !isMuted
    processor.muted = isMuted
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
      runCatching { camera.enableLantern(); true }.getOrDefault(false)
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
    display?.let { runCatching { it.stopStream() } }
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

  private fun activeClient(): StreamBaseClient? =
    if (screenMode) display?.getStreamClient() else rtmp.getStreamClient()

  // ---------------------------------------------------------------- ConnectChecker

  override fun onConnectionStarted(url: String) {
    scope.launch { _state.value = State(Status.CONNECTING) }
  }

  override fun onConnectionSuccess() {
    scope.launch { _state.value = State(Status.LIVE, null, 0, System.currentTimeMillis()) }
  }

  override fun onConnectionFailed(reason: String) {
    scope.launch {
      val willRetry = runCatching { activeClient()?.reTry(RETRY_DELAY_MS, reason) ?: false }
        .getOrDefault(false)
      if (willRetry) {
        _state.value = State(Status.RETRYING, reason)
      } else {
        stop()
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
      stop()
      _state.value = State(Status.ERROR, "YouTube rejected the stream key")
    }
  }

  override fun onAuthSuccess() = Unit

  override fun onNewBitrate(bitrate: Long) {
    scope.launch { _state.value = _state.value.copy(bitrateKbps = (bitrate / 1000).toInt()) }
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
