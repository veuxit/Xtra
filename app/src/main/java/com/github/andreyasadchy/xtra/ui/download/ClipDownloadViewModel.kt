package com.github.andreyasadchy.xtra.ui.download

import android.content.ContentResolver
import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.model.offline.Request
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
import java.io.File
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

    fun download(url: String, path: String, quality: String, useWorkManager: Boolean) {
        GlobalScope.launch {
            val fileUri = if (path.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                path
            } else {
                val fileName = if (!clip.id.isNullOrBlank()) {
                    "${clip.id}$quality"
                } else {
                    System.currentTimeMillis()
                }
                "$path${File.separator}$fileName.mp4"
            }
            val offlineVideo = DownloadUtils.prepareDownload(applicationContext, clip, url, fileUri, clip.duration?.toLong()?.times(1000L), clip.vodOffset?.toLong()?.times(1000L))
            val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
            if (useWorkManager) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    videoId.toString(),
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                        .build()
                )
            } else {
                val request = Request(videoId, url, offlineVideo.url)
                offlineRepository.saveRequest(request)

                DownloadUtils.download(applicationContext, request)
            }
        }
    }
}