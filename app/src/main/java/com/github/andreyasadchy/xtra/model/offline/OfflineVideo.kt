package com.github.andreyasadchy.xtra.model.offline

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "videos")
data class OfflineVideo(
    var url: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "source_start_position")
    var sourceStartPosition: Long? = null,
    val name: String? = null,
    @ColumnInfo(name = "channel_id")
    val channelId: String? = null,
    @ColumnInfo(name = "channel_login")
    var channelLogin: String? = null,
    @ColumnInfo(name = "channel_name")
    var channelName: String? = null,
    @ColumnInfo(name = "channel_logo")
    var channelLogo: String? = null,
    var thumbnail: String? = null,
    val gameId: String? = null,
    val gameSlug: String? = null,
    val gameName: String? = null,
    var duration: Long? = null,
    @ColumnInfo(name = "upload_date")
    val uploadDate: Long? = null,
    @ColumnInfo(name = "download_date")
    val downloadDate: Long? = null,
    @ColumnInfo(name = "last_watch_position")
    var lastWatchPosition: Long? = null,
    var progress: Int,
    @ColumnInfo(name = "max_progress")
    var maxProgress: Int,
    var downloadPath: String? = null,
    val fromTime: Long? = null,
    val toTime: Long? = null,
    var status: Int = STATUS_PENDING,
    val type: String? = null,
    val videoId: String? = null,
    val quality: String? = null) : Parcelable {

    @IgnoredOnParcel
    @PrimaryKey(autoGenerate = true)
    var id = 0

    @IgnoredOnParcel
    @ColumnInfo(name = "is_vod")
    var vod = url.endsWith(".m3u8")

    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_DOWNLOADED = 2
        const val STATUS_MOVING = 3
        const val STATUS_DELETING = 4
        const val STATUS_CONVERTING = 5
    }
}
