package com.github.andreyasadchy.xtra.repository

import android.content.Context
import com.github.andreyasadchy.xtra.db.BookmarksDao
import com.github.andreyasadchy.xtra.db.LocalFollowsChannelDao
import com.github.andreyasadchy.xtra.db.VideosDao
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRepository @Inject constructor(
        private val videosDao: VideosDao,
        private val localFollowsChannelDao: LocalFollowsChannelDao,
        private val bookmarksDao: BookmarksDao) {

    fun loadAllVideos() = videosDao.getAll()

    suspend fun getVideoById(id: Int) = withContext(Dispatchers.IO) {
        videosDao.getById(id)
    }

    suspend fun getVideoByUrl(url: String) = withContext(Dispatchers.IO) {
        videosDao.getByUrl(url)
    }

    suspend fun getVideosByUserId(id: String) = withContext(Dispatchers.IO) {
        videosDao.getByUserId(id)
    }

    suspend fun saveVideo(video: OfflineVideo) = withContext(Dispatchers.IO) {
        videosDao.insert(video)
    }

    suspend fun deleteVideo(context: Context, video: OfflineVideo) = withContext(Dispatchers.IO) {
        video.videoId?.let {
            if (it.isNotBlank() && videosDao.getByVideoId(it).isEmpty() && bookmarksDao.getByVideoId(it) == null) {
                File(context.filesDir.path + File.separator + "thumbnails" + File.separator + "${it}.png").delete()
            }
        }
        video.channelId?.let {
            if (it.isNotBlank() && getVideosByUserId(it).isEmpty() && bookmarksDao.getByUserId(it).isEmpty() && localFollowsChannelDao.getByUserId(it) == null) {
                File(context.filesDir.path + File.separator + "profile_pics" + File.separator + "${it}.png").delete()
            }
        }
        videosDao.delete(video)
    }

    suspend fun updateVideo(video: OfflineVideo) = withContext(Dispatchers.IO) {
        videosDao.update(video)
    }

    fun updateVideoPosition(id: Int, position: Long) {
        GlobalScope.launch {
            videosDao.updatePosition(id, position)
        }
    }

    suspend fun deletePositions() = withContext(Dispatchers.IO) {
        videosDao.deletePositions()
    }
}
