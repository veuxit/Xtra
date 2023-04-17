package com.github.andreyasadchy.xtra.model

import android.os.Parcelable
import com.github.andreyasadchy.xtra.model.ui.Video
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoDownloadInfo(
    val video: Video,
    val qualities: Map<String, String>,
    val relativeStartTimes: List<Long>,
    val durations: List<Long>,
    val totalDuration: Long,
    val targetDuration: Long,
    val currentPosition: Long) : Parcelable