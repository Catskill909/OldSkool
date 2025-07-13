package com.oldskool.sessions.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.palette.graphics.Palette
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    // Artwork caching for notifications
    private var cachedArtwork: Bitmap? = null
    private var currentArtworkUrl: String? = null

    companion object {
        private const val TAG = "OSSPlayerService"
        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "com.oldskool.sessions.media"
        private const val CUSTOM_COMMAND_TOGGLE = "com.oldskool.sessions.TOGGLE_PLAYBACK"
        
        // Action constants for intent communication
        const val ACTION_PLAY = "com.oldskool.sessions.media.PLAY"
        const val ACTION_LOAD = "com.oldskool.sessions.media.LOAD"
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
            ACTION_LOAD -> {
                intent.getParcelableExtra<AudioItem>(EXTRA_AUDIO_ITEM)?.let { audioItem ->
                    // Load audio without starting playback
                    loadAudioOnly(audioItem)
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
            _currentItem.value = item
            
            try {
                // Create MediaItem from AudioItem
                val mediaItem = createMediaItemFromAudioItem(item)
                
                // Set media item to player and start playback
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true  // Set to auto-play when ready
                
                // Load and cache artwork for notification
                loadAndCacheArtwork(item.albumArtUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
            }
        }
    }

    /**
     * Load audio without starting playback
     */
    fun loadAudioOnly(item: AudioItem) {
        serviceScope.launch {
            _currentItem.value = item
            
            try {
                // Create MediaItem from AudioItem
                val mediaItem = createMediaItemFromAudioItem(item)
                
                // Set media item to player and prepare without playing
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = false  // Explicitly set not to play when ready
                
                // Load and cache artwork for notification
                loadAndCacheArtwork(item.albumArtUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio", e)
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

    /**
     * Optimize bitmap for notification display - critical for consistent notification tray images
     */
    private fun optimizeBitmapForNotification(bitmap: Bitmap): Bitmap? {
        return try {
            // Calculate optimal size for notification large icon
            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            
            // Standard notification large icon size (64dp converted to pixels)
            val targetSize = (64 * density).toInt()
            
            // Create scaled bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            
            // Create optimized bitmap with ARGB_8888 configuration (required by many devices)
            val optimizedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(optimizedBitmap)
            
            // Fill with solid background to avoid transparency issues
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
            
            Log.d(TAG, "Optimized artwork for notification: ${optimizedBitmap.width}x${optimizedBitmap.height}")
            optimizedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing bitmap for notification", e)
            bitmap // Return original as fallback
        }
    }

    /**
     * Get fallback artwork (app icon) when image loading fails
     */
    private fun getFallbackArtwork(): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(
                    it.intrinsicWidth,
                    it.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                optimizeBitmapForNotification(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback artwork", e)
            null
        }
    }

    /**
     * Load and cache artwork for notification display
     */
    private fun loadAndCacheArtwork(artworkUrl: String?) {
        // Skip if we already have this artwork cached
        if (artworkUrl == currentArtworkUrl && cachedArtwork != null) {
            Log.d(TAG, "Using cached artwork for: $artworkUrl")
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Loading artwork: $artworkUrl")
                val bitmap = loadArtwork(artworkUrl)
                
                if (bitmap != null) {
                    // Optimize bitmap for notification display
                    cachedArtwork = optimizeBitmapForNotification(bitmap)
                    currentArtworkUrl = artworkUrl
                    Log.d(TAG, "Artwork loaded and cached successfully")
                } else {
                    // Use fallback artwork
                    cachedArtwork = getFallbackArtwork()
                    currentArtworkUrl = null
                    Log.d(TAG, "Using fallback artwork")
                }
                
                // Force notification update with new artwork
                onUpdateNotification(mediaSession)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading and caching artwork", e)
                // Use fallback artwork on error
                cachedArtwork = getFallbackArtwork()
                currentArtworkUrl = null
                onUpdateNotification(mediaSession)
            }
        }
    }

    /**
     * Extract a simple dominant color from artwork for notification theming
     * Non-blocking, minimal approach that doesn't change architecture
     */
    private fun extractNotificationColor(bitmap: Bitmap?): Int {
        if (bitmap == null) return Color.parseColor("#424242") // Default dark gray
        
        return try {
            val palette = Palette.from(bitmap).generate()
            // Get the most suitable color for notification background
            palette.darkVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: Color.parseColor("#424242") // Fallback
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract color from artwork", e)
            Color.parseColor("#424242") // Safe fallback
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
        // Get the artwork for the large icon - this is the critical fix!
        val artworkBitmap = cachedArtwork ?: getFallbackArtwork()
        
        Log.d(TAG, "Building notification with artwork: ${artworkBitmap != null}")
        if (artworkBitmap != null) {
            Log.d(TAG, "Artwork dimensions: ${artworkBitmap.width}x${artworkBitmap.height}")
        }
        
        // Create content intent to bring app to foreground when notification is tapped
        val contentIntent = Intent(this, com.oldskool.sessions.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Extract color from artwork for notification theming
        val notificationColor = extractNotificationColor(artworkBitmap)
        Log.d(TAG, "Using notification color: ${String.format("#%06X", 0xFFFFFF and notificationColor)}")
        
        // Build a media notification with large icon and palette-based coloring
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_play_arrow_24)
            .setLargeIcon(artworkBitmap) // *** THIS IS THE CRITICAL FIX ***
            .setContentTitle(_currentItem.value?.title ?: "Playing")
            .setContentText(_currentItem.value?.artist ?: "Old Skool Sessions")
            .setContentIntent(contentPendingIntent) // *** TAP TO OPEN APP ***
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(_isPlaying.value)
            .setColor(notificationColor) // *** PALETTE-BASED COLOR ***
            .setColorized(true) // *** ENABLE NOTIFICATION COLORING ***
        
        // Create a media style notification
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

    private var positionTrackingJob: Job? = null

    private fun startPositionTracking() {
        // Cancel any existing tracking job first
        positionTrackingJob?.cancel()
        
        // Start a new tracking job
        positionTrackingJob = serviceScope.launch {
            while (isActive) {  // This keeps the coroutine alive until canceled
                if (_isPlaying.value) {  // Check playback state on each iteration
                    _currentPosition.value = player.currentPosition
                }
                delay(500) // Update every half second
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
        
        // Clean up artwork cache
        cachedArtwork?.recycle()
        cachedArtwork = null
        currentArtworkUrl = null
        
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
