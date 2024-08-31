package com.github.andreyasadchy.xtra.ui.videos.channel

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.offline.SortChannel
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.SortChannelRepository
import com.github.andreyasadchy.xtra.repository.datasource.ChannelVideosDataSource
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class ChannelVideosViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    repository: ApiRepository,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val sortChannelRepository: SortChannelRepository) : BaseVideosViewModel(playerRepository, bookmarksRepository, repository, okHttpClient) {

    private val _sortText = MutableStateFlow<CharSequence?>(null)
    val sortText: StateFlow<CharSequence?> = _sortText
    private val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val filter = MutableStateFlow(setChannelId())

    val sort: VideoSortEnum
        get() = filter.value.sort
    val period: VideoPeriodEnum
        get() = filter.value.period
    val type: BroadcastTypeEnum
        get() = filter.value.broadcastType
    val saveSort: Boolean
        get() = filter.value.saveSort == true

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            with(filter) {
                ChannelVideosDataSource(
                    channelId = args.channelId,
                    channelLogin = args.channelLogin,
                    helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                    helixPeriod = period,
                    helixBroadcastTypes = broadcastType,
                    helixSort = sort,
                    helixApi = helix,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                    gqlQueryType = when (broadcastType) {
                        BroadcastTypeEnum.ARCHIVE -> BroadcastType.ARCHIVE
                        BroadcastTypeEnum.HIGHLIGHT -> BroadcastType.HIGHLIGHT
                        BroadcastTypeEnum.UPLOAD -> BroadcastType.UPLOAD
                        else -> null },
                    gqlQuerySort = when (sort) { VideoSortEnum.TIME -> VideoSort.TIME else -> VideoSort.VIEWS },
                    gqlType = if (broadcastType == BroadcastTypeEnum.ALL) { null }
                    else { broadcastType.value.uppercase() },
                    gqlSort = sort.value.uppercase(),
                    gqlApi = graphQLRepository,
                    checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                    apiPref = TwitchApiHelper.listFromPrefs(applicationContext.prefs().getString(C.API_PREF_CHANNEL_VIDEOS, ""), TwitchApiHelper.channelVideosApiDefaults))
            }
        }.flow
    }.cachedIn(viewModelScope)

    private fun setChannelId(): Filter {
        var sortValues = args.channelId?.let { runBlocking { sortChannelRepository.getById(it) } }
        if (sortValues?.saveSort != true) {
            sortValues = runBlocking { sortChannelRepository.getById("default") }
        }
        _sortText.value = ContextCompat.getString(applicationContext, R.string.sort_and_period).format(
            when (sortValues?.videoSort) {
                VideoSortEnum.VIEWS.value -> ContextCompat.getString(applicationContext, R.string.view_count)
                else -> ContextCompat.getString(applicationContext, R.string.upload_date)
            }, ContextCompat.getString(applicationContext, R.string.all_time)
        )
        return Filter(
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
    }

    fun filter(sort: VideoSortEnum, type: BroadcastTypeEnum, text: CharSequence, saveSort: Boolean, saveDefault: Boolean) {
        filter.value = filter.value.copy(saveSort = saveSort, sort = sort, broadcastType = type)
        _sortText.value = text
        viewModelScope.launch {
            val sortValues = args.channelId?.let { sortChannelRepository.getById(it) }
            if (saveSort) {
                sortValues?.apply {
                    this.saveSort = true
                    videoSort = sort.value
                    videoType = type.value
                } ?: args.channelId?.let { SortChannel(
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
                } ?: args.channelId?.let { SortChannel(
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
        if (saveDefault != applicationContext.prefs().getBoolean(C.SORT_DEFAULT_CHANNEL_VIDEOS, false)) {
            applicationContext.prefs().edit { putBoolean(C.SORT_DEFAULT_CHANNEL_VIDEOS, saveDefault) }
        }
    }

    private data class Filter(
        val saveSort: Boolean?,
        val sort: VideoSortEnum = VideoSortEnum.TIME,
        val period: VideoPeriodEnum = VideoPeriodEnum.ALL,
        val broadcastType: BroadcastTypeEnum = BroadcastTypeEnum.ALL)
}
