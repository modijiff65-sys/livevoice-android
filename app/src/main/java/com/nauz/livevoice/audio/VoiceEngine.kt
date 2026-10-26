package com.nauz.livevoice.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A voice preset: the whole DSP chain described by a handful of numbers, so a preset can be
 * switched mid-stream without touching the audio pipeline.
 */
data class VoicePreset(
  val id: String,
  val label: String,
  val pitch: Float = 0f,        // semitones, -12..+12
  val formant: Float = 1f,      // 0.7 (big head) .. 1.4 (small head)
  val ringHz: Float = 0f,       // ring modulation frequency, 0 = off
  val ringMix: Float = 0f,
  val echoMs: Int = 0,          // 0 = off
  val echoMix: Float = 0f,
  val drive: Float = 1f,        // 1 = clean
  val bandpass: Boolean = false
) {
  companion object {
    val ALL: List<VoicePreset> = listOf(
      VoicePreset("normal", "Normal"),
      VoicePreset("deep_man", "Deep Man", pitch = -4f, formant = 0.88f, drive = 1.1f),
      VoicePreset("child", "Child", pitch = 5f, formant = 1.28f),
      VoicePreset("female", "Female", pitch = 3f, formant = 1.15f),
      VoicePreset("robot", "Robot", ringHz = 80f, ringMix = 0.9f, bandpass = true),
      VoicePreset("alien", "Alien", pitch = 4f, formant = 1.35f, ringHz = 24f, ringMix = 0.8f, echoMs = 180, echoMix = 0.25f),
      VoicePreset("echo", "Echo", echoMs = 300, echoMix = 0.45f),
      VoicePreset("monster", "Monster", pitch = -7f, formant = 0.8f, echoMs = 120, echoMix = 0.2f, drive = 3f),
      VoicePreset("radio", "Radio", drive = 2.5f, bandpass = true),
      VoicePreset("custom", "Custom")
    )

    fun byId(id: String): VoicePreset = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun custom(pitch: Float, formant: Float) =
      VoicePreset("custom", "Custom", pitch = pitch, formant = formant)
  }
}

/**
 * Real-time voice changer. Works on interleaved 16-bit PCM in place, block by block, so it can sit
 * between AudioRecord and the RTMP audio encoder.
 *
 * Everything here is allocation free after the first block, which matters because this runs on the
 * audio thread while the phone is also encoding and streaming video.
 */
class VoiceProcessor {

  @Volatile
  var preset: VoicePreset = VoicePreset.byId("normal")

  /** Called with the RMS level (0..1) of every third block, for the audio meter. */
  var onLevel: ((Float) -> Unit)? = null

  private var sampleRate = 44100
  private var channels = 1
  private var shifter = PitchShifter()
  private var echo = EchoLine(44100, 700)
  private var formantFilter = Biquad.peaking(44100.0, 1200.0, 1.2, 0.0)
  private val bandFilter = Biquad.bandPass(44100.0, 1400.0, 0.9)
  private var ringPhase = 0.0
  private var mono = FloatArray(0)
  private var processed = FloatArray(0)
  private var levelCounter = 0

  fun configure(sampleRate: Int, channels: Int) {
    if (this.sampleRate == sampleRate && this.channels == channels) return
    this.sampleRate = sampleRate
    this.channels = channels
    shifter = PitchShifter()
    echo = EchoLine(sampleRate, 700)
    formantFilter = Biquad.peaking(sampleRate.toDouble(), 1200.0, 1.2, 0.0)
  }

  /** Processes [size] bytes of interleaved 16-bit PCM inside [buffer], in place. */
  fun process(buffer: ByteArray, size: Int) {
    val p = preset
    val frames = (size / 2) / channels
    if (frames <= 0) return
    if (mono.size != frames) {
      mono = FloatArray(frames)
      processed = FloatArray(frames)
    }

    var sample = 0
    for (f in 0 until frames) {
      var sum = 0f
      for (c in 0 until channels) {
        sum += readShort(buffer, sample * 2)
        sample++
      }
      mono[f] = sum / channels
    }

    if (abs(p.pitch) > 0.05f) {
      shifter.process(mono, processed, 2.0.pow(p.pitch / 12.0))
    } else {
      System.arraycopy(mono, 0, processed, 0, frames)
    }

    formantFilter.setPeaking(1200.0, 1.2, ((p.formant - 1f) * 9f).toDouble())
    val ringStep = 2.0 * PI * p.ringHz / sampleRate
    val echoDelay = sampleRate * p.echoMs / 1000
    var sumSquares = 0.0

    for (i in 0 until frames) {
      var v = processed[i]
      if (p.bandpass) v = bandFilter.process(v)
      v = formantFilter.process(v)
      if (p.ringHz > 0f && p.ringMix > 0f) {
        ringPhase += ringStep
        if (ringPhase > 2 * PI) ringPhase -= 2 * PI
        val modulated = v * sin(ringPhase).toFloat()
        v = v * (1f - p.ringMix) + modulated * p.ringMix
      }
      if (p.drive > 1.01f) {
        val d = p.drive
        v = tanh((v * d).toDouble()).toFloat() / tanh(d.toDouble()).toFloat()
      }
      if (p.echoMs > 0 && p.echoMix > 0f) v = echo.process(v, echoDelay, p.echoMix)
      if (v > 1f) v = 1f else if (v < -1f) v = -1f
      processed[i] = v
      sumSquares += v.toDouble() * v.toDouble()
    }

    sample = 0
    for (f in 0 until frames) {
      val v = processed[f]
      for (c in 0 until channels) {
        writeShort(buffer, sample * 2, v)
        sample++
      }
    }

    levelCounter++
    if (levelCounter >= 3) {
      levelCounter = 0
      onLevel?.invoke(sqrt(sumSquares / frames).toFloat())
    }
  }

  private fun readShort(buffer: ByteArray, index: Int): Float {
    val lo = buffer[index].toInt() and 0xFF
    val hi = buffer[index + 1].toInt()
    return ((hi shl 8) or lo) / 32768f
  }

  private fun writeShort(buffer: ByteArray, index: Int, value: Float) {
    val s = (value * 32767f).toInt().coerceIn(-32768, 32767)
    buffer[index] = (s and 0xFF).toByte()
    buffer[index + 1] = ((s shr 8) and 0xFF).toByte()
  }
}

/**
 * Two-tap granular pitch shifter. The read head moves at [ratio] speed while a second tap sits half
 * a grain ahead; the two are crossfaded with a raised cosine so the grain jump stays inaudible.
 * Quality is "voice chat" grade on purpose: no latency worth speaking of, no big FFT buffers.
 */
private class PitchShifter {
  private val size = 16384
  private val grain = 1024
  private val half = grain / 2.0
  private val ring = FloatArray(size)
  private var write = 0
  private var readPos = 0.0

  fun process(input: FloatArray, output: FloatArray, ratio: Double) {
    for (i in input.indices) {
      ring[write] = input[i]
      write = (write + 1) % size

      val v1 = tap(readPos)
      val v2 = tap(readPos + half)
      val phase = ((readPos % grain) + grain) % grain / grain.toDouble()
      val mix = (0.5 - 0.5 * cos(2.0 * PI * phase)).toFloat()
      output[i] = v1 * (1f - mix) + v2 * mix

      readPos += ratio
      var distance = write - readPos
      while (distance < grain) {
        readPos -= grain
        distance += grain
      }
      while (distance > size - 2 * grain) {
        readPos += grain
        distance -= grain
      }
    }
  }

  private fun tap(pos: Double): Float {
    val base = floor(pos).toInt()
    val frac = (pos - base).toFloat()
    val i0 = ((base % size) + size) % size
    val i1 = (i0 + 1) % size
    return ring[i0] + (ring[i1] - ring[i0]) * frac
  }
}

/** Simple feedback delay used for the echo presets. */
private class EchoLine(sampleRate: Int, maxDelayMs: Int) {
  private val size = sampleRate * maxDelayMs / 1000 + 2
  private val buffer = FloatArray(size)
  private var pos = 0

  fun process(input: Float, delaySamples: Int, mix: Float): Float {
    if (delaySamples <= 0 || delaySamples >= size) return input
    val readIndex = (pos - delaySamples + size) % size
    val delayed = buffer[readIndex]
    buffer[pos] = input + delayed * mix.coerceIn(0f, 0.7f)
    pos = (pos + 1) % size
    return input * (1f - mix * 0.4f) + delayed * mix
  }
}

/** RBJ cookbook biquad. Coefficients are recomputed when the formant slider moves. */
class Biquad private constructor() {
  private var b0 = 1f
  private var b1 = 0f
  private var b2 = 0f
  private var a1 = 0f
  private var a2 = 0f
  private var x1 = 0f
  private var x2 = 0f
  private var y1 = 0f
  private var y2 = 0f

  fun process(x: Float): Float {
    val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
    x2 = x1
    x1 = x
    y2 = y1
    y1 = y
    return y
  }

  private var sr = 44100.0

  fun setPeaking(freq: Double, q: Double, gainDb: Double) {
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * freq / sr
    val alpha = sin(w0) / (2.0 * q)
    val cw = cos(w0)
    val a0 = 1.0 + alpha / a
    b0 = ((1.0 + alpha * a) / a0).toFloat()
    b1 = (-2.0 * cw / a0).toFloat()
    b2 = ((1.0 - alpha * a) / a0).toFloat()
    a1 = (-2.0 * cw / a0).toFloat()
    a2 = ((1.0 - alpha / a) / a0).toFloat()
  }

  companion object {
    fun peaking(sampleRate: Double, freq: Double, q: Double, gainDb: Double): Biquad {
      return Biquad().apply {
        sr = sampleRate
        setPeaking(freq, q, gainDb)
      }
    }

    fun bandPass(sampleRate: Double, freq: Double, q: Double): Biquad {
      val w0 = 2.0 * PI * freq / sampleRate
      val alpha = sin(w0) / (2.0 * q)
      val cw = cos(w0)
      val a0 = 1.0 + alpha
      return Biquad().apply {
        b0 = (alpha / a0).toFloat()
        b1 = 0f
        b2 = (-alpha / a0).toFloat()
        a1 = (-2.0 * cw / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
      }
    }
  }
}
