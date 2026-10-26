package com.nauz.livevoice

import android.app.Application
import com.nauz.livevoice.data.SettingsRepository
import com.nauz.livevoice.stream.StreamController

class LiveVoiceApp : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}

/**
 * Hand-rolled dependency container. The stream has to outlive the activity (it keeps running while
 * the phone is in the user's pocket), so it is owned here, not by a ViewModel.
 */
class AppContainer(application: Application) {
  val settings = SettingsRepository(application)
  val stream = StreamController(application)
}
