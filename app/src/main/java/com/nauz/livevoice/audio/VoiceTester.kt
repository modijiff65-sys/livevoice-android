package com.nauz.livevoice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Records a few seconds through the same [VoiceProcessor] the stream uses, writes a WAV into the
 * cache dir and hands the file back so the UI can play it. Lets the user hear the effect before
 * going live, without touching the real stream.
 */
@SuppressLint("MissingPermission")
class VoiceTester {

  private val processor = VoiceProcessor()

  @Volatile
  var isRecording = false
    private set

  @Volatile
  private var cancelled = false

  private var thread: Thread? = null

  fun start(context: Context, preset: VoicePreset, seconds: Int = 5, onDone: (Result<File>) -> Unit) {
    if (isRecording) return
    isRecording = true
    cancelled = false
    processor.preset = preset

    thread = Thread {
      val result = runCatching { record(context.applicationContext, seconds) }
      isRecording = false
      onDone(result)
    }.apply {
      priority = Thread.NORM_PRIORITY
      start()
    }
  }

  fun cancel() {
    cancelled = true
  }

  private fun record(context: Context, seconds: Int): File {
    val sampleRate = 44100
    val minBuffer = AudioRecord.getMinBufferSize(
      sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuffer <= 0) throw IllegalStateException("Microphone does not support 44.1 kHz mono")
    val record = AudioRecord(
      MediaRecorder.AudioSource.VOICE_COMMUNICATION,
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
      max(minBuffer * 2, 8192)
    )
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      throw IllegalStateException("Microphone is busy")
    }
    processor.configure(sampleRate, 1)
    val collected = ByteArrayOutputStream()
    val pcm = ByteArray(2048)
    val target = sampleRate * 2 * seconds
    try {
      record.startRecording()
      var read = 0
      while (read < target && !cancelled) {
        val size = record.read(pcm, 0, pcm.size)
        if (size <= 0) continue
        processor.process(pcm, size)
        collected.write(pcm, 0, size)
        read += size
      }
    } finally {
      runCatching { record.stop() }
      record.release()
    }
    val file = File(context.cacheDir, "voice_test.wav")
    writeWav(file, collected.toByteArray(), sampleRate)
    return file
  }

  private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    file.outputStream().use { out ->
      out.write("RIFF".toByteArray())
      out.write(intLe(36 + pcm.size))
      out.write("WAVEfmt ".toByteArray())
      out.write(intLe(16))
      out.write(shortLe(1))
      out.write(shortLe(channels))
      out.write(intLe(sampleRate))
      out.write(intLe(byteRate))
      out.write(shortLe(channels * bitsPerSample / 8))
      out.write(shortLe(bitsPerSample))
      out.write("data".toByteArray())
      out.write(intLe(pcm.size))
      out.write(pcm)
    }
  }

  private fun intLe(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte()
  )

  private fun shortLe(value: Int): ByteArray =
    byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
}
