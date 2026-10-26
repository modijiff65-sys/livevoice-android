# LiveVoice

Android live streaming app in Kotlin + Jetpack Compose. Camera video plus microphone audio are
streamed to YouTube Live over RTMP, and the mic audio passes through a real time voice changer
**before** it is encoded. Both live in one app, so the mic is never shared with another app and the
changed voice is exactly what viewers hear.

No Google login, no YouTube API, no Google Cloud project. You paste your reusable RTMP stream key
once and it is stored encrypted on the device.

## Install

Download the APK from the latest **Actions -> Build APK** run (artifact `livevoice-debug-apk`), or
build it yourself:

```bash
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

The APK is signed with the debug key, so allow "install from unknown sources" on the phone.

## First run

1. Onboarding -> allow camera and microphone (and notifications, so the live notification shows).
2. Settings -> paste your stream key from YouTube Studio (Create -> Go live -> Stream tab). The URL
   is pre-filled with `rtmp://a.rtmp.youtube.com/live2`.
3. Back on Stream: pick a voice effect, tap **GO LIVE**.
4. Use **Chat** to open YouTube and read the live chat.

## Features

- Two capture sources: **Camera** (front/back, flash, mute) and **Screen** (whole screen, or a
  single app on Android 14+ through the system picker). Both keep the voice effect.
- RTMP streaming with front/back camera switch, flash, mute, 480p / 720p / 1080p, portrait or
  landscape, 30 fps, configurable bitrate per quality.
- Voice presets: Normal, Deep Man, Child, Female, Robot, Alien, Echo, Monster, Radio, Custom
  (pitch -12..+12 semitones and formant 0.7x..1.4x).
- Clean audio chain: microphone source (not the call audio path), 85 Hz high pass, WSOLA pitch
  shifter, formant biquad, noise gate, soft limiter. No hard clipping, so no buzz.
- Effects can be switched while live, without dropping the stream.
- Optional 5 second test recording played back on the speaker, so you can hear the effect before
  going live.
- Foreground service (camera + microphone types) with a stop action in the notification, automatic
  reconnect with retries, audio level meter, bitrate readout, live timer.

## Architecture

```
app/src/main/java/com/nauz/livevoice/
  LiveVoiceApp.kt          Application + AppContainer (manual DI, owns the stream)
  MainActivity.kt          Compose host, permissions, bottom navigation
  data/SettingsRepository  DataStore for settings, EncryptedSharedPreferences for the stream key
  audio/VoiceEngine.kt     VoicePreset, VoiceProcessor (pitch shifter, biquad, ring mod, echo)
  audio/ProcessedMicSource Custom RootEncoder AudioSource: AudioRecord -> DSP -> AAC encoder
  audio/VoiceTester.kt     5 second test record + WAV write
  stream/StreamController  RtmpStream (camera) + RtmpDisplay (screen) wrapper, state, reconnect
  stream/LiveService.kt    Foreground service
  ui/                      Stream, Effects, Settings, Onboarding screens + theme + components
```

The audio chain is `AudioRecord(MIC)` -> `AcousticEchoCanceler` + `NoiseSuppressor` ->
`VoiceProcessor` -> RootEncoder AAC encoder. Nothing is played back on the speaker, so there is no
feedback loop. In screen mode `RtmpDisplay` builds its own microphone, so the same
`VoiceProcessor` is plugged in through RootEncoder's `CustomAudioEffect` hook instead.

Screen streaming uses MediaProjection. Android 14+ shows the system picker where the user can share
the entire screen or one app; older versions share the whole screen. The projection runs inside the
mediaProjection type foreground service, which is required on Android 14.

## Build a release APK

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias livevoice
```

Then add to `app/build.gradle` (or pass with `-P`):

```gradle
android {
    signingConfigs {
        release {
            storeFile file(System.getenv("KEYSTORE") ?: "release.jks")
            storePassword System.getenv("KEYSTORE_PASSWORD")
            keyAlias "livevoice"
            keyPassword System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release { signingConfig signingConfigs.release }
    }
}
```

```bash
gradle assembleRelease
```

R8 shrinking is already on for release builds, with `app/proguard-rules.pro` keeping RootEncoder.
Never commit the keystore or its passwords.

## Notes and limits

- Dependency injection is a small hand written `AppContainer` instead of Hilt: fewer moving parts,
  faster builds, and the stream must outlive any ViewModel anyway.
- Fonts use the platform sans-serif family rather than bundling Poppins/Inter, to keep the APK small.
- Presets are time domain (granular pitch shift, biquads, ring modulation, delay), not a phase
  vocoder, which keeps latency low enough for live chat.
- YouTube can take a few seconds to show the stream on the watch page after the RTMP connection is
  accepted.
