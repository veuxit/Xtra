package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File


abstract class PlayerViewModel(
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val okHttpClient: OkHttpClient) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    var started = false
    var background = false
    var pipMode = false
    var playerMode = PlayerMode.NORMAL
    val loaded = MutableStateFlow(false)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)

    fun isFollowingChannel(helixHeaders: Map<String, String>, account: Account, gqlHeaders: Map<String, String>, setting: Int, channelId: String?, channelLogin: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    val isFollowing = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        if ((!helixHeaders[C.HEADER_CLIENT_ID].isNullOrBlank() && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && !account.id.isNullOrBlank() && !channelId.isNullOrBlank() && account.id != channelId) ||
                            (!account.login.isNullOrBlank() && !channelLogin.isNullOrBlank() && account.login != channelLogin)) {
                            repository.loadUserFollowing(helixHeaders, channelId, account.id, gqlHeaders, channelLogin)
                        } else false
                    } else {
                        channelId?.let {
                            localFollowsChannel.getFollowByUserId(it)
                        } != null
                    }
                    _isFollowing.value = isFollowing
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(filesDir: String, gqlHeaders: Map<String, String>, setting: Int, userId: String?, userLogin: String?, userName: String?, channelLogo: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.followUser(gqlHeaders, userId)
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
                    if (!userId.isNullOrBlank()) {
                        val downloadedLogo = channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "profile_pics").mkdir()
                            val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body()!!.source())
                                            }
                                        }
                                    }
                                } catch (e: Exception) {

                                }
                            }
                            path
                        }
                        localFollowsChannel.saveFollow(LocalFollowChannel(userId, userLogin, userName, downloadedLogo))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(gqlHeaders: Map<String, String>, setting: Int, userId: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.unfollowUser(gqlHeaders, userId)
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
                    if (userId != null) {
                        localFollowsChannel.getFollowByUserId(userId)?.let { localFollowsChannel.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}