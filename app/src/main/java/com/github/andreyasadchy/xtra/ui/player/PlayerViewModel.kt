package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule


abstract class PlayerViewModel(
    protected val applicationContext: Context,
    val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository) : ViewModel() {

    var started = false
    var background = false
    var pipMode = false
    var playerMode = PlayerMode.NORMAL
    val loaded = MutableLiveData<Boolean>()
    val follow = MutableLiveData<Pair<Boolean, String?>>()

    protected val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private var timer: Timer? = null
    private val _sleepTimer = MutableLiveData<Boolean>()
    val sleepTimer: LiveData<Boolean>
        get() = _sleepTimer
    private var timerEndTime = 0L
    val timerTimeLeft
        get() = timerEndTime - System.currentTimeMillis()

    fun setTimer(duration: Long) {
        timer?.let {
            it.cancel()
            timerEndTime = 0L
            timer = null
        }
        if (duration > 0L) {
            timer = Timer().apply {
                timerEndTime = System.currentTimeMillis() + duration
                schedule(duration) {
                    _sleepTimer.postValue(true)
                }
            }
        }
    }

    override fun onCleared() {
        timer?.cancel()
    }

    fun isFollowingChannel(channelId: String?, channelLogin: String?) {
        if (!follow.isInitialized) {
            viewModelScope.launch {
                try {
                    val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
                    val account = Account.get(applicationContext)
                    val helixClientId = applicationContext.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi")
                    val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
                    val isFollowing = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        if ((!helixClientId.isNullOrBlank() && !account.helixToken.isNullOrBlank() && !account.id.isNullOrBlank() && !channelId.isNullOrBlank() && account.id != channelId) ||
                            (!account.login.isNullOrBlank() && !channelLogin.isNullOrBlank() && account.login != channelLogin)) {
                            repository.loadUserFollowing(helixClientId, account.helixToken, channelId, account.id, gqlHeaders, channelLogin)
                        } else false
                    } else {
                        channelId?.let {
                            localFollowsChannel.getFollowByUserId(it)
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

    fun saveFollowChannel(userId: String?, userLogin: String?, userName: String?, channelLogo: String?) {
        viewModelScope.launch {
            val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.followUser(gqlHeaders, userId)
                    follow.postValue(Pair(true, errorMessage))
                } else {
                    if (userId != null) {
                        val downloadedLogo = DownloadUtils.savePng(applicationContext, channelLogo, "profile_pics", userId)
                        localFollowsChannel.saveFollow(LocalFollowChannel(userId, userLogin, userName, downloadedLogo))
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

    fun deleteFollowChannel(userId: String?) {
        viewModelScope.launch {
            val setting = applicationContext.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.unfollowUser(gqlHeaders, userId)
                    follow.postValue(Pair(false, errorMessage))
                } else {
                    if (userId != null) {
                        localFollowsChannel.getFollowByUserId(userId)?.let { localFollowsChannel.deleteFollow(applicationContext, it) }
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
}