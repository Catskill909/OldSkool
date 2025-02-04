package com.oldskool.sessions.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OSSMediaManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: OSSMediaManager? = null
        
        fun getInstance(context: Context): OSSMediaManager =
            instance ?: synchronized(this) {
                instance ?: OSSMediaManager(context.applicationContext).also { instance = it }
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
        val intent = Intent(context, OSSMediaService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }

    private fun loadArtwork(artworkUrl: String?, callback: (Bitmap?) -> Unit) {
        if (artworkUrl == null) {
            callback(null)
            return
        }

        try {
            Glide.with(context)
                .asBitmap()
                .load(artworkUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        callback(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        callback(null)
                    }
                })
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error loading artwork", e)
            callback(null)
        }
    }

    fun prepareAudio(url: String, title: String, artworkUrl: String?, sourceFragmentId: Int) {
        this.sourceFragmentId = sourceFragmentId
        Log.d("OSSMediaManager", "Preparing audio: $url")
        try {
            // Release any existing player
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Create and prepare new player first
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { 
                    Log.d("OSSMediaManager", "Media prepared")
                    _duration.value = duration.toLong()
                    
                    // Load artwork after media is prepared
                    loadArtwork(artworkUrl) { bitmap ->
                        // Update media session metadata
                        mediaService?.updateMetadata(title, "Old Skool Sessions", bitmap)
                        mediaService?.updatePlaybackState(
                            PlaybackStateCompat.STATE_PAUSED,
                            0L
                        )
                    }
                }
                setOnCompletionListener {
                    Log.d("OSSMediaManager", "Playback completed")
                    _isPlaying.value = false
                    mediaService?.updatePlaybackState(
                        PlaybackStateCompat.STATE_STOPPED,
                        duration.toLong()
                    )
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
                player.start()
                _isPlaying.value = true
                mediaService?.updatePlaybackState(
                    PlaybackStateCompat.STATE_PLAYING,
                    player.currentPosition.toLong()
                )
            } else {
                Log.d("OSSMediaManager", "Pausing playback")
                player.pause()
                _isPlaying.value = false
                mediaService?.updatePlaybackState(
                    PlaybackStateCompat.STATE_PAUSED,
                    player.currentPosition.toLong()
                )
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
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                _currentPosition.value = player.currentPosition.toLong()
            }
        }
    }

    fun release() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }



    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
