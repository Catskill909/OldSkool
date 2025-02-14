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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.oldskool.sessions.R
import com.oldskool.sessions.media.OSSMediaManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerDetailFragment : Fragment() {

    private lateinit var mediaManager: OSSMediaManager
    private val args: PlayerDetailFragmentArgs by navArgs()

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

            // Initialize MediaManager
            mediaManager = OSSMediaManager.getInstance(requireContext())

            setupClickListeners()
            setupProgressBar()
            setupObservers()

            // Get arguments safely
            val safeArgs = try {
                navArgs<PlayerDetailFragmentArgs>().value
            } catch (e: Exception) {
                Log.e("PlayerDetailFragment", "Failed to get arguments: ${e.message}")
                // Don't navigate up, just use current state
                null
            }

            // Load initial state from arguments
            safeArgs?.let { args ->
                titleText.text = args.title
                loadAlbumArt(args.imageUrl)

                args.audioUrl?.let { url ->
                    Log.d("PlayerDetailFragment", "Preparing audio with URL: $url")
                    mediaManager.prepareAudio(
                        url = url,
                        title = args.title,
                        artworkUrl = args.imageUrl,
                        sourceFragmentId = R.id.navigation_player_detail
                    )
                }
            }

            // Observe current playback state
            lifecycleScope.launch {
                mediaManager.currentTitle.collectLatest { title ->
                    if (title != null) {
                        titleText.text = title
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerDetailFragment", "Error in onViewCreated: ${e.message}")
            findNavController().navigateUp()
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop playback when leaving the detail view
        if (mediaManager.isPlaying.value) {
            mediaManager.togglePlayPause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressUpdateJob?.cancel()
        // Reset playback state but preserve metadata
        mediaManager.cleanupPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only destroy media manager when the app is finishing
        if (requireActivity().isFinishing) {
            mediaManager.destroy()
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        playPauseButton.setOnClickListener {
            try {
                mediaManager.togglePlayPause()
            } catch (e: Exception) {
                Log.e("PlayerDetailFragment", "Error toggling play/pause", e)
                // Disable the button if we hit an error
                playPauseButton.isEnabled = false
            }
        }
    }

    private fun setupProgressBar() {
        try {
            progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        if (fromUser) {
                            currentTime.text = mediaManager.formatTime(progress.toLong())
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerDetailFragment", "Error updating progress text", e)
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
                                mediaManager.seekTo(progress.toLong())
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
        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.isPlaying.collectLatest { isPlaying ->
                playPauseButton.setImageResource(
                    if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.duration.collectLatest { duration ->
                if (duration > 0) {
                    progressBar.max = duration.toInt()
                    timeContainer.isVisible = true
                    totalTime.text = mediaManager.formatTime(duration)
                }
            }
        }

        progressUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                mediaManager.updateProgress()
                delay(1000) // Update every second
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.currentPosition.collectLatest { position ->
                if (!userIsSeeking) {
                    progressBar.progress = position.toInt()
                    currentTime.text = mediaManager.formatTime(position)
                }
            }
        }

        // Observe artwork changes
        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.currentArtwork.collectLatest { artworkUrl ->
                if (artworkUrl != null) {
                    loadAlbumArt(artworkUrl)
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
                    .into(albumArt)
            } else {
                albumArt.setImageResource(R.drawable.placeholder_image)
            }
        } catch (e: Exception) {
            Log.e("PlayerDetailFragment", "Error loading album art", e)
            albumArt.setImageResource(R.drawable.placeholder_image)
        }
    }
}
