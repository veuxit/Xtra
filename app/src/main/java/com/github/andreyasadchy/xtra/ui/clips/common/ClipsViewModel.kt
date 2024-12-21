package com.github.andreyasadchy.xtra.ui.clips.common

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.SortChannel
import com.github.andreyasadchy.xtra.model.ui.SortGame
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.SortChannelRepository
import com.github.andreyasadchy.xtra.repository.SortGameRepository
import com.github.andreyasadchy.xtra.repository.datasource.ChannelClipsDataSource
import com.github.andreyasadchy.xtra.repository.datasource.GameClipsDataSource
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import com.github.andreyasadchy.xtra.type.Language
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.videos.VideosSortDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class ClipsViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val sortChannelRepository: SortChannelRepository,
    private val sortGameRepository: SortGameRepository,
    savedStateHandle: SavedStateHandle) : ViewModel() {

    private val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val period: String
        get() = filter.value?.period ?: VideosSortDialog.PERIOD_WEEK
    val languageIndex: Int
        get() = filter.value?.languageIndex ?: 0
    val saveSort: Boolean
        get() = filter.value?.saveSort == true

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            PagingConfig(pageSize = 20, prefetchDistance = 3, initialLoadSize = 20)
        ) {
            val started = when (period) {
                VideosSortDialog.PERIOD_ALL -> null
                else -> TwitchApiHelper.getClipTime(
                    when (period) {
                        VideosSortDialog.PERIOD_DAY -> 1
                        VideosSortDialog.PERIOD_WEEK -> 7
                        VideosSortDialog.PERIOD_MONTH -> 30
                        else -> 7
                    }
                )
            }
            val ended = when (period) {
                VideosSortDialog.PERIOD_ALL -> null
                else -> TwitchApiHelper.getClipTime(0)
            }
            val gqlQueryPeriod = when (period) {
                VideosSortDialog.PERIOD_DAY -> ClipsPeriod.LAST_DAY
                VideosSortDialog.PERIOD_WEEK -> ClipsPeriod.LAST_WEEK
                VideosSortDialog.PERIOD_MONTH -> ClipsPeriod.LAST_MONTH
                VideosSortDialog.PERIOD_ALL -> ClipsPeriod.ALL_TIME
                else -> ClipsPeriod.LAST_WEEK
            }
            val gqlPeriod = when (period) {
                VideosSortDialog.PERIOD_DAY -> "LAST_DAY"
                VideosSortDialog.PERIOD_WEEK -> "LAST_WEEK"
                VideosSortDialog.PERIOD_MONTH -> "LAST_MONTH"
                VideosSortDialog.PERIOD_ALL -> "ALL_TIME"
                else -> "LAST_WEEK"
            }
            if (args.channelId != null || args.channelLogin != null) {
                ChannelClipsDataSource(
                    channelId = args.channelId,
                    channelLogin = args.channelLogin,
                    helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                    startedAt = started,
                    endedAt = ended,
                    helixApi = helix,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                    gqlQueryPeriod = gqlQueryPeriod,
                    gqlPeriod = gqlPeriod,
                    gqlApi = graphQLRepository,
                    checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                    apiPref = applicationContext.prefs().getString(C.API_PREFS_GAME_CLIPS, null)?.split(',') ?: TwitchApiHelper.gameClipsApiDefaults
                )
            } else {
                val langList = mutableListOf<Language>()
                if (languageIndex != 0) {
                    val langValues = applicationContext.resources.getStringArray(R.array.gqlUserLanguageValues).toList()
                    val item = Language.entries.find { lang -> lang.rawValue == langValues.elementAt(languageIndex) }
                    if (item != null) {
                        langList.add(item)
                    }
                }
                GameClipsDataSource(
                    gameId = args.gameId,
                    gameSlug = args.gameSlug,
                    gameName = args.gameName,
                    helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                    startedAt = started,
                    endedAt = ended,
                    helixApi = helix,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                    gqlQueryLanguages = langList.ifEmpty { null },
                    gqlQueryPeriod = gqlQueryPeriod,
                    gqlPeriod = gqlPeriod,
                    gqlApi = graphQLRepository,
                    checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                    apiPref = applicationContext.prefs().getString(C.API_PREFS_GAME_CLIPS, null)?.split(',') ?: TwitchApiHelper.gameClipsApiDefaults
                )
            }
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getSortGame(id: String): SortGame? {
        return sortGameRepository.getById(id)
    }

    suspend fun saveSortGame(item: SortGame) {
        sortGameRepository.save(item)
    }

    suspend fun getSortChannel(id: String): SortChannel? {
        return sortChannelRepository.getById(id)
    }

    suspend fun saveSortChannel(item: SortChannel) {
        sortChannelRepository.save(item)
    }

    fun setFilter(period: String?, languageIndex: Int?, saveSort: Boolean?) {
        filter.value = Filter(period, languageIndex, saveSort)
    }

    class Filter(
        val period: String?,
        val languageIndex: Int?,
        val saveSort: Boolean?,
    )
}
