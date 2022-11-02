package com.github.andreyasadchy.xtra.model.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_emotes")
class RecentEmote(
        @PrimaryKey
        override val name: String,
        override val url1x: String?,
        override val url2x: String?,
        override val url3x: String?,
        override val url4x: String?,
        @ColumnInfo(name = "used_at")
        val usedAt: Long) : Emote() {

    companion object {
        const val MAX_SIZE = 50
    }
}
