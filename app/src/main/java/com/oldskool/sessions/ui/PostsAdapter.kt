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

        fun bind(post: WordPressPost) {
            titleView.text = post.title

            // Load image with Glide
            try {
                Glide.with(imageView.context)
                    .clear(imageView)
                
                post.featuredMediaUrl?.let { url ->
                    Glide.with(imageView.context)
                        .load(url)
                        .centerCrop()
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .into(imageView)
                } ?: run {
                    imageView.setImageResource(R.drawable.placeholder_image)
                }
            } catch (e: Exception) {
                android.util.Log.e("PostsAdapter", "Error loading image", e)
                imageView.setImageResource(R.drawable.placeholder_image)
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
