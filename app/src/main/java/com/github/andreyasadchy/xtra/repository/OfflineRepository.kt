package com.github.andreyasadchy.xtra.repository

import android.content.Context
import com.github.andreyasadchy.xtra.db.BookmarksDao
import com.github.andreyasadchy.xtra.db.LocalFollowsChannelDao
import com.github.andreyasadchy.xtra.db.RequestsDao
import com.github.andreyasadchy.xtra.db.VideosDao
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.offline.Request
import com.github.andreyasadchy.xtra.ui.download.DownloadService
import com.github.andreyasadchy.xtra.util.DownloadUtils
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
        private val requestsDao: RequestsDao,
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
            if (it.isNotBlank() && bookmarksDao.getByVideoId(it) == null) {
                File(context.filesDir.path + File.separator + "thumbnails" + File.separator + "${it}.png").delete()
            }
        }
        video.channelId?.let {
            if (it.isNotBlank() && localFollowsChannelDao.getByUserId(it) == null && bookmarksDao.getByUserId(it).isEmpty()) {
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

    suspend fun resumeDownloads(context: Context) = withContext(Dispatchers.IO) {
        requestsDao.getAll().forEach {
            if (DownloadService.activeRequests.add(it.offlineVideoId)) {
                DownloadUtils.download(context, it)
            }
        }
    }

    suspend fun saveRequest(request: Request) = withContext(Dispatchers.IO) {
        requestsDao.insert(request)
    }

    suspend fun deleteRequest(request: Request) = withContext(Dispatchers.IO) {
        requestsDao.delete(request)
    }
}
