package com.github.andreyasadchy.xtra.ui.games

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.LocalFollowGame
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GamePagerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: ApiRepository,
    private val localFollowsGame: LocalFollowGameRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    val follow = MutableLiveData<Pair<Boolean, String?>>()
    private var updatedLocalGame = false

    fun isFollowingGame(gameId: String?, gameName: String?) {
        if (!follow.isInitialized) {
            viewModelScope.launch {
                try {
                    val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
                    val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
                    val isFollowing = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        gameName?.let {
                            repository.loadGameFollowing(gqlHeaders, gameName)
                        } == true
                    } else {
                        gameId?.let {
                            localFollowsGame.getFollowByGameId(it)
                        } != null
                    }
                    follow.postValue(Pair(isFollowing, null))
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
        }
    }

    fun saveFollowGame(gameId: String?, gameSlug: String?, gameName: String?) {
        viewModelScope.launch {
            val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val account = Account.get(applicationContext)
            val helixClientId = applicationContext.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi")
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.followGame(gqlHeaders, gameId)
                    follow.postValue(Pair(true, errorMessage))
                } else {
                    if (gameId != null) {
                        val downloadedLogo = DownloadUtils.savePng(applicationContext, TwitchApiHelper.getTemplateUrl(repository.loadGameBoxArt(gameId, helixClientId, account.helixToken, gqlHeaders), "game"), "box_art", gameId)
                        localFollowsGame.saveFollow(LocalFollowGame(gameId, gameSlug, gameName, downloadedLogo))
                        follow.postValue(Pair(true, null))
                    }
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    _integrity.postValue(true)
                }
            }
        }
    }

    fun deleteFollowGame(gameId: String?) {
        viewModelScope.launch {
            val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.unfollowGame(gqlHeaders, gameId)
                    follow.postValue(Pair(false, errorMessage))
                } else {
                    if (gameId != null) {
                        localFollowsGame.getFollowByGameId(gameId)?.let { localFollowsGame.deleteFollow(applicationContext, it) }
                        follow.postValue(Pair(false, null))
                    }
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    _integrity.postValue(true)
                }
            }
        }
    }

    fun updateLocalGame(gameId: String?, gameName: String?) {
        if (!updatedLocalGame) {
            updatedLocalGame = true
            viewModelScope.launch {
                if (gameId != null) {
                    val downloadedLogo = try {
                        val get = repository.loadGameBoxArt(
                            gameId = gameId,
                            helixClientId = applicationContext.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                            helixToken = Account.get(applicationContext).helixToken,
                            gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext)
                        )
                        DownloadUtils.savePng(applicationContext, TwitchApiHelper.getTemplateUrl(get, "game"), "box_art", gameId)
                    } catch (e: Exception) {
                        null
                    }
                    localFollowsGame.getFollowByGameId(gameId)?.let { localFollowsGame.updateFollow(it.apply {
                        this.gameName = gameName
                        boxArt = downloadedLogo }) }
                }
            }
        }
    }
}
