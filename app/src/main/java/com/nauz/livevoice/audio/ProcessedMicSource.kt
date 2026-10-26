package com.nauz.livevoice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import kotlin.math.max

/**
 * Custom RootEncoder audio source: opens the mic itself, runs every block through [VoiceProcessor]
 * and only then hands the PCM to the AAC encoder. Because the effect happens before encoding, the
 * changed voice is what actually reaches YouTube - and nothing is ever played on the phone speaker,
 * so there is no echo.
 */
@SuppressLint("MissingPermission")
class ProcessedMicSource(private val processor: VoiceProcessor) : AudioSource() {

  private var record: AudioRecord? = null
  private var aec: AcousticEchoCanceler? = null
  private var ns: NoiseSuppressor? = null
  private var thread: Thread? = null

  @Volatile
  private var running = false


  /** RootEncoder only asks for validity here; the real AudioRecord is opened in [start]. */
  override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean) = true

  override fun isRunning(): Boolean = running

  override fun start(getMicrophoneData: GetMicrophoneData) {
    this.getMicrophoneData = getMicrophoneData
    if (running) return

    val channelMask =
      if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
    val minBuffer =
      AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuffer <= 0) throw IllegalArgumentException("Unsupported audio config: $sampleRate Hz")

    val rec = AudioRecord(
      // MIC, not VOICE_COMMUNICATION: the call audio path is narrow band and applies aggressive
      // AGC/noise suppression, which is exactly what makes a voice sound muffled and buzzy.
      MediaRecorder.AudioSource.MIC,
      sampleRate,
      channelMask,
      AudioFormat.ENCODING_PCM_16BIT,
      max(minBuffer * 2, 8192)
    )
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
      rec.release()
      throw IllegalArgumentException("Microphone is busy or blocked by another app")
    }
    record = rec

    // echoCanceler / noiseSuppressor come from AudioSource.init(), set by prepareAudio()
    if (echoCanceler) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
    if (noiseSuppressor) ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }

    processor.configure(sampleRate, if (isStereo) 2 else 1)
    running = true
    rec.startRecording()
    thread = Thread({ readLoop(rec) }, "voice-mic").apply {
      priority = Thread.MAX_PRIORITY
      start()
    }
  }

  private fun readLoop(rec: AudioRecord) {
    val pcm = ByteArray(4096)
    while (running) {
      val size = rec.read(pcm, 0, pcm.size)
      if (size <= 0) {
        if (size < 0) break else continue
      }
      val timeStamp = System.nanoTime() / 1000
      // VoiceProcessor applies the effect and handles mute itself.
      processor.process(pcm, size)
      getMicrophoneData?.inputPCMData(Frame(pcm, 0, size, timeStamp))
    }
  }

  override fun stop() {
    running = false
    thread?.join(600)
    thread = null
    aec?.release()
    aec = null
    ns?.release()
    ns = null
    record?.let { rec ->
      runCatching { rec.stop() }
      rec.release()
    }
    record = null
    getMicrophoneData = null
  }

  override fun release() = stop()

  fun setMuted(value: Boolean) {
    processor.muted = value
  }
}
