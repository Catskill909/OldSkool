package com.oldskool.sessions.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class WordPressPost(
    val id: Int,
    @SerializedName("title")
    val titleData: Title,
    @SerializedName("excerpt")
    val excerptData: Excerpt,
    @SerializedName("x_featured_media_large")
    val featuredMediaUrl: String?,
    val date: String
) : Parcelable {
    val title: String
        get() = titleData.rendered

    val audioUrl: String?
        get() = excerptData.rendered
            .substringAfter("https://")
            .substringBefore("</p>")
            .let { if (it.isNotEmpty()) "https://$it" else null }
}

@Parcelize
data class Title(
    @SerializedName("rendered")
    val rendered: String
) : Parcelable

@Parcelize
data class Excerpt(
    @SerializedName("rendered")
    val rendered: String,
    val protected: Boolean
) : Parcelable
