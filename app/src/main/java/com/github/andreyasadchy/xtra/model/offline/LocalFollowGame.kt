package com.github.andreyasadchy.xtra.model.offline

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "local_follows_games")
data class LocalFollowGame(
    val gameId: String? = null,
    var gameName: String? = null,
    var boxArt: String? = null) : Parcelable {

    @IgnoredOnParcel
    @PrimaryKey(autoGenerate = true)
    var id = 0
}
