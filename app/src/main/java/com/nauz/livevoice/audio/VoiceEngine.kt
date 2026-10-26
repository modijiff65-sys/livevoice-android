package com.nauz.livevoice.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
      VoicePreset("deep_man", "Deep Man", pitch = -4f, formant = 0.9f),
      VoicePreset("child", "Child", pitch = 5f, formant = 1.25f),
      VoicePreset("female", "Female", pitch = 3f, formant = 1.12f),
      VoicePreset("robot", "Robot", ringHz = 70f, ringMix = 0.85f, bandpass = true),
      VoicePreset("alien", "Alien", pitch = 4f, formant = 1.3f, ringHz = 24f, ringMix = 0.7f, echoMs = 180, echoMix = 0.22f),
      VoicePreset("echo", "Echo", echoMs = 300, echoMix = 0.4f),
      VoicePreset("monster", "Monster", pitch = -7f, formant = 0.82f, echoMs = 120, echoMix = 0.18f, drive = 2.2f),
      VoicePreset("radio", "Radio", drive = 1.8f, bandpass = true),
      VoicePreset("custom", "Custom")
    )

    fun byId(id: String): VoicePreset = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun custom(pitch: Float, formant: Float) =
      VoicePreset("custom", "Custom", pitch = pitch, formant = formant)
  }
}

/**
 * Real-time voice changer. Works on interleaved 16-bit PCM in place, block by block, so it can sit
 * between the microphone and the AAC encoder.
 *
 * Chain: high pass -> (radio band) -> formant -> ring mod -> drive -> echo -> noise gate -> limiter.
 * Pitch shifting (WSOLA) runs on the block before the per-sample stage. Everything is allocation
 * free after the first block because this runs on the audio thread while video is being encoded.
 */
class VoiceProcessor {

  @Volatile
  var preset: VoicePreset = VoicePreset.byId("normal")

  @Volatile
  var muted: Boolean = false

  /** Called with the RMS level (0..1) of every third block, for the audio meter. */
  var onLevel: ((Float) -> Unit)? = null

  private var sampleRate = 44100
  private var channels = 1
  private var shifter = WsolaShifter()
  private var echo = EchoLine(44100, 700)
  private var formantFilter = Biquad.peaking(44100.0, 1200.0, 1.2, 0.0)
  private var bandFilter = Biquad.bandPass(44100.0, 1500.0, 0.8)
  private var highPass = Biquad.highPass(44100.0, 85.0, 0.707)
  private var ringPhase = 0.0
  private var mono = FloatArray(0)
  private var processed = FloatArray(0)
  private var gateEnv = 0f
  private var gateGain = 1f
  private var levelCounter = 0
  private var lastRatio = 1.0

  fun configure(sampleRate: Int, channels: Int) {
    if (this.sampleRate == sampleRate && this.channels == channels) return
    this.sampleRate = sampleRate
    this.channels = channels
    shifter = WsolaShifter()
    echo = EchoLine(sampleRate, 700)
    highPass = Biquad.highPass(sampleRate.toDouble(), 85.0, 0.707)
    formantFilter = Biquad.peaking(sampleRate.toDouble(), 1200.0, 1.2, 0.0)
    bandFilter = Biquad.bandPass(sampleRate.toDouble(), 1500.0, 0.8)
    lastRatio = 1.0
  }

  /** Processes [size] bytes of interleaved 16-bit PCM inside [buffer], in place. */
  fun process(buffer: ByteArray, size: Int) {
    val frames = (size / 2) / channels
    if (frames <= 0) return
    if (muted) {
      java.util.Arrays.fill(buffer, 0, size, 0)
      onLevel?.invoke(0f)
      return
    }
    val p = preset
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

    val ratio = 2.0.pow(p.pitch / 12.0)
    if (abs(ratio - 1.0) > 0.003) {
      if (abs(ratio - lastRatio) > 0.001) shifter.reset()
      lastRatio = ratio
      shifter.process(mono, processed, ratio)
    } else {
      lastRatio = 1.0
      System.arraycopy(mono, 0, processed, 0, frames)
    }

    formantFilter.setPeaking(1200.0, 1.2, ((p.formant - 1f) * 9f).toDouble())
    val ringStep = 2.0 * PI * p.ringHz / sampleRate
    val echoDelay = sampleRate * p.echoMs / 1000
    var sumSquares = 0.0

    for (i in 0 until frames) {
      var v = processed[i]

      // Removes rumble, handling noise and DC offset: the main "mud" in phone mics.
      v = highPass.process(v)
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

      // Noise gate / downward expander: quiet room noise is pushed down instead of being amplified.
      val level = if (v < 0f) -v else v
      gateEnv = if (level > gateEnv) gateEnv + (level - gateEnv) * 0.35f else gateEnv * 0.995f
      val target = if (gateEnv < GATE_THRESHOLD) GATE_FLOOR else 1f
      gateGain += (target - gateGain) * (if (target > gateGain) 0.25f else 0.02f)
      v *= gateGain

      // Soft limiter instead of hard clipping: hard clipping is what makes a voice sound buzzy.
      if (v > 0.9f) {
        v = 0.9f + 0.1f * tanh(((v - 0.9f) / 0.1f).toDouble()).toFloat()
      } else if (v < -0.9f) {
        v = -0.9f - 0.1f * tanh(((-v - 0.9f) / 0.1f).toDouble()).toFloat()
      }

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

  private companion object {
    const val GATE_THRESHOLD = 0.006f
    const val GATE_FLOOR = 0.1f
  }
}

/**
 * WSOLA pitch shifter: overlap-add grains with a similarity search (cross correlation) so the
 * grains line up with the waveform instead of cutting it at random, then a resample stage to bring
 * the length back. That is what keeps the result clean instead of the warble/buzz a fixed two tap
 * crossfade produces.
 */
private class WsolaShifter {

  private val frame = 1024
  private val hop = frame / 2
  private val tolerance = 160
  private val searchStep = 4
  private val window = FloatArray(frame) {
    (0.5 - 0.5 * cos(2.0 * PI * it / (frame - 1))).toFloat()
  }

  private var input = FloatArray(1 shl 15)
  private var inputLen = 0
  private var analysisPos = 0.0
  private var naturalPos = 0

  private var stretched = FloatArray(1 shl 15)
  private var written = 0
  private var readPos = 0.0

  fun reset() {
    inputLen = 0
    analysisPos = 0.0
    naturalPos = 0
    written = 0
    readPos = 0.0
  }

  fun process(source: FloatArray, out: FloatArray, ratio: Double) {
    val alpha = ratio.coerceIn(0.5, 2.0)
    appendInput(source)

    val needed = readPos + out.size * alpha + frame
    while (written < needed) produceFrame(alpha)

    for (i in out.indices) {
      val p = readPos
      val i0 = p.toInt()
      val frac = (p - i0).toFloat()
      val s0 = stretched[i0]
      val s1 = stretched[i0 + 1]
      out[i] = s0 + (s1 - s0) * frac
      readPos += alpha
    }
    trim()
  }

  private fun produceFrame(alpha: Double) {
    val base = analysisPos.toInt().coerceAtLeast(0)
    ensureInput(base + frame + tolerance + 1)
    val from = (base + bestOffset(base)).coerceIn(0, (inputLen - frame).coerceAtLeast(0))
    val end = written + frame
    if (end > stretched.size) {
      var size = stretched.size
      while (size < end) size *= 2
      stretched = stretched.copyOf(size)
    }
    for (k in 0 until frame) stretched[written + k] += window[k] * input[from + k]
    written += hop
    naturalPos = from + hop
    analysisPos += hop / alpha
  }

  /** Offset (within +/- tolerance) whose waveform best continues the previous grain. */
  private fun bestOffset(base: Int): Int {
    if (inputLen < frame + tolerance) return 0
    val ref = naturalPos.coerceIn(0, inputLen - tolerance - 1)
    var best = 0
    var bestScore = -1e18
    var d = -tolerance
    while (d <= tolerance) {
      val start = base + d
      if (start >= 0 && start + tolerance < inputLen) {
        var dot = 0.0
        var energy = 1e-9
        var k = 0
        while (k < tolerance) {
          val a = input[start + k]
          val b = input[ref + k]
          dot += (a * b).toDouble()
          energy += (a * a).toDouble()
          k += 2
        }
        val score = dot / sqrt(energy)
        if (score > bestScore) {
          bestScore = score
          best = d
        }
      }
      d += searchStep
    }
    return best
  }

  private fun appendInput(source: FloatArray) {
    val needed = inputLen + source.size
    if (needed > input.size) {
      var size = input.size
      while (size < needed) size *= 2
      input = input.copyOf(size)
    }
    System.arraycopy(source, 0, input, inputLen, source.size)
    inputLen = needed
  }

  private fun ensureInput(min: Int) {
    if (min > input.size) {
      var size = input.size
      while (size < min) size *= 2
      input = input.copyOf(size)
    }
    if (min > inputLen) {
      java.util.Arrays.fill(input, inputLen, min, 0f)
      inputLen = min
    }
  }

  private fun trim() {
    val cut = readPos.toInt()
    if (cut > 1 shl 15 && cut < written) {
      System.arraycopy(stretched, cut, stretched, 0, written - cut)
      written -= cut
      readPos -= cut
      val inCut = analysisPos.toInt() - 4096
      if (inCut > 1 shl 13 && inCut < inputLen) {
        System.arraycopy(input, inCut, input, 0, inputLen - inCut)
        inputLen -= inCut
        analysisPos -= inCut
        naturalPos -= inCut
      }
    }
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
  private var sr = 44100.0

  fun process(x: Float): Float {
    val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
    x2 = x1
    x1 = x
    y2 = y1
    y1 = y
    return y
  }

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
    fun peaking(sampleRate: Double, freq: Double, q: Double, gainDb: Double): Biquad =
      Biquad().apply {
        sr = sampleRate
        setPeaking(freq, q, gainDb)
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

    fun highPass(sampleRate: Double, freq: Double, q: Double): Biquad {
      val w0 = 2.0 * PI * freq / sampleRate
      val alpha = sin(w0) / (2.0 * q)
      val cw = cos(w0)
      val a0 = 1.0 + alpha
      return Biquad().apply {
        b0 = ((1.0 + cw) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + cw) / a0).toFloat()
        b2 = ((1.0 + cw) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cw / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
      }
    }
  }
}
