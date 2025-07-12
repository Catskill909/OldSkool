package com.oldskool.sessions.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing an audio item that can be played by the media player.
 */
@Parcelize
data class AudioItem(
    val id: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val audioUrl: String,  // Changed from Uri to String for consistency
    val albumArtUrl: String? = null,
    val duration: Long = 0,
    val sourceFragmentId: Int = 0
) : Parcelable
