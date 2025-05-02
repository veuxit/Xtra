package com.github.andreyasadchy.xtra.ui.videos.game

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.SortGame
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.SortGameRepository
import com.github.andreyasadchy.xtra.repository.datasource.GameVideosDataSource
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.ui.videos.VideosSortDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@HiltViewModel
class GameVideosViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val sortGameRepository: SortGameRepository,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    cronetEngine: CronetEngine?,
    cronetExecutor: ExecutorService,
    okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : BaseVideosViewModel(playerRepository, bookmarksRepository, graphQLRepository, helixRepository, cronetEngine, cronetExecutor, okHttpClient) {

    private val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = filter.value?.sort ?: VideosSortDialog.SORT_VIEWS
    val period: String
        get() = filter.value?.period ?: VideosSortDialog.PERIOD_WEEK
    val type: String
        get() = filter.value?.type ?: VideosSortDialog.VIDEO_TYPE_ALL
    val languageIndex: Int
        get() = filter.value?.languageIndex ?: 0
    val saveSort: Boolean
        get() = filter.value?.saveSort == true

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            val language = if (languageIndex != 0) {
                applicationContext.resources.getStringArray(R.array.gqlUserLanguageValues).toList().elementAt(languageIndex)
            } else null
            GameVideosDataSource(
                gameId = args.gameId,
                gameSlug = args.gameSlug,
                gameName = args.gameName,
                gqlQueryLanguages = language?.let { listOf(it) },
                gqlQueryType = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> null
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> BroadcastType.ARCHIVE
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> BroadcastType.HIGHLIGHT
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> BroadcastType.UPLOAD
                    else -> null
                },
                gqlQuerySort = when (sort) {
                    VideosSortDialog.SORT_TIME -> VideoSort.TIME
                    VideosSortDialog.SORT_VIEWS -> VideoSort.VIEWS
                    else -> VideoSort.VIEWS
                },
                gqlType = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> null
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> "ARCHIVE"
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> "HIGHLIGHT"
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> "UPLOAD"
                    else -> null
                },
                gqlSort = when (sort) {
                    VideosSortDialog.SORT_TIME -> "TIME"
                    VideosSortDialog.SORT_VIEWS -> "VIEWS"
                    else -> "VIEWS"
                },
                helixPeriod = when (period) {
                    VideosSortDialog.PERIOD_DAY -> "day"
                    VideosSortDialog.PERIOD_WEEK -> "week"
                    VideosSortDialog.PERIOD_MONTH -> "month"
                    VideosSortDialog.PERIOD_ALL -> "all"
                    else -> "week"
                },
                helixBroadcastTypes = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> "all"
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> "archive"
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> "highlight"
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> "upload"
                    else -> "all"
                },
                helixLanguage = language?.lowercase(),
                helixSort = when (sort) {
                    VideosSortDialog.SORT_TIME -> "time"
                    VideosSortDialog.SORT_VIEWS -> "views"
                    else -> "views"
                },
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixRepository = helixRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                apiPref = applicationContext.prefs().getString(C.API_PREFS_GAME_VIDEOS, null)?.split(',') ?: TwitchApiHelper.gameVideosApiDefaults,
                useCronet = applicationContext.prefs().getBoolean(C.USE_CRONET, false),
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getSortGame(id: String): SortGame? {
        return sortGameRepository.getById(id)
    }

    suspend fun saveSortGame(item: SortGame) {
        sortGameRepository.save(item)
    }

    fun setFilter(sort: String?, period: String?, type: String?, languageIndex: Int?, saveSort: Boolean?) {
        filter.value = Filter(sort, period, type, languageIndex, saveSort)
    }

    class Filter(
        val sort: String?,
        val period: String?,
        val type: String?,
        val languageIndex: Int?,
        val saveSort: Boolean?,
    )
}
