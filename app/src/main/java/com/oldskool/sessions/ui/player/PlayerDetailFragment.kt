package com.oldskool.sessions.ui.player

import android.os.Bundle
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
        progressBar.isVisible = false
        timeContainer.isVisible = false

        // Start playback if we have a URL
        args.excerpt?.let { excerpt ->
            mediaManager.playAudio(
                url = excerpt,
                title = args.title,
                artworkUrl = args.imageUrl
            )
        }
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
            mediaManager.isPlaying.collectLatest { isPlaying: Boolean ->
                playPauseButton.setImageResource(
                    if (isPlaying) R.drawable.ic_pause_circle
                    else R.drawable.ic_play_circle
                )
                progressBar.isVisible = isPlaying
                timeContainer.isVisible = isPlaying
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.duration.collectLatest { duration: Long ->
                progressBar.max = duration.toInt()
                totalTime.text = mediaManager.formatTime(duration)
            }
        }

        progressUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            mediaManager.currentPosition.collectLatest { position: Long ->
                if (!userIsSeeking) {
                    progressBar.progress = position.toInt()
                    currentTime.text = mediaManager.formatTime(position)
                }
            }
        }
    }

    private fun loadAlbumArt(imageUrl: String?) {
        imageUrl?.let {
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.placeholder_artwork)
                .error(R.drawable.placeholder_artwork)
                .into(albumArt)
        } ?: run {
            albumArt.setImageResource(R.drawable.placeholder_artwork)
        }
    }

    override fun onStart() {
        super.onStart()
        mediaManager.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaManager.disconnect()
        progressUpdateJob?.cancel()
    }
}
