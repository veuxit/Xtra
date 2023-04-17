package com.github.andreyasadchy.xtra.repository

import android.content.Context
import com.github.andreyasadchy.xtra.db.BookmarksDao
import com.github.andreyasadchy.xtra.db.LocalFollowsChannelDao
import com.github.andreyasadchy.xtra.db.VideosDao
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFollowChannelRepository @Inject constructor(
    private val localFollowsChannelDao: LocalFollowsChannelDao,
    private val videosDao: VideosDao,
    private val bookmarksDao: BookmarksDao) {

    suspend fun loadFollows() = withContext(Dispatchers.IO) {
        localFollowsChannelDao.getAll()
    }

    suspend fun getFollowByUserId(id: String) = withContext(Dispatchers.IO) {
        localFollowsChannelDao.getByUserId(id)
    }

    suspend fun saveFollow(item: LocalFollowChannel) = withContext(Dispatchers.IO) {
        localFollowsChannelDao.insert(item)
    }

    fun deleteFollow(context: Context, item: LocalFollowChannel) {
        GlobalScope.launch {
            if (!item.userId.isNullOrBlank() && bookmarksDao.getByUserId(item.userId).isEmpty() && videosDao.getByUserId(item.userId.toInt()).isEmpty()) {
                File(context.filesDir.toString() + File.separator + "profile_pics" + File.separator + "${item.userId}.png").delete()
            }
            localFollowsChannelDao.delete(item)
        }
    }

    fun updateFollow(item: LocalFollowChannel) {
        GlobalScope.launch { localFollowsChannelDao.update(item) }
    }
}
