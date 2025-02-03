package com.oldskool.sessions.data

import com.google.gson.annotations.SerializedName

data class WordPressPost(
    val id: Int,
    @SerializedName("title")
    val titleData: Title,
    @SerializedName("excerpt")
    val excerptData: Excerpt,
    @SerializedName("x_featured_media_large")
    val featuredMediaUrl: String?,
    val date: String
) {
    val title: String
        get() = titleData.rendered

    val audioUrl: String?
        get() = excerptData.rendered
            .substringAfter("https://")
            .substringBefore("</p>")
            .let { if (it.isNotEmpty()) "https://$it" else null }
}

data class Title(
    @SerializedName("rendered")
    val rendered: String
)

data class Excerpt(
    @SerializedName("rendered")
    val rendered: String,
    val protected: Boolean
)
