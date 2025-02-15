package com.oldskool.sessions.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
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
                override fun onPlayPauseClicked() {
                    togglePlayPause()
                }
                
                override fun onSeekTo(position: Long) {
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
                            Log.d("OSSMediaManager", "Artwork load cleared")
                            callback(null)
                        }
                        
                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            Log.e("OSSMediaManager", "Artwork load failed")
                            if (retryCount > 0) {
                                Log.d("OSSMediaManager", "Retrying artwork load (${retryCount - 1} attempts left)")
                                loadArtwork(artworkUrl, callback, retryCount - 1)
                            } else {
                                Log.e("OSSMediaManager", "All artwork load retries exhausted")
                                callback(null)
                            }
                        }
                    })
            } ?: run {
                Log.e("OSSMediaManager", "Context reference is null")
                callback(null)
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error loading artwork", e)
            if (retryCount > 0) {
                Log.d("OSSMediaManager", "Retrying after error (${retryCount - 1} attempts left)")
                loadArtwork(artworkUrl, callback, retryCount - 1)
            } else {
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
        this.sourceFragmentId = sourceFragmentId
        Log.d("OSSMediaManager", "=== Track Change ===")
        Log.d("OSSMediaManager", "Preparing new audio track - Title: $title")
        
        // Clean up previous track without destroying the media session
        cleanupCurrentTrack()
        try {
            // Release any existing player
            Log.d("OSSMediaManager", "=== Track Change Lifecycle ===")
            Log.d("OSSMediaManager", "1. Releasing previous player")
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Create and prepare new player first
            Log.d("OSSMediaManager", "2. Creating new player instance")
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(contextRef.get(), PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(url)
                setOnPreparedListener {
                    Log.d("OSSMediaManager", "3. Media prepared")
                    _duration.value = duration.toLong()
                    
                    // Initialize metadata state immediately
                    Log.d("OSSMediaManager", "4. Initializing metadata state")
                    Log.d("OSSMediaManager", "Previous metadata state - Title: $cachedTitle, HasArtwork: ${cachedArtwork != null}")
                    cachedTitle = title
                    _currentTitle.value = title
                    _currentArtwork.value = artworkUrl
                    
                    // Initial metadata update without artwork
                    updateMediaSessionMetadata()
                    
                    // Load and update artwork asynchronously
                    Log.d("OSSMediaManager", "5. Starting artwork load for URL: $artworkUrl")
                    loadArtwork(artworkUrl, { bitmap ->
                        Log.d("OSSMediaManager", "6. Artwork load complete - Success: ${bitmap != null}")
                        cachedArtwork = bitmap
                        
                        mediaService?.let { service ->
                            Log.d("OSSMediaManager", "7. Updating media session with complete metadata")
                            // Ensure complete metadata update with artwork
                            updateMediaSessionMetadata()
                            Log.d("OSSMediaManager", "8. Setting initial playback state")
                            service.updatePlaybackState(
                                PlaybackStateCompat.STATE_PAUSED,
                                0L
                            )
                            Log.d("OSSMediaManager", "9. Track initialization complete")
                        } ?: run {
                            Log.e("OSSMediaManager", "MediaService null during prepare, queueing update")
                            pendingMetadataUpdate = true
                        }
                    }, 3)
                }
                setOnCompletionListener {
                    Log.d("OSSMediaManager", "Playback completed")
                    try {
                        _isPlaying.value = false
                        seekTo(0) // Reset MediaPlayer position first
                        _currentPosition.value = 0
                        mediaService?.updatePlaybackState(
                            PlaybackStateCompat.STATE_STOPPED,
                            0L
                        )
                    } catch (e: Exception) {
                        Log.e("OSSMediaManager", "Error updating state on completion", e)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("OSSMediaManager", "Media player error: what=$what extra=$extra")
                    _isPlaying.value = false
                    mediaService?.updatePlaybackState(
                        PlaybackStateCompat.STATE_ERROR,
                        0L
                    )
                    true
                }
                prepareAsync()
            }
            
            _currentTitle.value = title
            _currentArtwork.value = artworkUrl
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error preparing audio", e)
            _isPlaying.value = false
            mediaService?.updatePlaybackState(
                PlaybackStateCompat.STATE_ERROR,
                0L
            )
        }
    }

    fun togglePlayPause() {
        Log.d("OSSMediaManager", "Toggle play/pause")
        try {
            val player = mediaPlayer
            if (player == null) {
                Log.w("OSSMediaManager", "MediaPlayer is null")
                return
            }
            
            if (!player.isPlaying) {
                Log.d("OSSMediaManager", "Starting playback")
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
                Log.d("OSSMediaManager", "Pausing playback")
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
            Log.e("OSSMediaManager", "Error toggling play/pause", e)
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
            Log.e("OSSMediaManager", "Error updating progress", e)
        }
    }

    private fun updateMediaSessionMetadata() {
        try {
            Log.d("OSSMediaManager", "=== Media Session Update ===")
            Log.d("OSSMediaManager", "Updating media session metadata - Title: $cachedTitle")
            Log.d("OSSMediaManager", "MediaService available: ${mediaService != null}")
            Log.d("OSSMediaManager", "Pending update: $pendingMetadataUpdate")
            
            // Validate metadata state
            if (cachedTitle == null && cachedArtwork == null) {
                Log.w("OSSMediaManager", "Attempting to update with null metadata")
                return
            }
            
            mediaService?.let { service ->
                service.updateMetadata(
                    cachedTitle ?: "",
                    "Old Skool Sessions",
                    cachedArtwork
                )
                Log.d("OSSMediaManager", "Media session metadata updated successfully")
            } ?: run {
                Log.e("OSSMediaManager", "Failed to update metadata: MediaService is null")
                pendingMetadataUpdate = true
            }
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error updating media session metadata", e)
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
