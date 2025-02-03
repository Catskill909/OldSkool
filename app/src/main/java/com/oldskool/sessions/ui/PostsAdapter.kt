package com.oldskool.sessions.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.oldskool.sessions.R
import com.oldskool.sessions.data.WordPressPost
import java.text.SimpleDateFormat
import java.util.Locale

class PostsAdapter(private val onPostClick: (WordPressPost) -> Unit) : 
    ListAdapter<WordPressPost, PostsAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view, onPostClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
        itemView: View,
        private val onPostClick: (WordPressPost) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.postImage)
        private val titleView: TextView = itemView.findViewById(R.id.postTitle)
        private val dateView: TextView = itemView.findViewById(R.id.postDate)
        private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        fun bind(post: WordPressPost) {
            titleView.text = post.title
            
            // Parse and format the date
            try {
                val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = apiDateFormat.parse(post.date)
                dateView.text = date?.let { dateFormat.format(it) } ?: post.date
            } catch (e: Exception) {
                dateView.text = post.date
            }

            // Load image with Glide
            post.featuredMediaUrl?.let { url ->
                Glide.with(imageView)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_image)
                    .into(imageView)
            }

            itemView.setOnClickListener { onPostClick(post) }
        }
    }

    private class PostDiffCallback : DiffUtil.ItemCallback<WordPressPost>() {
        override fun areItemsTheSame(oldItem: WordPressPost, newItem: WordPressPost): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WordPressPost, newItem: WordPressPost): Boolean {
            return oldItem == newItem
        }
    }
}
