package com.oldskool.sessions.ui.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.oldskool.sessions.R
import com.oldskool.sessions.media.OSSPlayerController
import com.oldskool.sessions.models.AudioItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Updated PlayerDetailFragment that uses the Media3-based OSSPlayerController.
 * This fragment is part of the ONE AUDIO TRUTH architecture.
 */
class PlayerDetailFragmentMedia3 : Fragment() {

    private lateinit var playerController: OSSPlayerController

    // UI Components
    private lateinit var backButton: View
    private lateinit var albumArt: ImageView
    private lateinit var titleText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var progressBar: SeekBar
    private lateinit var timeContainer: View
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView

    private var progressUpdateJob: Job? = null
    private var userIsSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_player_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Initialize UI components
            backButton = view.findViewById(R.id.backButton)
            albumArt = view.findViewById(R.id.albumArt)
            titleText = view.findViewById(R.id.titleText)
            playPauseButton = view.findViewById(R.id.playPauseButton)
            progressBar = view.findViewById(R.id.progressBar)
            timeContainer = view.findViewById(R.id.timeContainer)
            currentTime = view.findViewById(R.id.currentTime)
            totalTime = view.findViewById(R.id.totalTime)
            
            // Initialize time display with default values
            currentTime.text = "00:00"
            totalTime.text = "--:--"

            // Initialize PlayerController - this is our ONE AUDIO TRUTH controller
            playerController = OSSPlayerController.getInstance(requireContext().applicationContext)
            
            // Register this fragment with the lifecycle owner so the controller can respond to lifecycle events
            lifecycle.addObserver(playerController)

            setupClickListeners()
            setupProgressBar()
            setupObservers()

            // Get arguments using the proper type constraint
            val args = try {
                val navArgs: PlayerDetailFragmentArgs by navArgs()
                navArgs
            } catch (e: Exception) {
                Log.e("PlayerDetailFragment", "Failed to get arguments: ${e.message}")
                findNavController().navigateUp()
                return
            }

            // Set initial state and prepare audio
            titleText.text = args.title
            loadAlbumArt(args.imageUrl)

            Log.d("PlayerDetailFragment", "Preparing audio with URL: ${args.audioUrl}")
            
            // Convert to AudioItem model for the new architecture
            val audioItem = AudioItem(
                id = System.currentTimeMillis().toString(), // Generate a unique ID
                title = args.title,
                artist = null, // This can be added to the navigation args if needed
                album = null,  // This can be added to the navigation args if needed
                audioUrl = args.audioUrl, // Now directly using String
                albumArtUrl = args.imageUrl,
                sourceFragmentId = R.id.navigation_player_detail_media3 // Using the Media3 fragment ID
            )
            
            // Load the audio without auto-playing using our new controller
            playerController.loadAudio(audioItem)
        } catch (e: Exception) {
            Log.e("PlayerDetailFragment", "Error in onViewCreated: ${e.message}")
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressUpdateJob?.cancel()
        // No need to manually clean up - the lifecycle observer handles it
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            // Stop the playback when navigating back
            playerController.stop()
            findNavController().navigateUp()
        }

        playPauseButton.setOnClickListener {
            playerController.togglePlayPause()
        }
    }

    private fun setupProgressBar() {
        try {
            progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentTime.text = formatTime(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userIsSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    try {
                        userIsSeeking = false
                        seekBar?.progress?.let { progress ->
                            if (progress >= 0) {
                                playerController.seekTo(progress.toLong())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerDetailFragment", "Error seeking to position", e)
                        // Reset seeking state
                        userIsSeeking = false
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("PlayerDetailFragment", "Error setting up progress bar", e)
            // Disable progress bar if setup fails
            progressBar.isEnabled = false
        }
    }

    private fun setupObservers() {
        // Observe playing state to update UI
        playerController.isPlaying.observe(viewLifecycleOwner, Observer { isPlaying ->
            playPauseButton.setImageResource(
                if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
            )
            // Ensure proper accessibility description
            playPauseButton.contentDescription = if (isPlaying) "Pause" else "Play"
        })

        // Observe duration changes
        playerController.duration.observe(viewLifecycleOwner, Observer { duration ->
            if (duration > 0) {
                progressBar.max = duration.toInt()
                totalTime.text = formatTime(duration)
            }
        })

        // Observe position changes
        playerController.currentPosition.observe(viewLifecycleOwner, Observer { position ->
            if (!userIsSeeking) {
                progressBar.progress = position.toInt()
                currentTime.text = formatTime(position)
            }
        })

        // Observe current item for metadata
        playerController.currentItem.observe(viewLifecycleOwner, Observer { audioItem ->
            audioItem?.let {
                titleText.text = it.title
                loadAlbumArt(it.albumArtUrl)
            }
        })

        // Start position update job - this ensures smooth progress bar updates
        startProgressUpdateJob()
    }
    
    private fun startProgressUpdateJob() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                try {
                    if (!userIsSeeking && playerController.isPlaying.value == true) {
                        val currentPos = playerController.getCurrentPosition()
                        progressBar.progress = currentPos.toInt()
                        currentTime.text = formatTime(currentPos)
                    }
                    delay(200)
                } catch (e: Exception) {
                    Log.e("PlayerDetailFragment", "Error updating progress", e)
                    delay(1000)
                }
            }
        }
    }

    private fun loadAlbumArt(imageUrl: String?) {
        try {
            // Clear any existing requests
            Glide.with(requireContext())
                .clear(albumArt)
            
            if (imageUrl != null) {
                Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .fitCenter()
                    .into(albumArt)
            } else {
                albumArt.setImageResource(R.drawable.placeholder_image)
            }
        } catch (e: Exception) {
            Log.e("PlayerDetailFragment", "Error loading album art", e)
            albumArt.setImageResource(R.drawable.placeholder_image)
        }
    }
    
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
