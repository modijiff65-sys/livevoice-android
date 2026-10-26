package com.nauz.livevoice.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nauz.livevoice.LiveVoiceApp
import com.nauz.livevoice.MainActivity
import com.nauz.livevoice.R

/**
 * Foreground service for a live session. The type mask is chosen per session: a camera stream
 * declares camera + microphone, a screen stream declares microphone + mediaProjection (Android 14
 * rejects a mediaProjection service if the user has not granted capture yet, so the two are kept
 * apart on purpose). The notification offers a one tap stop.
 */
class LiveService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      (application as LiveVoiceApp).container.stream.stop()
      stopSelf()
      return START_NOT_STICKY
    }
    val screen = intent?.getBooleanExtra(EXTRA_SCREEN, false) ?: false
    createChannel()
    val notification = buildNotification(screen)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val type = if (screen) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      }
      startForeground(NOTIFICATION_ID, notification, type)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  private fun buildNotification(screen: Boolean): Notification {
    val openApp = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val stop = PendingIntent.getService(
      this,
      1,
      Intent(this, LiveService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val text = if (screen) {
      "Sharing your screen to YouTube with your voice effect"
    } else {
      "Streaming to YouTube with your voice effect"
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("LiveVoice is live")
      .setContentText(text)
      .setSmallIcon(R.drawable.ic_notification)
      .setOngoing(true)
      .setContentIntent(openApp)
      .addAction(0, "Stop stream", stop)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.channel_name),
      NotificationManager.IMPORTANCE_LOW
    )
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val CHANNEL_ID = "live_voice_stream"
    private const val NOTIFICATION_ID = 42
    private const val ACTION_STOP = "com.nauz.livevoice.STOP_STREAM"
    private const val EXTRA_SCREEN = "screen_mode"

    fun start(context: Context, screen: Boolean) {
      val intent = Intent(context, LiveService::class.java).putExtra(EXTRA_SCREEN, screen)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, LiveService::class.java))
    }
  }
}
