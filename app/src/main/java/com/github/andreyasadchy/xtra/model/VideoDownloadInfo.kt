package com.github.andreyasadchy.xtra.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoDownloadInfo(
    val qualities: Map<String, String>? = null,
    val totalDuration: Long,
    val currentPosition: Long) : Parcelable