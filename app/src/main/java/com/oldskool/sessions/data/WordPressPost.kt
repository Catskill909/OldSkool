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
    @SerializedName("x_metadata")
    val metadata: Metadata?,
    val date: String
) : Parcelable {
    val title: String
        get() = titleData.rendered

    val audioUrl: String?
        get() {
            val enclosure = metadata?.enclosure
            android.util.Log.d("WordPressPost", "Raw enclosure data: $enclosure")
            val url = enclosure?.split("\n")?.firstOrNull()
            android.util.Log.d("WordPressPost", "Parsed audio URL: $url")
            return url
        }
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

@Parcelize
data class Metadata(
    val enclosure: String?
) : Parcelable
