package com.github.andreyasadchy.xtra.ui.download

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClipDownloadViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private val _qualities = MutableLiveData<Map<String, String>>()
    val qualities: LiveData<Map<String, String>>
        get() = _qualities

    lateinit var clip: Clip

    fun init(gqlHeaders: Map<String, String>, clip: Clip, qualities: Map<String, String>?, skipAccessToken: Int) {
        if (!this::clip.isInitialized) {
            this.clip = clip
            if (qualities.isNullOrEmpty()) {
                viewModelScope.launch {
                    try {
                        val urls = if (skipAccessToken <= 1 && !clip.thumbnailUrl.isNullOrBlank()) {
                            TwitchApiHelper.getClipUrlMapFromPreview(applicationContext, clip.thumbnailUrl)
                        } else {
                            graphQLRepository.loadClipUrls(
                                headers = gqlHeaders,
                                slug = clip.id
                            ) ?: if (skipAccessToken == 2 && !clip.thumbnailUrl.isNullOrBlank()) {
                                TwitchApiHelper.getClipUrlMapFromPreview(applicationContext, clip.thumbnailUrl)
                            } else mapOf()
                        }
                        _qualities.postValue(urls)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                    }
                }
            } else {
                qualities.let { _qualities.value = it }
            }
        }
    }

    fun download(url: String, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        GlobalScope.launch {
            with(clip) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, thumbnail, "thumbnails", it)
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, channelLogo, "profile_pics", it)
                }
                val videoId = offlineRepository.saveVideo(OfflineVideo(
                    sourceUrl = url,
                    sourceStartPosition = vodOffset?.toLong()?.times(1000L),
                    name = title,
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelLogo = downloadedLogo,
                    thumbnail = downloadedThumbnail,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    duration = duration?.toLong()?.times(1000L),
                    uploadDate = uploadDate?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = path,
                    status = OfflineVideo.STATUS_BLOCKED,
                    videoId = videoId,
                    clipId = id,
                    quality = if (!quality.contains("Audio", true)) quality else "audio",
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes
                )).toInt()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "download",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                        .addTag(videoId.toString())
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                )
            }
        }
    }
}