package com.oldskool.sessions.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

// Fallback helper is no longer needed; fallback handled in OSSMediaService.kt notification builder.

class OSSMediaManager private constructor(context: Context) {
    private val contextRef = WeakReference(context.applicationContext)
    companion object {
        @Volatile
        private var instance: OSSMediaManager? = null
        
        fun getInstance(context: Context): OSSMediaManager =
            instance ?: synchronized(this) {
                instance ?: OSSMediaManager(context).also { instance = it }
            }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaService: OSSMediaService? = null
    private var sourceFragmentId: Int = 0

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Current track metadata
    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentArtwork = MutableStateFlow<String?>(null)
    val currentArtwork: StateFlow<String?> = _currentArtwork.asStateFlow()
    
    // Cache latest metadata
    private var cachedTitle: String? = null
    private var cachedArtwork: Bitmap? = null
    private var pendingMetadataUpdate = false
    
    private fun ensureMetadataSync() {
        if (pendingMetadataUpdate) {
            Log.d("OSSMediaManager", "Ensuring metadata sync")
            updateMediaSessionMetadata()
            pendingMetadataUpdate = false
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OSSMediaService.LocalBinder
            mediaService = binder.getService()
            mediaService?.setCallback(object : OSSMediaService.Callback {
                override fun onPlayClicked() {
                    Log.d("OSSMediaManager", "Notification PLAY action received")
                    if (!_isPlaying.value) {
                        togglePlayPause()
                    }
                }
                
                override fun onPauseClicked() {
                    Log.d("OSSMediaManager", "Notification PAUSE action received")
                    if (_isPlaying.value) {
                        togglePlayPause()
                    }
                }
                
                override fun onStopClicked() {
                    Log.d("OSSMediaManager", "Notification STOP action received")
                    try {
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                player.stop()
                            }
                            _isPlaying.value = false
                            mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0)
                            // Could also call release() to fully clean up resources
                        }
                    } catch (e: Exception) {
                        Log.e("OSSMediaManager", "Error stopping playback", e)
                    }
                }
                
                override fun onSeekTo(position: Long) {
                    Log.d("OSSMediaManager", "Notification SEEK action received: $position")
                    seekTo(position)
                }

                override fun getSourceFragmentId(): Int {
                    return sourceFragmentId
                }
            })
            Log.d("OSSMediaManager", "Media service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaService = null
            Log.d("OSSMediaManager", "Media service disconnected")
        }
    }

    init {
        // Start and bind to the media service
        contextRef.get()?.let { ctx ->
            val intent = Intent(ctx, OSSMediaService::class.java)
            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            ctx.startService(intent)
        }
    }

    private fun loadArtwork(artworkUrl: String?, callback: (Bitmap?) -> Unit, retryCount: Int = 3) {
        if (artworkUrl == null) {
            Log.d("OSSMediaManager", "No artwork URL provided")
            Log.d("OSSMediaManager", "Using default placeholder artwork fallback")
            callback(null)
            return
        }

        try {
            contextRef.get()?.let { ctx ->
                Log.d("OSSMediaManager", "Loading artwork from: $artworkUrl")
                
                Glide.with(ctx)
                    .asBitmap()
                    .load(artworkUrl)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            Log.d("OSSMediaManager", "Artwork loaded successfully")
                            callback(resource)
                        }
                        
                        override fun onLoadCleared(placeholder: Drawable?) {
                            Log.d("OSSMediaManager_TRACE", "3b. Glide onLoadCleared. Artwork load cleared")
                            Log.d("OSSMediaManager", "Using default placeholder artwork fallback")
                            callback(null)
                        }
                        
                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            Log.e("OSSMediaManager_TRACE", "3b. Glide onLoadFailed. Artwork download FAILED for $artworkUrl")
                            // Even if artwork fails, we should proceed with audio playback.
                            // The notification will use the fallback icon.
                            cachedArtwork = null // Ensure no stale artwork is used
                            callback(null)
                            if (retryCount > 0) {
                                Log.d("OSSMediaManager_TRACE", "Retrying artwork load (${retryCount - 1} attempts left)")
                                loadArtwork(artworkUrl, callback, retryCount - 1)
                            } else {
                                Log.e("OSSMediaManager", "All artwork load retries exhausted")
                                Log.d("OSSMediaManager", "Using default placeholder artwork fallback")
                                callback(null)
                            }
                        }
                    })
            } ?: run {
                Log.e("OSSMediaManager", "Context reference is null")
                Log.d("OSSMediaManager_TRACE", "2. Context reference is null. Using default placeholder artwork fallback")
                callback(null)
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error loading artwork", e)
            if (retryCount > 0) {
                Log.d("OSSMediaManager_TRACE", "Retrying after error (${retryCount - 1} attempts left)")
                loadArtwork(artworkUrl, callback, retryCount - 1)
            } else {
                Log.d("OSSMediaManager_TRACE", "All retries exhausted. Using default placeholder artwork fallback")
                callback(null)
            }
        }
    }

    fun cleanupPlayback() {
        Log.d("OSSMediaManager", "Cleaning up playback state")
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
        }
        _isPlaying.value = false
        _currentPosition.value = 0  // Reset position to start
        
        // Update media session state but preserve metadata
        mediaService?.updatePlaybackState(
            PlaybackStateCompat.STATE_STOPPED,
            0L
        )
    }

    private fun cleanupCurrentTrack() {
        Log.d("OSSMediaManager", "Preparing for new track")
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
        }
    }

    fun prepareAudio(url: String, title: String, artworkUrl: String?, sourceFragmentId: Int) {
        Log.d("OSSMediaManager_TRACE", "1. prepareAudio START. URL: $url")
        this.sourceFragmentId = sourceFragmentId
        Log.d("OSSMediaManager", "=== Track Change ===")
        Log.d("OSSMediaManager", "Preparing new audio track - Title: $title")

        cleanupCurrentTrack()

        try {
            Log.d("OSSMediaManager_TRACE", "1. Creating new player instance")
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(contextRef.get(), PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(url)

                setOnPreparedListener { preparedPlayer ->
                    Log.d("OSSMediaManager_TRACE", "2. Media prepared")
                    _duration.value = preparedPlayer.duration.toLong()

                    // Set initial metadata without artwork
                    cachedTitle = title
                    cachedArtwork = null
                    updateMediaSessionMetadata() // Update session with title first

                    // Set initial playback state to paused
                    mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, 0L)

                    // Now, load the artwork asynchronously
                    Log.d("OSSMediaManager_TRACE", "3. Starting artwork load for URL: $artworkUrl")
                    loadArtwork(artworkUrl, { bitmap ->
                        Log.d("OSSMediaManager_TRACE", "4. Artwork load complete. Updating metadata...")
                        // Sanitize the bitmap to a mutable, standard format
                        val mutableBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                        if (mutableBitmap != null) {
                            Log.d("OSSMediaManager_TRACE", "4. Artwork load complete. SANITIZED artwork. New size: ${mutableBitmap.width}x${mutableBitmap.height}")
                        } else {
                            Log.w("OSSMediaManager", "Artwork bitmap was null after load.")
                        }
                        cachedArtwork = mutableBitmap
                        mediaService?.let { service ->
                            Log.d("OSSMediaManager_TRACE", "5. Updating media session with artwork")
                            updateMediaSessionMetadata() // Update again with artwork
                            
                            // CRITICAL FIX: Send the direct artwork to the service using the same path as player view
                            Log.d("OSSMediaManager_TRACE", "5b. Setting DIRECT artwork on service, bypassing MediaSession")
                            service.setDirectArtwork(mutableBitmap)

                            // --- FORCE NOTIFICATION REBUILD WITH NEW ARTWORK ---
                            val state = service.mediaSession.controller.playbackState
                            val metadata = service.mediaSession.controller.metadata
                            if (state != null && metadata != null) {
                                Log.d("OSSMediaManager_TRACE", "6. Forcing notification rebuild with artwork: ${bitmap != null}")
                                val notification = service.createNotification(metadata, state)
                                service.notificationManager.notify(OSSMediaService.NOTIFICATION_ID, notification)
                            }
                    } ?: run {
                        Log.e("OSSMediaManager", "MediaService null, queueing metadata update")
                        pendingMetadataUpdate = true
                    }
                    })
                }

                setOnCompletionListener {
                    Log.d("OSSMediaManager_TRACE", "7. Media playback completed")
                    _isPlaying.value = false
                    mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("OSSMediaManager_TRACE", "8. MediaPlayer Error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
                    true // Indicates we've handled the error
                }

                Log.d("OSSMediaManager_TRACE", "9. Preparing player asynchronously")
                prepareAsync()
            }

            // Update UI-facing state immediately
            _currentTitle.value = title
            _currentArtwork.value = artworkUrl
            _currentPosition.value = 0
            _isPlaying.value = false

        } catch (e: Exception) {
            Log.e("OSSMediaManager_TRACE", "Error preparing audio", e)
            _isPlaying.value = false
            mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L)
        }
    }

    fun togglePlayPause() {
        Log.d("OSSMediaManager_TRACE", "togglePlayPause START")
        try {
            val player = mediaPlayer
            if (player == null) {
                Log.w("OSSMediaManager", "MediaPlayer is null")
                return
            }
            
            if (!player.isPlaying) {
                Log.d("OSSMediaManager_TRACE", "Starting playback")
                // Ensure metadata is synchronized before state change
                ensureMetadataSync()
                
                player.start()
                _isPlaying.value = true
                
                mediaService?.let { service ->
                    // Force metadata refresh to ensure lockscreen/notification are up to date
                    updateMediaSessionMetadata()
                    service.updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        player.currentPosition.toLong()
                    )
                } ?: run {
                    Log.e("OSSMediaManager", "MediaService null during play transition")
                    pendingMetadataUpdate = true
                }
            } else {
                Log.d("OSSMediaManager_TRACE", "Pausing playback")
                player.pause()
                _isPlaying.value = false
                
                mediaService?.let { service ->
                    // Ensure metadata stays in sync during pause
                    updateMediaSessionMetadata()
                    service.updatePlaybackState(
                        PlaybackStateCompat.STATE_PAUSED,
                        player.currentPosition.toLong()
                    )
                } ?: run {
                    Log.e("OSSMediaManager", "MediaService null during pause transition")
                    pendingMetadataUpdate = true
                }
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager_TRACE", "Error toggling play/pause", e)
            _isPlaying.value = false
            mediaService?.updatePlaybackState(
                PlaybackStateCompat.STATE_ERROR,
                0L
            )
        }
    }

    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position
        mediaService?.updatePlaybackState(
            if (_isPlaying.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            position
        )
    }

    fun updateProgress() {
        try {
            mediaPlayer?.let { player ->
                if (_isPlaying.value) {
                    val position = player.currentPosition.toLong()
                    _currentPosition.value = position
                }
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager_TRACE", "Error updating progress", e)
        }
    }

    private fun updateMediaSessionMetadata() {
        try {
            Log.d("OSSMediaManager_TRACE", "updateMediaSessionMetadata START")
            Log.d("OSSMediaManager_TRACE", "Updating media session metadata - Title: $cachedTitle")
            Log.d("OSSMediaManager_TRACE", "MediaService available: ${mediaService != null}")
            Log.d("OSSMediaManager_TRACE", "Pending update: $pendingMetadataUpdate")
            
            // Validate metadata state
            if (cachedTitle == null && cachedArtwork == null) {
                Log.w("OSSMediaManager_TRACE", "Attempting to update with null metadata")
                return
            }
            
            mediaService?.let { service ->
                Log.d("OSSMediaManager_TRACE", "4. updateMediaSessionMetadata called. Notifying service.")
                service.updateMetadata(
                    cachedTitle ?: "",
                    "Old Skool Sessions",
                    cachedArtwork
                )
                Log.d("OSSMediaManager_TRACE", "5. Media session metadata updated successfully")
            } ?: run {
                Log.e("OSSMediaManager_TRACE", "Failed to update metadata: MediaService is null")
                pendingMetadataUpdate = true
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager_TRACE", "Error updating media session metadata", e)
            pendingMetadataUpdate = true
        }
    }

    private fun release() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        
        // Reset all state flows
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
        _currentTitle.value = null
        _currentArtwork.value = null
        
        // Clear cached metadata
        cachedTitle = null
        cachedArtwork = null
        
        // Reset media service state
        mediaService?.updatePlaybackState(PlaybackStateCompat.STATE_NONE, 0)
        mediaService?.updateMetadata("", null, null)
    }
    
    fun destroy() {
        release()
        try {
            contextRef.get()?.unbindService(serviceConnection)
            contextRef.get()?.let { ctx ->
                ctx.stopService(Intent(ctx, OSSMediaService::class.java))
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error unbinding service", e)
        }
        mediaService = null
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
