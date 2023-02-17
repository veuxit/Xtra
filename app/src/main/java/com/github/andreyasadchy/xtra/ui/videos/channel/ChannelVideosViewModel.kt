package com.github.andreyasadchy.xtra.ui.videos.channel

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
class ChannelVideosViewModel @Inject constructor(
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
        repository.loadChannelVideos(it.channelId, it.channelLogin, it.helixClientId, it.helixToken, it.period, it.broadcastType, it.sort, it.gqlClientId,
            when (it.broadcastType) {
                BroadcastTypeEnum.ARCHIVE -> BroadcastType.ARCHIVE
                BroadcastTypeEnum.HIGHLIGHT -> BroadcastType.HIGHLIGHT
                BroadcastTypeEnum.UPLOAD -> BroadcastType.UPLOAD
                else -> null },
            when (it.sort) { VideoSortEnum.TIME -> VideoSort.TIME else -> VideoSort.VIEWS },
            if (it.broadcastType == BroadcastTypeEnum.ALL) { null }
            else { it.broadcastType.value.uppercase() }, it.sort.value.uppercase(),
            it.apiPref, viewModelScope)
    }
    val sort: VideoSortEnum
        get() = filter.value!!.sort
    val period: VideoPeriodEnum
        get() = filter.value!!.period
    val type: BroadcastTypeEnum
        get() = filter.value!!.broadcastType
    val saveSort: Boolean
        get() = filter.value?.saveSort == true

    fun setChannelId(context: Context, channelId: String? = null, channelLogin: String? = null, helixClientId: String? = null, helixToken: String? = null, gqlClientId: String? = null, apiPref: ArrayList<Pair<Long?, String?>?>) {
        if (filter.value?.channelId != channelId || filter.value?.channelLogin != channelLogin) {
            var sortValues = channelId?.let { runBlocking { sortChannelRepository.getById(it) } }
            if (sortValues?.saveSort != true) {
                sortValues = runBlocking { sortChannelRepository.getById("default") }
            }
            filter.value = Filter(
                channelId = channelId,
                channelLogin = channelLogin,
                helixClientId = helixClientId,
                helixToken = helixToken,
                gqlClientId = gqlClientId,
                apiPref = apiPref,
                saveSort = sortValues?.saveSort,
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

    fun filter(sort: VideoSortEnum, type: BroadcastTypeEnum, text: CharSequence, saveSort: Boolean, saveDefault: Boolean) {
        filter.value = filter.value?.copy(saveSort = saveSort, sort = sort, broadcastType = type)
        _sortText.value = text
        viewModelScope.launch {
            val sortValues = filter.value?.channelId?.let { sortChannelRepository.getById(it) }
            if (saveSort) {
                sortValues?.apply {
                    this.saveSort = true
                    videoSort = sort.value
                    videoType = type.value
                } ?: filter.value?.channelId?.let { SortChannel(
                    id = it,
                    saveSort = true,
                    videoSort = sort.value,
                    videoType = type.value)
                }
            } else {
                sortValues?.apply {
                    this.saveSort = false
                }
            }?.let { sortChannelRepository.save(it) }
            if (saveDefault) {
                (sortValues?.apply {
                    this.saveSort = saveSort
                } ?: filter.value?.channelId?.let { SortChannel(
                    id = it,
                    saveSort = saveSort)
                })?.let { sortChannelRepository.save(it) }
                val sortDefaults = sortChannelRepository.getById("default")
                (sortDefaults?.apply {
                    videoSort = sort.value
                    videoType = type.value
                } ?: SortChannel(
                    id = "default",
                    videoSort = sort.value,
                    videoType = type.value
                )).let { sortChannelRepository.save(it) }
            }
        }
        val appContext = XtraApp.INSTANCE.applicationContext
        if (saveDefault != appContext.prefs().getBoolean(C.SORT_DEFAULT_CHANNEL_VIDEOS, false)) {
            appContext.prefs().edit { putBoolean(C.SORT_DEFAULT_CHANNEL_VIDEOS, saveDefault) }
        }
    }

    private data class Filter(
        val channelId: String?,
        val channelLogin: String?,
        val helixClientId: String?,
        val helixToken: String?,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>,
        val saveSort: Boolean?,
        val sort: VideoSortEnum = VideoSortEnum.TIME,
        val period: VideoPeriodEnum = VideoPeriodEnum.ALL,
        val broadcastType: BroadcastTypeEnum = BroadcastTypeEnum.ALL)
}
