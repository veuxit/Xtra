package com.github.andreyasadchy.xtra.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.offline.LocalFollowGame
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.offline.SortChannel
import com.github.andreyasadchy.xtra.model.offline.SortGame
import com.github.andreyasadchy.xtra.model.offline.VodBookmarkIgnoredUser

@Database(entities = [OfflineVideo::class, RecentEmote::class, VideoPosition::class, LocalFollowChannel::class, LocalFollowGame::class, Bookmark::class, VodBookmarkIgnoredUser::class, SortChannel::class, SortGame::class, ShownNotification::class], version = 27)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videos(): VideosDao
    abstract fun recentEmotes(): RecentEmotesDao
    abstract fun videoPositions(): VideoPositionsDao
    abstract fun localFollowsChannel(): LocalFollowsChannelDao
    abstract fun localFollowsGame(): LocalFollowsGameDao
    abstract fun bookmarks(): BookmarksDao
    abstract fun vodBookmarkIgnoredUsers(): VodBookmarkIgnoredUsersDao
    abstract fun sortChannelDao(): SortChannelDao
    abstract fun sortGameDao(): SortGameDao
    abstract fun shownNotificationsDao(): ShownNotificationsDao
}