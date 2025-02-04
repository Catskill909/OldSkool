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
        mediaManager = OSSMediaManager(requireContext())

        setupClickListeners()
        setupProgressBar()
        setupObservers()

        // Set initial UI state
        titleText.text = args.title
        loadAlbumArt(args.imageUrl)

        // Start playback if we have a URL
        Log.d("PlayerDetailFragment", "Audio URL: ${args.audioUrl}")
        Log.d("PlayerDetailFragment", "Title: ${args.title}")
        Log.d("PlayerDetailFragment", "Image URL: ${args.imageUrl}")

        args.audioUrl?.let { url ->
            Log.d("PlayerDetailFragment", "Starting playback with URL: $url")
            mediaManager.playAudio(
                url = url,
                title = args.title,
                artworkUrl = args.imageUrl
            )
        } ?: Log.e("PlayerDetailFragment", "No audio URL provided")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaManager.release()
        progressUpdateJob?.cancel()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        playPauseButton.setOnClickListener {
            mediaManager.togglePlayPause()
        }
    }

    private fun setupProgressBar() {
        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTime.text = mediaManager.formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userIsSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userIsSeeking = false
                seekBar?.progress?.let { progress ->
                    mediaManager.seekTo(progress.toLong())
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.isPlaying.collectLatest { isPlaying ->
                playPauseButton.setImageResource(
                    if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
                )
                // Update visibility of progress bar based on playback state
                progressBar.visibility = if (isPlaying) View.VISIBLE else View.INVISIBLE
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
    }

    private fun loadAlbumArt(imageUrl: String?) {
        if (imageUrl != null) {
            Glide.with(requireContext())
                .load(imageUrl)
                .into(albumArt)
        }
    }
}
