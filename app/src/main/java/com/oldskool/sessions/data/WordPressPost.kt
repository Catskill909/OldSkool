package com.oldskool.sessions.data

data class WordPressPost(
    val id: Int,
    val title: String,
    val content: String,
    val excerpt: String,
    val date: String,
    val featuredMediaUrl: String?
)
