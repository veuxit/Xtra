package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.LoggedIn
import com.github.andreyasadchy.xtra.model.NotValidated
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.nullIfEmpty
import com.github.andreyasadchy.xtra.util.toast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.HttpException
import java.util.Timer
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: ApiRepository,
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient,
    private val json: Json) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val newNetworkStatus = MutableStateFlow<Boolean?>(null)
    val isNetworkAvailable = MutableStateFlow<Boolean?>(null)

    var isPlayerMaximized = false
        private set

    var isPlayerOpened = false
        private set

    var sleepTimer: Timer? = null
    var sleepTimerEndTime = 0L

    val video = MutableStateFlow<Video?>(null)
    val clip = MutableStateFlow<Clip?>(null)
    val user = MutableStateFlow<User?>(null)

    fun onMaximize() {
        isPlayerMaximized = true
    }

    fun onMinimize() {
        isPlayerMaximized = false
    }

    fun onPlayerStarted() {
        isPlayerOpened = true
        isPlayerMaximized = true
    }

    fun onPlayerClosed() {
        isPlayerOpened = false
        isPlayerMaximized = false
    }

    fun setNetworkAvailable(available: Boolean) {
        if (isNetworkAvailable.value != available) {
            isNetworkAvailable.value = available
            newNetworkStatus.value = available
        }
    }

    fun loadVideo(videoId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (video.value == null) {
            viewModelScope.launch {
                try {
                    video.value = repository.loadVideo(videoId, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun loadClip(clipId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (clip.value == null) {
            viewModelScope.launch {
                try {
                    clip.value = repository.loadClip(clipId, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun loadUser(login: String? = null, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (user.value == null) {
            viewModelScope.launch {
                try {
                    user.value = repository.loadCheckUser(channelLogin = login, helixHeaders = helixHeaders, gqlHeaders = gqlHeaders, checkIntegrity = checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun validate(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, activity: Activity) {
        val account = Account.get(activity)
        if (account is NotValidated) {
            viewModelScope.launch {
                try {
                    val helixToken = helixHeaders[C.HEADER_TOKEN]
                    if (!helixToken.isNullOrBlank()) {
                        val response = authRepository.validate(helixToken)
                        if (response.clientId.isNotBlank() && response.clientId == helixHeaders[C.HEADER_CLIENT_ID]) {
                            if ((!response.userId.isNullOrBlank() && response.userId != account.id) || (!response.login.isNullOrBlank() && response.login != account.login)) {
                                Account.set(activity, LoggedIn(response.userId?.nullIfEmpty() ?: account.id, response.login?.nullIfEmpty() ?: account.login))
                            }
                        } else {
                            throw IllegalStateException("401")
                        }
                    }
                    val gqlToken = gqlHeaders[C.HEADER_TOKEN]
                    if (!gqlToken.isNullOrBlank()) {
                        val response = authRepository.validate(gqlToken)
                        if (response.clientId.isNotBlank() && response.clientId == gqlHeaders[C.HEADER_CLIENT_ID]) {
                            if ((!response.userId.isNullOrBlank() && response.userId != account.id) || (!response.login.isNullOrBlank() && response.login != account.login)) {
                                Account.set(activity, LoggedIn(response.userId?.nullIfEmpty() ?: account.id, response.login?.nullIfEmpty() ?: account.login))
                            }
                        } else {
                            throw IllegalStateException("401")
                        }
                    }
                    if (!helixToken.isNullOrBlank() || !gqlToken.isNullOrBlank()) {
                        Account.validated()
                    }
                } catch (e: Exception) {
                    if ((e is IllegalStateException && e.message == "401") || (e is HttpException && e.code() == 401)) {
                        Account.set(activity, null)
                        activity.toast(R.string.token_expired)
                        (activity as? MainActivity)?.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                    }
                }
            }
        }
        TwitchApiHelper.checkedValidation = true
    }
}