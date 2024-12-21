package com.github.andreyasadchy.xtra.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.LocalFollowGame
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GamePagerViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val localFollowsGame: LocalFollowGameRepository,
    private val okHttpClient: OkHttpClient) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private var updatedLocalGame = false

    fun isFollowingGame(gqlHeaders: Map<String, String>, setting: Int, gameId: String?, gameName: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    val isFollowing = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        gameName?.let {
                            repository.loadGameFollowing(gqlHeaders, gameName)
                        } == true
                    } else {
                        gameId?.let {
                            localFollowsGame.getFollowByGameId(it)
                        } != null
                    }
                    _isFollowing.value = isFollowing
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowGame(filesDir: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, setting: Int, gameId: String?, gameSlug: String?, gameName: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.followGame(gqlHeaders, gameId)
                    if (!errorMessage.isNullOrBlank()) {
                        if (errorMessage == "failed integrity check" && integrity.value == null) {
                            integrity.value = "follow"
                        } else {
                            follow.value = Pair(true, errorMessage)
                        }
                    } else {
                        _isFollowing.value = true
                        follow.value = Pair(true, errorMessage)
                    }
                } else {
                    if (!gameId.isNullOrBlank()) {
                        File(filesDir, "box_art").mkdir()
                        val path = filesDir + File.separator + "box_art" + File.separator + gameId
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                repository.loadGameBoxArt(gameId, helixHeaders, gqlHeaders).takeIf { !it.isNullOrBlank() }?.let { TwitchApiHelper.getTemplateUrl(it, "game") }?.let {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        localFollowsGame.saveFollow(LocalFollowGame(gameId, gameSlug, gameName, path))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowGame(gqlHeaders: Map<String, String>, setting: Int, gameId: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.unfollowGame(gqlHeaders, gameId)
                    if (!errorMessage.isNullOrBlank()) {
                        if (errorMessage == "failed integrity check" && integrity.value == null) {
                            integrity.value = "unfollow"
                        } else {
                            follow.value = Pair(false, errorMessage)
                        }
                    } else {
                        _isFollowing.value = false
                        follow.value = Pair(false, errorMessage)
                    }
                } else {
                    if (gameId != null) {
                        localFollowsGame.getFollowByGameId(gameId)?.let { localFollowsGame.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateLocalGame(filesDir: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, gameId: String?, gameName: String?) {
        if (!updatedLocalGame) {
            updatedLocalGame = true
            if (!gameId.isNullOrBlank()) {
                viewModelScope.launch {
                    File(filesDir, "box_art").mkdir()
                    val path = filesDir + File.separator + "box_art" + File.separator + gameId
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.loadGameBoxArt(gameId, helixHeaders, gqlHeaders).takeIf { !it.isNullOrBlank() }?.let { TwitchApiHelper.getTemplateUrl(it, "game") }?.let {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {

                        }
                    }
                    localFollowsGame.getFollowByGameId(gameId)?.let {
                        localFollowsGame.updateFollow(it.apply {
                            this.gameName = gameName
                            boxArt = path
                        })
                    }
                }
            }
        }
    }
}
