package com.oldskool.sessions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.oldskool.sessions.R
import java.text.SimpleDateFormat
import java.util.Locale

class PostDetailFragment : Fragment() {
    private val args: PostDetailFragmentArgs by navArgs()
    private lateinit var imageView: ImageView
    private lateinit var titleView: TextView
    private lateinit var dateView: TextView
    private lateinit var playButton: MaterialButton
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_post_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.postImage)
        titleView = view.findViewById(R.id.postTitle)
        dateView = view.findViewById(R.id.postDate)
        playButton = view.findViewById(R.id.playButton)

        setupUI()
    }

    private fun setupUI() {
        titleView.text = args.post.title

        // Parse and format the date
        try {
            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = apiDateFormat.parse(args.post.date)
            dateView.text = date?.let { dateFormat.format(it) } ?: args.post.date
        } catch (e: Exception) {
            dateView.text = args.post.date
        }

        // Load image
        args.post.featuredMediaUrl?.let { url ->
            Glide.with(requireContext())
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(imageView)
        }

        // Setup play button
        args.post.audioUrl?.let { url ->
            playButton.setOnClickListener {
                // TODO: Implement audio playback
            }
            playButton.visibility = View.VISIBLE
        } ?: run {
            playButton.visibility = View.GONE
        }
    }
}
