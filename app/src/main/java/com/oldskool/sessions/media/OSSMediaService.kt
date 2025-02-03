package com.oldskool.sessions.media

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service

import android.graphics.Bitmap
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.oldskool.sessions.R
import kotlinx.coroutines.SupervisorJob

@UnstableApi
class OSSMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: PlayerNotificationManager
    
    private val serviceJob = SupervisorJob()

    companion object {
        private const val CHANNEL_ID = "com.oldskool.sessions.PLAYBACK"
        private const val NOTIFICATION_ID = 1
        private const val MEDIA_SESSION_TAG = "OSSMediaService"
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }

        // Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setId(MEDIA_SESSION_TAG)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        // Initialize notification manager
        notificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(DescriptionAdapter())
            .setNotificationListener(NotificationListener())
            .setChannelNameResourceId(R.string.app_name)
            .setChannelDescriptionResourceId(R.string.app_name)
            .build()
            .apply {
                setPlayer(player)
                setMediaSessionToken(mediaSession!!.sessionCompatToken)
            }
    }

    @SuppressLint("NewApi")




    private inner class NotificationListener : 
        PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing) {
                startForeground(notificationId, notification)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stopForeground(Service.STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            }
        }

        override fun onNotificationCancelled(
            notificationId: Int,
            dismissedByUser: Boolean
        ) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }



    private inner class DescriptionAdapter : 
        PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.mediaMetadata.title?.toString() ?: "Unknown Title"
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            return PendingIntent.getActivity(
                this@OSSMediaService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        @Suppress("RedundantNullableReturnType")
        override fun getCurrentContentText(player: Player): CharSequence? {
            return "Old Skool Sessions"
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            return null
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        player.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
}
