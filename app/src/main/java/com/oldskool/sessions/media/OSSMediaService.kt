package com.oldskool.sessions.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.oldskool.sessions.MainActivity
import com.oldskool.sessions.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class OSSMediaService : MediaBrowserServiceCompat() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): OSSMediaService = this@OSSMediaService
    }
    
    // Direct access to the current artwork from OSSMediaManager
    private var directArtworkBitmap: Bitmap? = null
    
    // Function to receive direct artwork from the manager
    fun setDirectArtwork(artwork: Bitmap?) {
        directArtworkBitmap = artwork
        Log.d("OSSMediaService", "DIRECT ARTWORK SET: ${artwork != null}")
        
        // Force notification update with direct artwork
        val state = mediaSession.controller.playbackState
        if (state != null) {
            val metadata = mediaSession.controller.metadata ?: lastMetadata ?: MediaMetadataCompat.Builder().build()
            val notification = createNotification(metadata, state)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("OSSMediaService", "Notification updated with direct artwork")
        }
    }

    companion object {
        internal const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "com.oldskool.sessions.media.PLAYBACK"
        private const val MEDIA_ROOT_ID = "media_root_id"
        
        private fun playbackStateToString(state: Int): String = when (state) {
            PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
            PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
            PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
            PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
            PlaybackStateCompat.STATE_ERROR -> "STATE_ERROR"
            else -> "STATE_UNKNOWN($state)"
        }
    }

    internal lateinit var mediaSession: MediaSessionCompat
    internal lateinit var stateBuilder: PlaybackStateCompat.Builder
    internal lateinit var notificationManager: NotificationManager
    
    // Cache the last known metadata to handle reconnection scenarios
    private var lastMetadata: MediaMetadataCompat? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize the Media Session with minimal controls
        mediaSession = MediaSessionCompat(baseContext, "OSSMediaService").apply {
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
            
            // Enable media button callbacks
            setMediaButtonReceiver(PendingIntent.getBroadcast(
                baseContext,
                0,
                Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(
                    ComponentName(packageName, MediaButtonReceiver::class.java.name)
                ),
                PendingIntent.FLAG_IMMUTABLE
            ))

            // Set initial playback state with all possible actions
            setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build())
        }
        
        // Handle media buttons that come through broadcast receiver
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        val mediaPendingIntent = PendingIntent.getBroadcast(
            this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Restore last known metadata if available
        lastMetadata?.let { metadata ->
            Log.d("OSSMediaService", "Restoring previous metadata: ${metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
            mediaSession.setMetadata(metadata)
        }

        // Initialize the playback state with minimal controls
        stateBuilder = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)

        // Initialize notification manager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    interface Callback {
        fun onPlayClicked()
        fun onPauseClicked()
        fun onStopClicked()
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
                Log.d("OSSMediaService", "MediaSession.Callback.onPlay() called")
                callback?.onPlayClicked()
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onPlay", e)
            }
        }

        override fun onPause() {
            try {
                Log.d("OSSMediaService", "MediaSession.Callback.onPause() called")
                callback?.onPauseClicked()
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error in onPause", e)
            }
        }

        override fun onStop() {
            try {
                Log.d("OSSMediaService", "MediaSession.Callback.onStop() called")
                callback?.onStopClicked()
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

    /**
     * Optimizes any bitmap for notification display across all Android devices
     * This is critical for Samsung and other OEM devices that have custom notification trays
     */
    private fun getOptimizedBitmap(bitmap: Bitmap): Bitmap {
        try {
            // Many OEMs require specific bitmap configurations for notification tray
            // Using production-ready settings that work across devices
            val dm = resources.displayMetrics
            val density = dm.density
            
            // Standard size and ratio for notification tray icons
            val width = (64 * density).toInt()
            val height = (64 * density).toInt()
            
            // Create a scaled bitmap that meets the requirements
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            
            // Create a clean bitmap with the standard ARGB_8888 configuration
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            
            // Draw with a solid background to avoid transparency issues
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
            
            return resultBitmap
        } catch (e: Exception) {
            Log.e("OSSMediaService", "Failed to optimize bitmap", e)
            return bitmap  // Return original as fallback
        }
    }
    
    internal fun createNotification(metadata: MediaMetadataCompat, state: PlaybackStateCompat): Notification {
        Log.d("OSSMediaService", "Creating notification")

        // Get the controller
        val controller = mediaSession.controller
        
        // Get the artwork
        val currentMetadata = metadata
        var artworkBitmap = currentMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        Log.d("OSSMediaService", "Creating notification with artwork: ${artworkBitmap != null}")
        
        // Process the bitmap for notification display - critical for notification tray
        if (artworkBitmap != null) {
            Log.d("OSSMediaService", "Original artwork dimensions: ${artworkBitmap.width}x${artworkBitmap.height}, config: ${artworkBitmap.config}")
            
            // Production-grade bitmap sanitization for notification tray
            try {
                // Step 1: Calculate proper notification size based on device density
                // Use exact size requirements known to work across devices
                val displayMetrics = resources.displayMetrics
                val density = displayMetrics.density
                
                // Standard sizes that work reliably in notifications across devices
                val targetWidth = (94 * density).toInt() // ~94dp for large icons
                val targetHeight = (94 * density).toInt()
                
                Log.d("OSSMediaService", "Target dimensions: ${targetWidth}x${targetHeight} for density: $density")
                
                // Step 2: Create drawable from the bitmap with correct density
                val bitmapDrawable = BitmapDrawable(resources, artworkBitmap)
                bitmapDrawable.setBounds(0, 0, targetWidth, targetHeight)
                
                // Step 3: Create high quality bitmap with ARGB_8888 config (required by many devices)
                // Using a bitmap that matches the device's density requirements
                val sanitizedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sanitizedBitmap)
                
                // Fill with a background color to ensure no transparency issues
                canvas.drawColor(Color.BLACK) 
                
                // Draw the drawable at the exact right size for this device
                bitmapDrawable.draw(canvas)
                
                // Replace the original bitmap with our device-optimized version
                artworkBitmap = sanitizedBitmap
                
                Log.d("OSSMediaService", "Sanitized artwork: ${artworkBitmap.width}x${artworkBitmap.height}, config: ${artworkBitmap.config}, density: ${displayMetrics.densityDpi}")
            } catch (e: Exception) {
                Log.e("OSSMediaService", "Error sanitizing bitmap", e)
                // Fall back to original bitmap if sanitization fails
            }
        } else {
            Log.d("OSSMediaService", "createNotification: Artwork NOT found in metadata. Using fallback.")
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)

        // Create content intent
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create media control actions
        val playPauseAction: NotificationCompat.Action = if (state.state == PlaybackStateCompat.STATE_PLAYING) {
            // Create pause action
            val pauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
            NotificationCompat.Action(R.drawable.baseline_pause_24, "Pause", pauseIntent)
        } else {
            // Create play action
            val playIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
            NotificationCompat.Action(R.drawable.baseline_play_arrow_24, "Play", playIntent)
        }
        
        // Create stop action
        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)
        val stopAction = NotificationCompat.Action(R.drawable.baseline_stop_24, "Stop", stopIntent)

        // USING THE DIRECT PATH that works in the player view
        // This bypasses MediaSession metadata completely for the image
        val notificationArtwork = directArtworkBitmap
        
        // Log the direct bitmap properties for diagnosis
        if (notificationArtwork != null) {
            Log.d("OSSMediaService", "USING DIRECT ARTWORK BITMAP: ${notificationArtwork.width}x${notificationArtwork.height}, config: ${notificationArtwork.config}")
        } else {
            Log.d("OSSMediaService", "No direct artwork available, using app icon")
        }
        
        // Configure the notification using the direct artwork that works in the player
        builder.setContentTitle(currentMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            .setContentText(currentMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
            .setLargeIcon(notificationArtwork ?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1) // Show both actions in compact view
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)
        
        // Add media actions to notification
        Log.d("OSSMediaManager_TRACE", "6. createNotification FINAL CHECK. Artwork in metadata: ${currentMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) != null}")
        builder.addAction(playPauseAction)
        builder.addAction(stopAction)

        return builder.build()
    }

    fun updateMetadata(title: String, artist: String?, artwork: Bitmap?) {
        try {
            Log.d("OSSMediaService", "Updating metadata: title=$title, hasArtwork=${artwork != null}")
            
            val metadata = MediaMetadataCompat.Builder().apply {
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                
                artwork?.let {
                Log.d("OSSMediaManager_TRACE", "5. Service updateMetadata RECEIVED. Has artwork: ${it != null}. Size: ${it.width}x${it.height}")
                    try {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    } catch (e: Exception) {
                        Log.e("OSSMediaService", "Error setting artwork bitmap", e)
                    }
                }
            }.build()
            
            // Cache the metadata
            lastMetadata = metadata
            
            Log.d("OSSMediaService", "Setting metadata on media session - Title: ${metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
            mediaSession.setMetadata(metadata)
            
            // Verify media session state after update
            val sessionMetadata = mediaSession.controller.metadata
            Log.d("OSSMediaService", "Media session metadata after update - Title: ${sessionMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
            
            // Force notification update with latest metadata
            val state = mediaSession.controller.playbackState
            if (state != null) {
                // Create fresh notification with latest metadata
                val notification = createNotification(metadata, state)
                
                // Update notification based on playback state
                if (state.state == PlaybackStateCompat.STATE_PLAYING) {
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
            
            Log.d("OSSMediaService", "Metadata update complete")
        } catch (e: Exception) {
            Log.e("OSSMediaService", "Error updating metadata", e)
            // Try to recover using cached metadata
            lastMetadata?.let { cachedMetadata ->
                Log.d("OSSMediaService", "Attempting recovery with cached metadata")
                mediaSession.setMetadata(cachedMetadata)
            }
        }
    }

    fun updatePlaybackState(state: Int, position: Long) {
        Log.d("OSSMediaService", "=== Playback State Change ===")
        Log.d("OSSMediaService", "Updating state to: ${playbackStateToString(state)}, position: $position")
        Log.d("OSSMediaService", "Current metadata: ${mediaSession.controller.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
        
        // Set available actions based on state
        val actions = PlaybackStateCompat.ACTION_PLAY or
                     PlaybackStateCompat.ACTION_PAUSE or
                     PlaybackStateCompat.ACTION_PLAY_PAUSE or
                     PlaybackStateCompat.ACTION_STOP or
                     PlaybackStateCompat.ACTION_SEEK_TO

        // Log state transition
        Log.d("OSSMediaService", "State transition: ${playbackStateToString(state)}")
        Log.d("OSSMediaService", "Metadata present: ${mediaSession.controller.metadata != null}")
        
        val playbackState = stateBuilder
            .setActions(actions)
            .setState(state, position, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        // Update notification with fresh metadata
        val currentMetadata = mediaSession.controller.metadata
        Log.d("OSSMediaService", "Creating notification with metadata: ${currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
        val notification = createNotification(currentMetadata ?: lastMetadata ?: MediaMetadataCompat.Builder().build(), playbackState)
        
        // Show as foreground service when playing
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            Log.d("OSSMediaService", "Starting foreground service with notification")
            Log.d("OSSMediaService", "Notification metadata: ${notification.extras?.getString(NotificationCompat.EXTRA_TITLE)}")
            startForeground(NOTIFICATION_ID, notification)
        } else {
            Log.d("OSSMediaService", "Updating notification in background state: ${playbackStateToString(state)}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("OSSMediaService", "Background notification updated with metadata: ${notification.extras?.getString(NotificationCompat.EXTRA_TITLE)}")
        }
    }
}
