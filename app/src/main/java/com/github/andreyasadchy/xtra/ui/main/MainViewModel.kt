package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import retrofit2.HttpException
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

    val video = MutableStateFlow<Video?>(null)
    val clip = MutableStateFlow<Clip?>(null)
    val user = MutableStateFlow<User?>(null)

    val updateUrl = MutableSharedFlow<String?>()

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

    fun checkUpdates(url: String, lastChecked: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            updateUrl.emit(
                try {
                    json.decodeFromString<JsonObject>(
                        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
                    )["assets"]?.jsonArray?.find {
                        it.jsonObject.getValue("content_type").jsonPrimitive.contentOrNull == "application/vnd.android.package-archive"
                    }?.jsonObject?.let { obj ->
                        obj.getValue("updated_at").jsonPrimitive.contentOrNull?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                            if (it > lastChecked) {
                                obj.getValue("browser_download_url").jsonPrimitive.contentOrNull
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            )
        }
        TwitchApiHelper.checkedUpdates = true
    }

    fun downloadUpdate(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val packageInstaller = applicationContext.packageManager.packageInstaller
                        val sessionId = packageInstaller.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
                        val session = packageInstaller.openSession(sessionId)
                        session.openWrite("package", 0, -1).sink().buffer().use { sink ->
                            sink.writeAll(response.body.source())
                        }
                        session.commit(PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java).apply {
                            setAction(MainActivity.INTENT_INSTALL_UPDATE)
                        }, PendingIntent.FLAG_MUTABLE).intentSender)
                        session.close()
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}