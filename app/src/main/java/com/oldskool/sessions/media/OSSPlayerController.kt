package com.oldskool.sessions.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.oldskool.sessions.models.AudioItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Controller for the OSSPlayerService that provides a clean API for UI components.
 * This maintains the "ONE AUDIO TRUTH" architecture by being the single point of interaction
 * with the player service.
 */
@OptIn(UnstableApi::class)
class OSSPlayerController(private val context: Context) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "OSSPlayerController"
        
        // Singleton pattern to ensure ONE AUDIO TRUTH
        @Volatile
        private var INSTANCE: OSSPlayerController? = null
        
        fun getInstance(context: Context): OSSPlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OSSPlayerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // MediaController for controlling the session
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    // LiveData for UI observation
    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> get() = _isPlaying
    
    private val _currentPosition = MutableLiveData<Long>()
    val currentPosition: LiveData<Long> get() = _currentPosition
    
    private val _duration = MutableLiveData<Long>()
    val duration: LiveData<Long> get() = _duration
    
    private val _currentItem = MutableLiveData<AudioItem?>()
    val currentItem: LiveData<AudioItem?> get() = _currentItem

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    
    // Connect to the service when initialized
    init {
        connectToService()
    }
    
    private fun connectToService() {
        Log.d(TAG, "Connecting to service")
        
        try {
            // Release any existing controller first
            releaseController()
            
            // Start the service if it's not running
            val intent = Intent(context, OSSPlayerService::class.java)
            context.startService(intent)
            
            // Connect to the session
            val sessionToken = SessionToken(context, ComponentName(context, OSSPlayerService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    if (mediaController != null) {
                        mediaController?.addListener(PlayerListener())
                        Log.d(TAG, "Connected to media session successfully")
                        
                        // Initial state update
                        updatePlaybackState()
                    } else {
                        Log.e(TAG, "Failed to get media controller, received null")
                        // Try to reconnect after a delay
                        Handler(Looper.getMainLooper()).postDelayed({ 
                            connectToService() 
                        }, 1000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to media session", e)
                    // Try to reconnect after a delay
                    Handler(Looper.getMainLooper()).postDelayed({ 
                        connectToService() 
                    }, 1000)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating connection to service", e)
        }
    }
    
    private fun updatePlaybackState() {
        mediaController?.let { controller ->
            _isPlaying.postValue(controller.isPlaying)
            _currentPosition.postValue(controller.currentPosition)
            _duration.postValue(controller.duration)
            
            // We would need to map the MediaItem back to AudioItem
            // This is simplified here - in a real implementation we'd need to store 
            // the mapping or include enough metadata
        }
    }
    
    /**
     * Play an audio item - this is the main entry point for starting playback
     */
    fun playAudio(item: AudioItem) {
        val intent = Intent(context, OSSPlayerService::class.java).apply {
            action = OSSPlayerService.ACTION_PLAY
            putExtra(OSSPlayerService.EXTRA_AUDIO_ITEM, item)
        }
        context.startService(intent)
        
        // Update UI state immediately for better user experience
        _currentItem.postValue(item)
    }
    
    /**
     * Load an audio item without auto-playing - this prepares the audio without starting playback
     */
    fun loadAudio(item: AudioItem) {
        val intent = Intent(context, OSSPlayerService::class.java).apply {
            action = OSSPlayerService.ACTION_LOAD
            putExtra(OSSPlayerService.EXTRA_AUDIO_ITEM, item)
        }
        context.startService(intent)
        
        // Update UI state immediately for better user experience
        _currentItem.postValue(item)
    }
    
    /**
     * Toggle between play and pause
     */
    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        } ?: run {
            Log.w(TAG, "MediaController not connected")
        }
    }
    
    /**
     * Stop playback completely
     */
    fun stop() {
        mediaController?.stop()
    }
    
    /**
     * Seek to a specific position in the current item
     */
    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }
    
    /**
     * Get the current playback position as a flow
     */
    val positionFlow: Flow<Long>
        get() = mediaController?.let {
            // In a real implementation, we would create a Flow from the controller
            // This is just a placeholder
            emptyFlow()
        } ?: emptyFlow()
    
    inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.postValue(isPlaying)
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }
    }
    
    override fun onStart(owner: LifecycleOwner) {
        if (mediaController == null) {
            connectToService()
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        // Don't disconnect, we want to keep playback going in background
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        releaseController()
    }
    
    fun releaseController() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        controllerFuture = null
    }
}
