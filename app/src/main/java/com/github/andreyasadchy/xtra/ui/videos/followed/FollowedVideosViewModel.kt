package com.github.andreyasadchy.xtra.ui.videos.followed

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.SortChannel
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class FollowedVideosViewModel @Inject constructor(
        context: Application,
        private val repository: ApiRepository,
        playerRepository: PlayerRepository,
        private val bookmarksRepository: BookmarksRepository,
        private val sortChannelRepository: SortChannelRepository) : BaseVideosViewModel(playerRepository, bookmarksRepository, repository) {

    private val _sortText = MutableLiveData<CharSequence>()
    val sortText: LiveData<CharSequence>
        get() = _sortText
    private val filter = MutableLiveData<Filter>()
    override val result: LiveData<Listing<Video>> = Transformations.map(filter) {
        repository.loadFollowedVideos(it.gqlClientId, it.account.gqlToken,
            when (it.broadcastType) {
                BroadcastTypeEnum.ARCHIVE -> BroadcastType.ARCHIVE
                BroadcastTypeEnum.HIGHLIGHT -> BroadcastType.HIGHLIGHT
                BroadcastTypeEnum.UPLOAD -> BroadcastType.UPLOAD
                else -> null },
            when (it.sort) { VideoSortEnum.TIME -> VideoSort.TIME else -> VideoSort.VIEWS }, it.apiPref, viewModelScope)
    }
    val sort: VideoSortEnum
        get() = filter.value!!.sort
    val period: VideoPeriodEnum
        get() = filter.value!!.period
    val type: BroadcastTypeEnum
        get() = filter.value!!.broadcastType

    fun setUser(context: Context, account: Account, gqlClientId: String? = null, apiPref: ArrayList<Pair<Long?, String?>?>) {
        if (filter.value == null) {
            val sortValues = runBlocking { sortChannelRepository.getById("followed_videos") }
            filter.value = Filter(
                account = account,
                gqlClientId = gqlClientId,
                apiPref = apiPref,
                sort = when (sortValues?.videoSort) {
                    VideoSortEnum.VIEWS.value -> VideoSortEnum.VIEWS
                    else -> VideoSortEnum.TIME
                },
                broadcastType = when (sortValues?.videoType) {
                    BroadcastTypeEnum.ARCHIVE.value -> BroadcastTypeEnum.ARCHIVE
                    BroadcastTypeEnum.HIGHLIGHT.value -> BroadcastTypeEnum.HIGHLIGHT
                    BroadcastTypeEnum.UPLOAD.value -> BroadcastTypeEnum.UPLOAD
                    else -> BroadcastTypeEnum.ALL
                }
            )
            _sortText.value = context.getString(R.string.sort_and_period,
                when (sortValues?.videoSort) {
                    VideoSortEnum.VIEWS.value -> context.getString(R.string.view_count)
                    else -> context.getString(R.string.upload_date)
                }, context.getString(R.string.all_time)
            )
        }
    }

    fun filter(sort: VideoSortEnum, period: VideoPeriodEnum, type: BroadcastTypeEnum, text: CharSequence, saveDefault: Boolean) {
        filter.value = filter.value?.copy(sort = sort, period = period, broadcastType = type)
        _sortText.value = text
        if (saveDefault) {
            viewModelScope.launch {
                val sortDefaults = sortChannelRepository.getById("followed_videos")
                (sortDefaults?.apply {
                    videoSort = sort.value
                    videoType = type.value
                } ?: SortChannel(
                    id = "followed_videos",
                    videoSort = sort.value,
                    videoType = type.value
                )).let { sortChannelRepository.save(it) }
            }
        }
        val appContext = XtraApp.INSTANCE.applicationContext
        if (saveDefault != appContext.prefs().getBoolean(C.SORT_DEFAULT_FOLLOWED_VIDEOS, false)) {
            appContext.prefs().edit { putBoolean(C.SORT_DEFAULT_FOLLOWED_VIDEOS, saveDefault) }
        }
    }

    private data class Filter(
        val account: Account,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>,
        val sort: VideoSortEnum = VideoSortEnum.TIME,
        val period: VideoPeriodEnum = VideoPeriodEnum.ALL,
        val broadcastType: BroadcastTypeEnum = BroadcastTypeEnum.ALL)
}
