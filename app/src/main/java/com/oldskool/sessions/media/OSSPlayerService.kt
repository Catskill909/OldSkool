package com.oldskool.sessions.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.oldskool.sessions.R
import com.oldskool.sessions.models.AudioItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Future

/**
 * Main service for audio playback using Media3.
 * This is the ONE AUDIO TRUTH for the application.
 */
@OptIn(UnstableApi::class)
class OSSPlayerService : MediaLibraryService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var notificationManager: NotificationManager

    // StateFlows for observing player state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _currentItem = MutableStateFlow<AudioItem?>(null)
    val currentItem: StateFlow<AudioItem?> = _currentItem

    companion object {
        private const val TAG = "OSSPlayerService"
        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "com.oldskool.sessions.media"
        private const val CUSTOM_COMMAND_TOGGLE = "com.oldskool.sessions.TOGGLE_PLAYBACK"
        
        // Action constants for intent communication
        const val ACTION_PLAY = "com.oldskool.sessions.media.PLAY"
        const val ACTION_PAUSE = "com.oldskool.sessions.media.PAUSE"
        const val ACTION_TOGGLE_PLAYBACK = "com.oldskool.sessions.media.TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.oldskool.sessions.media.STOP"
        const val EXTRA_AUDIO_ITEM = "com.oldskool.sessions.media.EXTRA_AUDIO_ITEM"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Create notification channel
        createNotificationChannel()

        // Initialize ExoPlayer
        initializePlayer()

        // Create MediaSession
        initializeMediaSession()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY -> {
                intent.getParcelableExtra<AudioItem>(EXTRA_AUDIO_ITEM)?.let { audioItem ->
                    playAudio(audioItem)
                }
            }
            ACTION_TOGGLE_PLAYBACK -> {
                togglePlayPause()
            }
            ACTION_STOP -> {
                stop()
                stopSelf()  // Stop the service completely
                // Also make sure to remove the notification
                stopForeground(true)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Media Playback"
            val channelDescription = "Media playback controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                channelName,
                importance
            ).apply {
                description = channelDescription
                setShowBadge(false)
            }
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializePlayer() {
        // Build audio attributes for better audio focus handling
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Listen to player state changes
        player.addListener(PlayerEventListener())
    }

    @OptIn(UnstableApi::class)
    private fun initializeMediaSession() {
        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            SessionCallback()
        )
            .setId("OSSPlayerService")
            .build()
    }

    fun playAudio(item: AudioItem) {
        serviceScope.launch {
            try {
                _currentItem.value = item

                // Create MediaItem with metadata
                val mediaItem = createMediaItemFromAudioItem(item)
                
                // Set the media item to the player
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
            }
        }
    }

    private fun createMediaItemFromAudioItem(audioItem: AudioItem): MediaItem {
        // Extract metadata from our model object
        val metadata = MediaMetadata.Builder()
            .setTitle(audioItem.title)
            .setArtist(audioItem.artist ?: "")
            .setAlbumTitle(audioItem.album ?: "")
            .setArtworkUri(Uri.parse(audioItem.albumArtUrl ?: "")) // Use albumArtUrl for artwork
            // Store source fragment ID for navigation
            .setExtras(Bundle().apply { putInt("sourceFragmentId", audioItem.sourceFragmentId) })
            .build()
        
        // Create and return a new media item with the metadata
        return MediaItem.Builder()
            .setMediaId(audioItem.id ?: System.currentTimeMillis().toString())
            .setMediaMetadata(metadata)
            .setUri(Uri.parse(audioItem.audioUrl)) // Parse string URL to Uri
            .build()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun stop() {
        player.stop()
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    private suspend fun loadArtwork(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(applicationContext)
                    .asBitmap()
                    .load(url)
                    .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.ALL))
                    .submit()
                    .get()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artwork", e)
                null
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession) {
        try {
            val notification = buildNotification(session)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(mediaSession: MediaSession): Notification {
        // Build a simple notification using MediaStyle
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_play_arrow_24) // Using an existing play icon
            .setContentTitle(_currentItem.value?.title ?: "Playing")
            .setContentText(_currentItem.value?.artist ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(_isPlaying.value)
        
        // Create a media style notification (without setting the token)
        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0, 1) // Show play/pause button in compact view
        
        // Add play/pause action
        val playPauseIcon = if (_isPlaying.value) 
            R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        val playPauseAction = NotificationCompat.Action(
            playPauseIcon,
            if (_isPlaying.value) "Pause" else "Play",
            PendingIntent.getService(
                this, 
                0, 
                Intent(this, OSSPlayerService::class.java).setAction(ACTION_TOGGLE_PLAYBACK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        
        // Add stop action
        val stopAction = NotificationCompat.Action(
            R.drawable.baseline_stop_24,
            "Stop",
            PendingIntent.getService(
                this, 
                0, 
                Intent(this, OSSPlayerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        
        builder.addAction(playPauseAction)
        builder.addAction(stopAction)
        builder.setStyle(mediaStyle)
        
        return builder.build()
    }

    @OptIn(UnstableApi::class)
    inner class PlayerEventListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            Log.d(TAG, "isPlaying=$isPlaying")
            
            // Update position tracking when playback state changes
            if (isPlaying) {
                startPositionTracking()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _currentPosition.value = player.currentPosition
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    _duration.value = player.duration
                    _currentPosition.value = player.currentPosition
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _currentPosition.value = _duration.value
                }
            }
        }
    }

    private fun startPositionTracking() {
        serviceScope.launch {
            while (_isPlaying.value) {
                _currentPosition.value = player.currentPosition
                kotlinx.coroutines.delay(500) // Update every half second
            }
        }
    }

    @OptIn(UnstableApi::class)
    inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_TOGGLE) {
                togglePlayPause()
                val future = SettableFuture.create<SessionResult>()
                future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                return future
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_TOGGLE, Bundle()))
                .build()
            return MediaSession.ConnectionResult.accept(
                availableCommands,
                connectionResult.availablePlayerCommands
            )
        }
    }

    override fun onDestroy() {
        // Make sure to remove notifications
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(true)
        
        // Release media session
        mediaSession.release()
        
        // Release player
        player.stop()
        player.clearMediaItems()
        player.release()
        
        // Cancel all coroutines
        serviceJob.cancel()
        
        Log.d(TAG, "Service destroyed and all resources released")
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaSession
    }
}
