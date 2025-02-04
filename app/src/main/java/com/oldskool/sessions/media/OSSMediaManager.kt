package com.oldskool.sessions.media

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OSSMediaManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

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

    fun playAudio(url: String, title: String, artworkUrl: String?) {
        Log.d("OSSMediaManager", "Playing audio: $url")
        try {
            // Release any existing player
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Create and prepare new player
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { 
                    Log.d("OSSMediaManager", "Media prepared, starting playback")
                    start()
                    _isPlaying.value = true
                    _duration.value = duration.toLong()
                }
                setOnCompletionListener {
                    Log.d("OSSMediaManager", "Playback completed")
                    _isPlaying.value = false
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("OSSMediaManager", "Media player error: what=$what extra=$extra")
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
            
            _currentTitle.value = title
            _currentArtwork.value = artworkUrl
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error playing audio", e)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        Log.d("OSSMediaManager", "Toggle play/pause")
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    Log.d("OSSMediaManager", "Pausing playback")
                    player.pause()
                    _isPlaying.value = false
                } else {
                    Log.d("OSSMediaManager", "Starting playback")
                    player.start()
                    _isPlaying.value = true
                }
            } ?: Log.w("OSSMediaManager", "MediaPlayer is null")
        } catch (e: Exception) {
            Log.e("OSSMediaManager", "Error toggling play/pause", e)
            _isPlaying.value = false
        }
    }

    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position
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
