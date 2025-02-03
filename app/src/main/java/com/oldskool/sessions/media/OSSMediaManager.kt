package com.oldskool.sessions.media

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OSSMediaManager(private val context: Context) {

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Current track metadata
    @Suppress("unused")
    private val _currentTitle = MutableStateFlow<String?>(null)
    @Suppress("unused")
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    @Suppress("unused")
    private val _currentArtwork = MutableStateFlow<String?>(null)
    @Suppress("unused")
    val currentArtwork: StateFlow<String?> = _currentArtwork.asStateFlow()

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser?.sessionToken?.let { token ->
                mediaController = MediaControllerCompat(context, token).apply {
                    registerCallback(controllerCallback)
                }
            }
        }

        override fun onConnectionSuspended() {
            // Handle connection suspension
        }

        override fun onConnectionFailed() {
            // Handle connection failure
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let {
                _isPlaying.value = it.state == PlaybackStateCompat.STATE_PLAYING
                _currentPosition.value = it.position
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let {
                _currentTitle.value = it.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                _duration.value = it.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            }
        }
    }

    fun connect() {
        if (mediaBrowser?.isConnected == true) return

        mediaBrowser = MediaBrowserCompat(
            context,
            ComponentName(context, OSSMediaService::class.java),
            connectionCallback,
            null
        ).apply {
            connect()
        }
    }

    fun disconnect() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser?.disconnect()
        mediaBrowser = null
    }

    fun playAudio(url: String, title: String, artworkUrl: String?) {
        _currentTitle.value = title
        _currentArtwork.value = artworkUrl
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            )
            .build()
        audioManager.requestAudioFocus(focusRequest)

        // Extract MP3 URL from WordPress excerpt
        val mp3Url = url.substringAfter("<p>").substringBefore("</p>").trim()
        
        (context as? Context)?.startService(
            android.content.Intent(context, OSSMediaService::class.java)
        )

        (context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager)?.let {
            mediaController?.transportControls?.playFromUri(android.net.Uri.parse(mp3Url), null)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            mediaController?.transportControls?.pause()
        } else {
            mediaController?.transportControls?.play()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.transportControls?.seekTo(position)
    }

    fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
