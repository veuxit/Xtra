package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.BookmarksDao
import com.github.andreyasadchy.xtra.db.LocalFollowsChannelDao
import com.github.andreyasadchy.xtra.db.VideosDao
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import kotlinx.coroutines.Dispatchers
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

    suspend fun deleteFollow(item: LocalFollowChannel) = withContext(Dispatchers.IO) {
        if (!item.userId.isNullOrBlank() && bookmarksDao.getByUserId(item.userId).isEmpty() && videosDao.getByUserId(item.userId).isEmpty()) {
            item.channelLogo?.let {
                if (it.isNotBlank()) {
                    File(it).delete()
                }
            }
        }
        localFollowsChannelDao.delete(item)
    }

    suspend fun updateFollow(item: LocalFollowChannel) = withContext(Dispatchers.IO) {
        localFollowsChannelDao.update(item)
    }
}
