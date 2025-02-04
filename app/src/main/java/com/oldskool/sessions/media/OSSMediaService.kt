package com.oldskool.sessions.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.oldskool.sessions.MainActivity
import com.oldskool.sessions.R

class OSSMediaService : MediaBrowserServiceCompat() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): OSSMediaService = this@OSSMediaService
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "com.oldskool.sessions.media.PLAYBACK"
        private const val MEDIA_ROOT_ID = "media_root_id"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()

        // Initialize the Media Session with minimal controls
        mediaSession = MediaSessionCompat(baseContext, "OSSMediaService").apply {
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)

            // Set initial playback state with minimal controls
            setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .build())
        }

        // Initialize the playback state with minimal controls
        stateBuilder = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)

        // Initialize notification manager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    interface Callback {
        fun onPlayPauseClicked()
        fun onSeekTo(position: Long)
        fun getSourceFragmentId(): Int
    }

    private var callback: Callback? = null

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            try {
                callback?.onPlayPauseClicked()
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onPlay", e)
            }
        }

        override fun onPause() {
            try {
                callback?.onPlayPauseClicked()
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onPause", e)
            }
        }

        override fun onStop() {
            try {
                callback?.onPlayPauseClicked()
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onStop", e)
            }
        }

        override fun onSeekTo(pos: Long) {
            try {
                if (pos >= 0) {
                    callback?.onSeekTo(pos)
                }
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onSeekTo", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (SERVICE_INTERFACE == intent.action) {
            super.onBind(intent)
        } else {
            binder
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(emptyList())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(metadata: MediaMetadataCompat, @Suppress("UNUSED_PARAMETER") state: PlaybackStateCompat): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)

        // Create content intent
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or 
            PendingIntent.FLAG_IMMUTABLE
        )

        // Configure the notification
        builder.setContentTitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            .setContentText(metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
            .setLargeIcon(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)

        return builder.build()
    }

    fun updateMetadata(title: String, artist: String?, artwork: Bitmap?) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)

        artwork?.let {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }

        mediaSession.setMetadata(builder.build())
    }

    fun updatePlaybackState(state: Int, position: Long) {
        val playbackState = stateBuilder
            .setState(state, position, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        // Update notification
        val notification = createNotification(mediaSession.controller.metadata, playbackState)
        
        // Show as foreground service when playing
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
