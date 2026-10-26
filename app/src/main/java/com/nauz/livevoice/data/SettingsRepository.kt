package com.nauz.livevoice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("live_voice_settings")

enum class Quality(val label: String, val width: Int, val height: Int, val bitrate: Int) {
  SD480("480p", 854, 480, 1_500_000),
  HD720("720p", 1280, 720, 3_000_000),
  FHD1080("1080p", 1920, 1080, 5_000_000)
}

data class StreamSettings(
  val rtmpUrl: String = DEFAULT_RTMP_URL,
  val quality: Quality = Quality.HD720,
  val portrait: Boolean = true,
  val effectId: String = "normal",
  val customPitch: Float = 0f,
  val customFormant: Float = 1f,
  val onboarded: Boolean = false
) {
  companion object {
    const val DEFAULT_RTMP_URL = "rtmp://a.rtmp.youtube.com/live2"
  }
}

/**
 * Non-secret settings live in DataStore. The stream key is the one real secret in this app, so it
 * goes into EncryptedSharedPreferences (Android Keystore backed) and is never written to logs.
 * If the keystore is unavailable on a device we keep the key in memory for the session instead of
 * silently storing it in the clear.
 */
class SettingsRepository(context: Context) {

  private val appContext = context.applicationContext

  private val securePrefs: SharedPreferences? = try {
    val masterKey = MasterKey.Builder(appContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    EncryptedSharedPreferences.create(
      appContext,
      "live_voice_secure",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  } catch (t: Throwable) {
    null
  }

  private var inMemoryKey: String = ""

  fun streamKey(): String = securePrefs?.getString(KEY_STREAM_KEY, null) ?: inMemoryKey

  fun saveStreamKey(key: String) {
    inMemoryKey = key
    securePrefs?.edit()?.putString(KEY_STREAM_KEY, key)?.apply()
  }

  fun clearStreamKey() {
    inMemoryKey = ""
    securePrefs?.edit()?.remove(KEY_STREAM_KEY)?.apply()
  }

  val settings: Flow<StreamSettings> = appContext.settingsDataStore.data.map { prefs ->
    StreamSettings(
      rtmpUrl = prefs[KEY_RTMP_URL] ?: StreamSettings.DEFAULT_RTMP_URL,
      quality = Quality.entries.firstOrNull { it.name == prefs[KEY_QUALITY] } ?: Quality.HD720,
      portrait = prefs[KEY_PORTRAIT] ?: true,
      effectId = prefs[KEY_EFFECT] ?: "normal",
      customPitch = prefs[KEY_CUSTOM_PITCH] ?: 0f,
      customFormant = prefs[KEY_CUSTOM_FORMANT] ?: 1f,
      onboarded = prefs[KEY_ONBOARDED] ?: false
    )
  }

  suspend fun setRtmpUrl(url: String) = appContext.settingsDataStore.edit { it[KEY_RTMP_URL] = url.trim() }

  suspend fun setQuality(quality: Quality) =
    appContext.settingsDataStore.edit { it[KEY_QUALITY] = quality.name }

  suspend fun setPortrait(portrait: Boolean) =
    appContext.settingsDataStore.edit { it[KEY_PORTRAIT] = portrait }

  suspend fun setEffect(id: String) = appContext.settingsDataStore.edit { it[KEY_EFFECT] = id }

  suspend fun setCustom(pitch: Float, formant: Float) = appContext.settingsDataStore.edit {
    it[KEY_CUSTOM_PITCH] = pitch
    it[KEY_CUSTOM_FORMANT] = formant
  }

  suspend fun setOnboarded(done: Boolean) = appContext.settingsDataStore.edit { it[KEY_ONBOARDED] = done }

  private companion object {
    const val KEY_STREAM_KEY = "stream_key"
    val KEY_RTMP_URL = stringPreferencesKey("rtmp_url")
    val KEY_QUALITY = stringPreferencesKey("quality")
    val KEY_PORTRAIT = booleanPreferencesKey("portrait")
    val KEY_EFFECT = stringPreferencesKey("effect")
    val KEY_CUSTOM_PITCH = floatPreferencesKey("custom_pitch")
    val KEY_CUSTOM_FORMANT = floatPreferencesKey("custom_formant")
    val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
  }
}
