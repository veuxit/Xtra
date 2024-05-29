package com.github.andreyasadchy.xtra.ui.chat

import android.content.ContentResolver
import android.content.Context
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Chatter
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.chat.VideoChatMessage
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.ChatReplayManager
import com.github.andreyasadchy.xtra.ui.player.ChatReplayManagerLocal
import com.github.andreyasadchy.xtra.ui.view.chat.ChatView
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.chat.BroadcastSettings
import com.github.andreyasadchy.xtra.util.chat.ChatCallback
import com.github.andreyasadchy.xtra.util.chat.ChatListenerImpl
import com.github.andreyasadchy.xtra.util.chat.ChatReadIRC
import com.github.andreyasadchy.xtra.util.chat.ChatReadWebSocket
import com.github.andreyasadchy.xtra.util.chat.ChatWriteIRC
import com.github.andreyasadchy.xtra.util.chat.ChatWriteWebSocket
import com.github.andreyasadchy.xtra.util.chat.Command
import com.github.andreyasadchy.xtra.util.chat.EventSubCallback
import com.github.andreyasadchy.xtra.util.chat.EventSubListenerImpl
import com.github.andreyasadchy.xtra.util.chat.EventSubWebSocket
import com.github.andreyasadchy.xtra.util.chat.OnChatMessageReceivedListener
import com.github.andreyasadchy.xtra.util.chat.PlaybackMessage
import com.github.andreyasadchy.xtra.util.chat.PointsEarned
import com.github.andreyasadchy.xtra.util.chat.PubSubCallback
import com.github.andreyasadchy.xtra.util.chat.PubSubListenerImpl
import com.github.andreyasadchy.xtra.util.chat.PubSubWebSocket
import com.github.andreyasadchy.xtra.util.chat.Raid
import com.github.andreyasadchy.xtra.util.chat.RoomState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.use
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.collections.set


@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: ApiRepository,
    private val playerRepository: PlayerRepository,
    private val okHttpClient: OkHttpClient) : ViewModel(), ChatView.ChatViewCallback {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    val recentEmotes: LiveData<List<Emote>> by lazy {
        MediatorLiveData<List<Emote>>().apply {
            addSource(userEmotes) { user ->
                removeSource(userEmotes)
                addSource(_otherEmotes) { other ->
                    removeSource(_otherEmotes)
                    addSource(playerRepository.loadRecentEmotes()) { recent ->
                        value = recent.mapNotNull { emote -> (user + other).find { it.name == emote.name } }
                    }
                }
            }
        }
    }
    val userEmotes = MutableLiveData<List<Emote>>()
    private val _otherEmotes = MutableLiveData<List<Emote>>()
    val otherEmotes: LiveData<List<Emote>>
        get() = _otherEmotes

    private var loadedUserEmotes = false
    val localTwitchEmotes = MutableLiveData<List<TwitchEmote>>()
    val globalStvEmotes = MutableLiveData<List<Emote>?>()
    val channelStvEmotes = MutableLiveData<List<Emote>>()
    val globalBttvEmotes = MutableLiveData<List<Emote>?>()
    val channelBttvEmotes = MutableLiveData<List<Emote>>()
    val globalFfzEmotes = MutableLiveData<List<Emote>?>()
    val channelFfzEmotes = MutableLiveData<List<Emote>>()
    val globalBadges = MutableLiveData<List<TwitchBadge>?>()
    val channelBadges = MutableLiveData<List<TwitchBadge>>()
    val cheerEmotes = MutableLiveData<List<CheerEmote>>()
    val roomState = MutableLiveData<RoomState>()
    private var showRaids = false
    val raid = MutableLiveData<Raid>()
    val raidClicked = MutableLiveData<Boolean>()
    var raidAutoSwitch = false
    var raidNewId = true
    var raidClosed = false
    val viewerCount = MutableLiveData<Int?>()
    val title = MutableLiveData<BroadcastSettings?>()
    val streamLiveChanged = MutableLiveData<Pair<PlaybackMessage, String?>>()
    var streamId: String? = null
    private val rewardList = mutableListOf<ChatMessage>()

    private val _reloadMessages by lazy { SingleLiveEvent<Boolean>() }
    val reloadMessages: LiveData<Boolean>
        get() = _reloadMessages
    private val _scrollDown by lazy { SingleLiveEvent<Boolean>() }
    val scrollDown: LiveData<Boolean>
        get() = _scrollDown
    private val _command by lazy { SingleLiveEvent<Command>() }
    val command: LiveData<Command>
        get() = _command
    private val _pointsEarned by lazy { SingleLiveEvent<PointsEarned>() }
    val pointsEarned: LiveData<PointsEarned>
        get() = _pointsEarned

    private var messageLimit = 600
    private val _chatMessages by lazy {
        MutableLiveData<MutableList<ChatMessage>>().apply { value = Collections.synchronizedList(ArrayList(messageLimit + 1)) }
    }
    val chatMessages: LiveData<MutableList<ChatMessage>>
        get() = _chatMessages
    private val _newMessage by lazy { MutableLiveData<ChatMessage>() }
    val newMessage: LiveData<ChatMessage>
        get() = _newMessage

    var chat: ChatController? = null

    private val _newChatter by lazy { SingleLiveEvent<Chatter>() }
    val newChatter: LiveData<Chatter>
        get() = _newChatter

    val chatters: Collection<Chatter>?
        get() = (chat as? LiveChatController)?.chatters?.values

    fun startLive(useChatWebSocket: Boolean, useSSL: Boolean, usePubSub: Boolean, account: Account, isLoggedIn: Boolean, helixClientId: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, channelName: String?, streamId: String?, messageLimit: Int, emoteQuality: String, animateGifs: Boolean, showUserNotice: Boolean, showClearMsg: Boolean, showClearChat: Boolean, collectPoints: Boolean, notifyPoints: Boolean, showRaids: Boolean, autoSwitchRaids: Boolean, enableRecentMsg: Boolean, recentMsgLimit: String, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean, useApiCommands: Boolean, useApiChatMessages: Boolean, useEventSubChat: Boolean) {
        if (chat == null && channelLogin != null) {
            this.messageLimit = messageLimit
            this.streamId = streamId
            this.showRaids = showRaids
            raidAutoSwitch = autoSwitchRaids
            chat = LiveChatController(useChatWebSocket, useSSL, usePubSub, account, isLoggedIn, helixClientId, gqlHeaders, channelId, channelLogin, channelName, animateGifs, showUserNotice, showClearMsg, showClearChat, collectPoints, notifyPoints, useApiCommands, useApiChatMessages, useEventSubChat, checkIntegrity)
            chat?.start()
            loadEmotes(helixClientId, account.helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
            if (enableRecentMsg) {
                loadRecentMessages(channelLogin, recentMsgLimit)
            }
            if (isLoggedIn) {
                (chat as? LiveChatController)?.loadUserEmotes()
            }
        }
    }

    fun startReplay(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, localUri: String?, videoId: String?, startTime: Int, getCurrentPosition: () -> Long?, getCurrentSpeed: () -> Float?, messageLimit: Int, emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        if (chat == null) {
            this.messageLimit = messageLimit
            chat = VideoChatController(gqlHeaders, videoId, startTime, localUri, getCurrentPosition, getCurrentSpeed, helixClientId, helixToken, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
            chat?.start()
            if (videoId != null) {
                loadEmotes(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
            }
        }
    }

    fun start() {
        chat?.start()
    }

    fun stop() {
        chat?.pause()
    }

    override fun send(message: CharSequence) {
        chat?.send(message)
    }

    override fun onRaidClicked() {
        raidAutoSwitch = false
        raidClicked.postValue(true)
    }

    override fun onRaidClose() {
        raidAutoSwitch = false
        raidClosed = true
    }

    override fun onCleared() {
        chat?.stop()
        super.onCleared()
    }

    private fun loadEmotes(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        savedGlobalBadges.also { saved ->
            if (!saved.isNullOrEmpty()) {
                globalBadges.value = saved
                _reloadMessages.value = true
            } else {
                viewModelScope.launch {
                    try {
                        repository.loadGlobalBadges(helixClientId, helixToken, gqlHeaders, emoteQuality, checkIntegrity).let { badges ->
                            if (badges.isNotEmpty()) {
                                savedGlobalBadges = badges
                                globalBadges.value = badges
                                _reloadMessages.value = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global badges", e)
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                    }
                }
            }
        }
        if (enableStv) {
            savedGlobalStvEmotes.also { saved ->
                if (!saved.isNullOrEmpty()) {
                    (chat as? LiveChatController)?.addEmotes(saved)
                    globalStvEmotes.postValue(saved)
                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                        addAll(saved)
                        globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                        globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                    })
                    _reloadMessages.value = true
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalStvEmotes().body()?.emotes?.let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalStvEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    globalStvEmotes.postValue(emotes)
                                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                        addAll(emotes)
                                        globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                        globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                    })
                                    _reloadMessages.value = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load global 7tv emotes", e)
                        }
                    }
                }
            }
            if (!channelId.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        playerRepository.loadStvEmotes(channelId).body()?.emotes?.let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                channelStvEmotes.postValue(it)
                                _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                    addAll(it)
                                    channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                    channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                })
                                _reloadMessages.value = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load 7tv emotes for channel $channelId", e)
                    }
                }
            }
        }
        if (enableBttv) {
            savedGlobalBttvEmotes.also { saved ->
                if (!saved.isNullOrEmpty()) {
                    (chat as? LiveChatController)?.addEmotes(saved)
                    globalBttvEmotes.postValue(saved)
                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                        globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                        addAll(saved)
                        globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                    })
                    _reloadMessages.value = true
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalBttvEmotes().body()?.emotes?.let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalBttvEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    globalBttvEmotes.postValue(emotes)
                                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                        globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                                        addAll(emotes)
                                        globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                    })
                                    _reloadMessages.value = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load global BTTV emotes", e)
                        }
                    }
                }
            }
            if (!channelId.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        playerRepository.loadBttvEmotes(channelId).body()?.emotes?.let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                channelBttvEmotes.postValue(it)
                                _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                    channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                                    addAll(it)
                                    channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                })
                                _reloadMessages.value = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load BTTV emotes for channel $channelId", e)
                    }
                }
            }
        }
        if (enableFfz) {
            savedGlobalFfzEmotes.also { saved ->
                if (!saved.isNullOrEmpty()) {
                    (chat as? LiveChatController)?.addEmotes(saved)
                    globalFfzEmotes.postValue(saved)
                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                        globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                        globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                        addAll(saved)
                    })
                    _reloadMessages.value = true
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalFfzEmotes().body()?.emotes?.let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalFfzEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    globalFfzEmotes.postValue(emotes)
                                    _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                        channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                        channelFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                        globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                                        globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                        addAll(emotes)
                                    })
                                    _reloadMessages.value = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load global FFZ emotes", e)
                        }
                    }
                }
            }
            if (!channelId.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        playerRepository.loadFfzEmotes(channelId).body()?.emotes?.let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                channelFfzEmotes.postValue(it)
                                _otherEmotes.postValue(mutableListOf<Emote>().apply {
                                    channelStvEmotes.value?.let { emotes -> addAll(emotes) }
                                    channelBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                    addAll(it)
                                    globalStvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalBttvEmotes.value?.let { emotes -> addAll(emotes) }
                                    globalFfzEmotes.value?.let { emotes -> addAll(emotes) }
                                })
                                _reloadMessages.value = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load FFZ emotes for channel $channelId", e)
                    }
                }
            }
        }
        if (!channelId.isNullOrBlank() || !channelLogin.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    repository.loadChannelBadges(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, checkIntegrity).let {
                        if (it.isNotEmpty()) {
                            channelBadges.postValue(it)
                            _reloadMessages.value = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load badges for channel $channelId", e)
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
            viewModelScope.launch {
                try {
                    repository.loadCheerEmotes(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, animateGifs, checkIntegrity).let {
                        if (it.isNotEmpty()) {
                            cheerEmotes.postValue(it)
                            _reloadMessages.value = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cheermotes for channel $channelId", e)
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
        }
    }

    fun loadRecentMessages(channelLogin: String, recentMsgLimit: String) {
        viewModelScope.launch {
            try {
                playerRepository.loadRecentMessages(channelLogin, recentMsgLimit).body()?.messages?.let {
                    if (it.isNotEmpty()) {
                        _chatMessages.postValue(_chatMessages.value?.apply { addAll(0, it) })
                        _scrollDown.postValue(true)
                        _reloadMessages.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recent messages for channel $channelLogin", e)
            }
        }
    }

    fun reloadEmotes(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        savedGlobalBadges = null
        savedGlobalStvEmotes = null
        savedGlobalBttvEmotes = null
        savedGlobalFfzEmotes = null
        loadEmotes(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
    }

    fun getEmoteBytes(chatUrl: String, localData: Pair<Long, Int>): ByteArray? {
        return if (chatUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
            applicationContext.contentResolver.openInputStream(chatUrl.toUri())?.bufferedReader()
        } else {
            FileInputStream(File(chatUrl)).bufferedReader()
        }?.use { fileReader ->
            val buffer = CharArray(localData.second)
            fileReader.skip(localData.first)
            fileReader.read(buffer, 0, localData.second)
            Base64.decode(buffer.concatToString(), Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }

    inner class LiveChatController(
        private val useChatWebSocket: Boolean,
        private val useSSL: Boolean,
        private val usePubSub: Boolean,
        private val account: Account,
        private val isLoggedIn: Boolean,
        private val helixClientId: String?,
        private val gqlHeaders: Map<String, String>,
        private val channelId: String?,
        private val channelLogin: String,
        channelName: String?,
        private val animateGifs: Boolean,
        private val showUserNotice: Boolean,
        private val showClearMsg: Boolean,
        private val showClearChat: Boolean,
        private val collectPoints: Boolean,
        private val notifyPoints: Boolean,
        private val useApiCommands: Boolean,
        private val useApiChatMessages: Boolean,
        private val useEventSubChat: Boolean,
        private val checkIntegrity: Boolean) : ChatController(), ChatCallback, PubSubCallback, EventSubCallback {

        private var chatReadIRC: ChatReadIRC? = null
        private var chatWriteIRC: ChatWriteIRC? = null
        private var chatReadWebSocket: ChatReadWebSocket? = null
        private var chatWriteWebSocket: ChatWriteWebSocket? = null
        private var eventSub: EventSubWebSocket? = null
        private var pubSub: PubSubWebSocket? = null
        private val allEmotes = mutableListOf<Emote>()
        private var usedRaidId: String? = null

        val chatters = ConcurrentHashMap<String?, Chatter>()

        init {
            addChatter(channelName)
        }

        private fun addChatter(displayName: String?) {
            if (displayName != null && !chatters.containsKey(displayName)) {
                val chatter = Chatter(displayName)
                chatters[displayName] = chatter
                _newChatter.postValue(chatter)
            }
        }

        override fun send(message: CharSequence) {
            if (useApiCommands) {
                if (message.toString().startsWith("/")) {
                    try {
                        sendCommand(message)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                    }
                } else {
                    sendMessage(message)
                }
            } else {
                if (message.toString() == "/dc" || message.toString() == "/disconnect") {
                    if ((chatReadIRC?.isActive ?: chatReadWebSocket?.isActive ?: eventSub?.isActive) == true) {
                        disconnect()
                    }
                } else {
                    sendMessage(message)
                }
            }
        }

        override fun start() {
            pause()
            val gqlToken = gqlHeaders[C.HEADER_TOKEN]?.removePrefix("OAuth ")
            val helixToken = account.helixToken
            if (useEventSubChat && !account.helixToken.isNullOrBlank()) {
                eventSub = EventSubWebSocket(channelLogin, okHttpClient, viewModelScope, EventSubListenerImpl(this, this, this, showUserNotice, showClearChat, usePubSub)).apply { connect() }
            } else {
                if (useChatWebSocket) {
                    chatReadWebSocket = ChatReadWebSocket(isLoggedIn, channelLogin, okHttpClient, viewModelScope, ChatListenerImpl(this, this, showUserNotice, showClearMsg, showClearChat, usePubSub)).apply { connect() }
                    if (isLoggedIn && (!gqlToken.isNullOrBlank() || !account.helixToken.isNullOrBlank() && !useApiChatMessages)) {
                        chatWriteWebSocket = ChatWriteWebSocket(account.login, gqlToken?.takeIf { it.isNotBlank() } ?: helixToken, channelLogin, okHttpClient, viewModelScope, ChatListenerImpl(this, this, showUserNotice, showClearMsg, showClearChat, usePubSub)).apply { connect() }
                    }
                } else {
                    chatReadIRC = ChatReadIRC(useSSL, isLoggedIn, channelLogin, ChatListenerImpl(this, this, showUserNotice, showClearMsg, showClearChat, usePubSub)).apply { start() }
                    if (isLoggedIn && (!gqlToken.isNullOrBlank() || !account.helixToken.isNullOrBlank() && !useApiChatMessages)) {
                        chatWriteIRC = ChatWriteIRC(useSSL, account.login, gqlToken?.takeIf { it.isNotBlank() } ?: helixToken, channelLogin, ChatListenerImpl(this, this, showUserNotice, showClearMsg, showClearChat, usePubSub)).apply { start() }
                    }
                }
            }
            if (usePubSub && !channelId.isNullOrBlank()) {
                pubSub = PubSubWebSocket(channelId, account.id, gqlHeaders[C.HEADER_TOKEN]?.removePrefix("OAuth "), collectPoints, notifyPoints, showRaids, okHttpClient, viewModelScope, PubSubListenerImpl(this, this)).apply { connect() }
            }
        }

        override fun pause() {
            chatReadIRC?.disconnect() ?: chatReadWebSocket?.disconnect() ?: eventSub?.disconnect()
            chatWriteIRC?.disconnect() ?: chatWriteWebSocket?.disconnect()
            pubSub?.disconnect()
        }

        override fun stop() {
            pause()
        }

        override fun onMessage(message: ChatMessage) {
            super.onMessage(message)
            addChatter(message.userName)
        }

        override fun onCommand(list: Command) {
            _command.postValue(list)
        }

        override fun onRoomState(list: RoomState) {
            roomState.postValue(list)
        }

        fun loadUserEmotes() {
            savedUserEmotes.also { saved ->
                if (!saved.isNullOrEmpty()) {
                    addEmotes(saved.map { Emote(
                        name = it.name,
                        url1x = it.url1x,
                        url2x = it.url2x,
                        url3x = it.url3x,
                        url4x = it.url4x,
                        format = it.format
                    ) })
                    userEmotes.postValue(saved.sortedByDescending { it.ownerId == channelId }.map { Emote(
                        name = it.name,
                        url1x = it.url1x,
                        url2x = it.url2x,
                        url3x = it.url3x,
                        url4x = it.url4x,
                        format = it.format
                    ) })
                } else {
                    if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            try {
                                repository.loadUserEmotes(helixClientId, account.helixToken, gqlHeaders, channelId, account.id, animateGifs, checkIntegrity).let { emotes ->
                                    if (emotes.isNotEmpty()) {
                                        val sorted = emotes.sortedByDescending { it.setId }
                                        addEmotes(sorted.map { Emote(
                                            name = it.name,
                                            url1x = it.url1x,
                                            url2x = it.url2x,
                                            url3x = it.url3x,
                                            url4x = it.url4x,
                                            format = it.format
                                        ) })
                                        userEmotes.postValue(sorted.sortedByDescending { it.ownerId == channelId }.map { Emote(
                                            name = it.name,
                                            url1x = it.url1x,
                                            url2x = it.url2x,
                                            url3x = it.url3x,
                                            url4x = it.url4x,
                                            format = it.format
                                        ) })
                                        loadedUserEmotes = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load user emotes", e)
                                if (e.message == "failed integrity check") {
                                    _integrity.postValue(true)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun loadEmoteSets() {
            if (!savedEmoteSets.isNullOrEmpty() && !helixClientId.isNullOrBlank() && !account.helixToken.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val emotes = mutableListOf<TwitchEmote>()
                        savedEmoteSets?.chunked(25)?.forEach { list ->
                            repository.loadEmotesFromSet(helixClientId, account.helixToken, list, animateGifs).let { emotes.addAll(it) }
                        }
                        if (emotes.isNotEmpty()) {
                            val sorted = emotes.sortedByDescending { it.setId }
                            savedUserEmotes = sorted
                            addEmotes(sorted.map { Emote(
                                name = it.name,
                                url1x = it.url1x,
                                url2x = it.url2x,
                                url3x = it.url3x,
                                url4x = it.url4x,
                                format = it.format
                            ) })
                            userEmotes.postValue(sorted.sortedByDescending { it.ownerId == channelId }.map { Emote(
                                name = it.name,
                                url1x = it.url1x,
                                url2x = it.url2x,
                                url3x = it.url3x,
                                url4x = it.url4x,
                                format = it.format
                            ) })
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load emote sets", e)
                    }
                }
            }
        }

        override fun onUserState(emoteSets: List<String>?) {
            if (savedEmoteSets != emoteSets) {
                savedEmoteSets = emoteSets
                if (!loadedUserEmotes) {
                    loadEmoteSets()
                }
            }
        }

        override fun onPlaybackMessage(message: PlaybackMessage) {
            message.live?.let {
                if (it) {
                    _command.postValue(Command(duration = channelLogin, type = "stream_live"))
                } else {
                    _command.postValue(Command(duration = channelLogin, type = "stream_offline"))
                }
                streamLiveChanged.postValue(Pair(message, channelLogin))
            }
            viewerCount.postValue(message.viewers)
        }

        override fun onTitleUpdate(message: BroadcastSettings) {
            this@ChatViewModel.title.postValue(message)
        }

        override fun onRewardMessage(message: ChatMessage) {
            if (message.reward?.id != null) {
                val item = rewardList.find { it.reward?.id == message.reward.id && it.userId == message.userId }
                if (item != null) {
                    rewardList.remove(item)
                    onMessage(ChatMessage(
                        id = message.id ?: item.id,
                        userId = message.userId ?: item.userId,
                        userLogin = message.userLogin ?: item.userLogin,
                        userName = message.userName ?: item.userName,
                        message = message.message ?: item.message,
                        color = message.color ?: item.color,
                        emotes = message.emotes ?: item.emotes,
                        badges = message.badges ?: item.badges,
                        isAction = message.isAction || item.isAction,
                        isFirst = message.isFirst || item.isFirst,
                        bits = message.bits ?: item.bits,
                        systemMsg = message.systemMsg ?: item.systemMsg,
                        msgId = message.msgId ?: item.msgId,
                        reward = ChannelPointReward(
                            id = message.reward.id,
                            title = message.reward.title ?: item.reward?.title,
                            cost = message.reward.cost ?: item.reward?.cost,
                            url1x = message.reward.url1x ?: item.reward?.url1x,
                            url2x = message.reward.url2x ?: item.reward?.url2x,
                            url4x = message.reward.url4x ?: item.reward?.url4x,
                        ),
                        timestamp = message.timestamp ?: item.timestamp,
                        fullMsg = message.fullMsg ?: item.fullMsg,
                    ))
                } else {
                    rewardList.add(message)
                }
            } else {
                onMessage(message)
            }
        }

        override fun onPointsEarned(message: PointsEarned) {
            _pointsEarned.postValue(message)
        }

        override fun onClaimAvailable() {
            if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        repository.loadClaimPoints(gqlHeaders, channelId, channelLogin)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                    }
                }
            }
        }

        override fun onMinuteWatched() {
            if (!streamId.isNullOrBlank()) {
                viewModelScope.launch {
                    repository.loadMinuteWatched(account.id, streamId, channelId, channelLogin)
                }
            }
        }

        override fun onRaidUpdate(message: Raid) {
            raidNewId = message.raidId != usedRaidId
            raid.postValue(message)
            if (raidNewId) {
                usedRaidId = message.raidId
                if (collectPoints && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    viewModelScope.launch {
                        try {
                            repository.loadJoinRaid(gqlHeaders, message.raidId)
                        } catch (e: Exception) {
                            if (e.message == "failed integrity check") {
                                _integrity.postValue(true)
                            }
                        }
                    }
                }
            }
        }

        override fun onWelcomeMessage(sessionId: String) {
            listOf(
                "channel.chat.clear",
                "channel.chat.message",
                "channel.chat.notification",
                "channel.chat_settings.update",
            ).forEach {
                viewModelScope.launch {
                    repository.createChatEventSubSubscription(helixClientId, account.helixToken, account.id, channelId, it, sessionId)?.let {
                        onMessage(ChatMessage(message = it, color = "#999999", isAction = true))
                    }
                }
            }
        }

        fun addEmotes(list: List<Emote>) {
            allEmotes.addAll(list.filter { it !in allEmotes })
        }

        fun isActive(): Boolean? {
            return chatReadIRC?.isActive ?: chatReadWebSocket?.isActive ?: eventSub?.isActive
        }

        fun disconnect() {
            if ((chatReadIRC?.isActive ?: chatReadWebSocket?.isActive ?: eventSub?.isActive) == true) {
                chatReadIRC?.disconnect() ?: chatReadWebSocket?.disconnect() ?: eventSub?.disconnect()
                chatWriteIRC?.disconnect() ?: chatWriteWebSocket?.disconnect() ?: eventSub?.disconnect()
                pubSub?.disconnect()
                usedRaidId = null
                onRaidClose()
                _chatMessages.postValue(mutableListOf())
                _command.postValue(Command(type = "disconnect_command"))
            }
        }

        private fun sendMessage(message: CharSequence) {
            if (useApiChatMessages && !account.helixToken.isNullOrBlank()) {
                viewModelScope.launch {
                    repository.sendMessage(helixClientId, account.helixToken, account.id, channelId, message.toString())?.let {
                        onMessage(ChatMessage(message = it, color = "#999999", isAction = true))
                    }
                }
            } else {
                chatWriteIRC?.send(message) ?: chatWriteWebSocket?.send(message)
            }
            val usedEmotes = hashSetOf<RecentEmote>()
            val currentTime = System.currentTimeMillis()
            message.split(' ').forEach { word ->
                allEmotes.find { it.name == word }?.let { usedEmotes.add(RecentEmote(word, currentTime)) }
            }
            if (usedEmotes.isNotEmpty()) {
                viewModelScope.launch {
                    playerRepository.insertRecentEmotes(usedEmotes)
                }
            }
        }

        private fun sendCommand(message: CharSequence) {
            val command = message.toString().substringBefore(" ")
            when {
                command.startsWith("/announce", true) -> {
                    val splits = message.split(" ", limit = 2)
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.sendAnnouncement(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                message = splits[1],
                                color = splits[0].substringAfter("/announce", "").ifBlank { null }
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/ban", true) -> {
                    val splits = message.split(" ", limit = 3)
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.banUser(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1],
                                reason = if (splits.size >= 3) splits[2] else null
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/unban", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.unbanUser(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/clear", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.deleteMessages(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/color", true) -> {
                    val splits = message.split(" ")
                    viewModelScope.launch {
                        if (splits.size >= 2) {
                            repository.updateChatColor(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                color = splits[1]
                            )
                        } else {
                            repository.getChatColor(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id
                            )
                        }?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/commercial", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        val splits = message.split(" ")
                        if (splits.size >= 2) {
                            viewModelScope.launch {
                                repository.startCommercial(
                                    helixClientId = helixClientId,
                                    helixToken = account.helixToken,
                                    channelId = channelId,
                                    length = splits[1]
                                )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                            }
                        }
                    } else sendMessage(message)
                }
                command.equals("/delete", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        val splits = message.split(" ")
                        if (splits.size >= 2) {
                            viewModelScope.launch {
                                repository.deleteMessages(
                                    helixClientId = helixClientId,
                                    helixToken = account.helixToken,
                                    channelId = channelId,
                                    userId = account.id,
                                    messageId = splits[1]
                                )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                            }
                        }
                    } else sendMessage(message)
                }
                command.equals("/disconnect", true) -> disconnect()
                command.equals("/emoteonly", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                emote = true
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/emoteonlyoff", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                emote = false
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/followers", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        val splits = message.split(" ")
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                followers = true,
                                followersDuration = if (splits.size >= 2) splits[1] else null
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/followersoff", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                followers = false
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/marker", true) -> {
                    val splits = message.split(" ", limit = 2)
                    viewModelScope.launch {
                        repository.createStreamMarker(
                            helixClientId = helixClientId,
                            helixToken = account.helixToken,
                            channelId = channelId,
                            gqlHeaders = gqlHeaders,
                            channelLogin = channelLogin,
                            description = if (splits.size >= 2) splits[1] else null
                        )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/mod", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.addModerator(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/unmod", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.removeModerator(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/mods", true) -> {
                    viewModelScope.launch {
                        repository.getModerators(
                            gqlHeaders = gqlHeaders,
                            channelLogin = channelLogin,
                        )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/raid", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.startRaid(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1],
                                checkIntegrity = checkIntegrity
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/unraid", true) -> {
                    viewModelScope.launch {
                        repository.cancelRaid(
                            helixClientId = helixClientId,
                            helixToken = account.helixToken,
                            gqlHeaders = gqlHeaders,
                            channelId = channelId
                        )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/slow", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        val splits = message.split(" ")
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                slow = true,
                                slowDuration = if (splits.size >= 2) splits[1].toIntOrNull() else null
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/slowoff", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                slow = false
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/subscribers", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                subs = true,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/subscribersoff", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                subs = false,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/timeout", true) -> {
                    val splits = message.split(" ", limit = 4)
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.banUser(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1],
                                duration = if (splits.size >= 3) splits[2] else null ?: if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) "10m" else "600",
                                reason = if (splits.size >= 4) splits[3] else null,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/untimeout", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.unbanUser(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/uniquechat", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                unique = true,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/uniquechatoff", true) -> {
                    if (!account.helixToken.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                channelId = channelId,
                                userId = account.id,
                                unique = false,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/vip", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.addVip(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/unvip", true) -> {
                    val splits = message.split(" ")
                    if (splits.size >= 2) {
                        viewModelScope.launch {
                            repository.removeVip(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/vips", true) -> {
                    viewModelScope.launch {
                        repository.getVips(
                            gqlHeaders = gqlHeaders,
                            channelLogin = channelLogin,
                        )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/w", true) -> {
                    val splits = message.split(" ", limit = 3)
                    if (splits.size >= 3) {
                        viewModelScope.launch {
                            repository.sendWhisper(
                                helixClientId = helixClientId,
                                helixToken = account.helixToken,
                                userId = account.id,
                                targetLogin = splits[1],
                                message = splits[2]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                else -> sendMessage(message)
            }
        }
    }

    inner class VideoChatController(
        private val gqlHeaders: Map<String, String>,
        private val videoId: String?,
        private val startTime: Int,
        private val chatUrl: String?,
        private val getCurrentPosition: () -> Long?,
        private val getCurrentSpeed: () -> Float?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val channelId: String?,
        private val channelLogin: String?,
        private val emoteQuality: String,
        private val animateGifs: Boolean,
        private val enableStv: Boolean,
        private val enableBttv: Boolean,
        private val enableFfz: Boolean,
        private val checkIntegrity: Boolean) : ChatController() {

        private var chatReplayManager: ChatReplayManager? = null
        private var chatReplayManagerLocal: ChatReplayManagerLocal? = null

        override fun send(message: CharSequence) {}

        override fun start() {
            pause()
            if (!chatUrl.isNullOrBlank()) {
                readChatFile(chatUrl)
            } else {
                if (!videoId.isNullOrBlank()) {
                    chatReplayManager = ChatReplayManager(gqlHeaders, repository, videoId, startTime, getCurrentPosition, getCurrentSpeed, this, { _chatMessages.postValue(ArrayList()) }, { _integrity.postValue(true) }, viewModelScope).apply { start() }
                }
            }
        }

        override fun pause() {
            chatReplayManager?.stop() ?: chatReplayManagerLocal?.stop()
        }

        override fun stop() {
            pause()
        }

        fun updatePosition(position: Long) {
            chatReplayManager?.updatePosition(position) ?: chatReplayManagerLocal?.updatePosition(position)
        }

        fun updateSpeed(speed: Float) {
            chatReplayManager?.updateSpeed(speed) ?: chatReplayManagerLocal?.updateSpeed(speed)
        }

        private fun readChatFile(url: String) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val messages = mutableListOf<VideoChatMessage>()
                    var startTimeMs = 0L
                    val twitchEmotes = mutableListOf<TwitchEmote>()
                    val twitchBadges = mutableListOf<TwitchBadge>()
                    val cheerEmotesList = mutableListOf<CheerEmote>()
                    val emotes = mutableListOf<Emote>()
                    if (url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                        applicationContext.contentResolver.openInputStream(url.toUri())?.bufferedReader()
                    } else {
                        FileInputStream(File(url)).bufferedReader()
                    }?.use { fileReader ->
                        JsonReader(fileReader).use { reader ->
                            var position = 0L
                            reader.beginObject().also { position += 1 }
                            while (reader.hasNext()) {
                                when (reader.peek()) {
                                    JsonToken.NAME -> {
                                        when (reader.nextName().also { position += it.length + 3 }) {
                                            "comments" -> {
                                                reader.beginArray().also { position += 1 }
                                                while (reader.hasNext()) {
                                                    reader.beginObject().also { position += 1 }
                                                    val message = StringBuilder()
                                                    var id: String? = null
                                                    var offsetSeconds: Int? = null
                                                    var userId: String? = null
                                                    var userLogin: String? = null
                                                    var userName: String? = null
                                                    var color: String? = null
                                                    val emotesList = mutableListOf<TwitchEmote>()
                                                    val badgesList = mutableListOf<Badge>()
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                            "id" -> id = reader.nextString().also { position += it.length + 2 }
                                                            "commenter" -> {
                                                                reader.beginObject().also { position += 1 }
                                                                while (reader.hasNext()) {
                                                                    when (reader.nextName().also { position += it.length + 3 }) {
                                                                        "id" -> userId = reader.nextString().also { position += it.length + 2 }
                                                                        "login" -> userLogin = reader.nextString().also { position += it.length + 2 }
                                                                        "displayName" -> userName = reader.nextString().also { position += it.length + 2 }
                                                                        else -> position += skipJsonValue(reader)
                                                                    }
                                                                    if (reader.peek() != JsonToken.END_OBJECT) {
                                                                        position += 1
                                                                    }
                                                                }
                                                                reader.endObject().also { position += 1 }
                                                            }
                                                            "contentOffsetSeconds" -> offsetSeconds = reader.nextInt().also { position += it.toString().length }
                                                            "message" -> {
                                                                reader.beginObject().also { position += 1 }
                                                                while (reader.hasNext()) {
                                                                    when (reader.nextName().also { position += it.length + 3 }) {
                                                                        "fragments" -> {
                                                                            reader.beginArray().also { position += 1 }
                                                                            while (reader.hasNext()) {
                                                                                reader.beginObject().also { position += 1 }
                                                                                var emoteId: String? = null
                                                                                var fragmentText: String? = null
                                                                                while (reader.hasNext()) {
                                                                                    when (reader.nextName().also { position += it.length + 3 }) {
                                                                                        "emote" -> {
                                                                                            when (reader.peek()) {
                                                                                                JsonToken.BEGIN_OBJECT -> {
                                                                                                    reader.beginObject().also { position += 1 }
                                                                                                    while (reader.hasNext()) {
                                                                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                                                                            "emoteID" -> emoteId = reader.nextString().also { position += it.length + 2 }
                                                                                                            else -> position += skipJsonValue(reader)
                                                                                                        }
                                                                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                                                                            position += 1
                                                                                                        }
                                                                                                    }
                                                                                                    reader.endObject().also { position += 1 }
                                                                                                }
                                                                                                else -> position += skipJsonValue(reader)
                                                                                            }
                                                                                        }
                                                                                        "text" -> fragmentText = reader.nextString().also { position += it.length + 2 + it.count { c -> c == '"' || c == '\\' } }
                                                                                        else -> position += skipJsonValue(reader)
                                                                                    }
                                                                                    if (reader.peek() != JsonToken.END_OBJECT) {
                                                                                        position += 1
                                                                                    }
                                                                                }
                                                                                if (fragmentText != null && !emoteId.isNullOrBlank()) {
                                                                                    emotesList.add(TwitchEmote(
                                                                                        id = emoteId,
                                                                                        begin = message.codePointCount(0, message.length),
                                                                                        end = message.codePointCount(0, message.length) + fragmentText.lastIndex
                                                                                    ))
                                                                                }
                                                                                message.append(fragmentText)
                                                                                reader.endObject().also { position += 1 }
                                                                                if (reader.peek() != JsonToken.END_ARRAY) {
                                                                                    position += 1
                                                                                }
                                                                            }
                                                                            reader.endArray().also { position += 1 }
                                                                        }
                                                                        "userBadges" -> {
                                                                            reader.beginArray().also { position += 1 }
                                                                            while (reader.hasNext()) {
                                                                                reader.beginObject().also { position += 1 }
                                                                                var set: String? = null
                                                                                var version: String? = null
                                                                                while (reader.hasNext()) {
                                                                                    when (reader.nextName().also { position += it.length + 3 }) {
                                                                                        "setID" -> set = reader.nextString().also { position += it.length + 2 }
                                                                                        "version" -> version = reader.nextString().also { position += it.length + 2 }
                                                                                        else -> position += skipJsonValue(reader)
                                                                                    }
                                                                                    if (reader.peek() != JsonToken.END_OBJECT) {
                                                                                        position += 1
                                                                                    }
                                                                                }
                                                                                if (!set.isNullOrBlank() && !version.isNullOrBlank()) {
                                                                                    badgesList.add(Badge(set, version))
                                                                                }
                                                                                reader.endObject().also { position += 1 }
                                                                                if (reader.peek() != JsonToken.END_ARRAY) {
                                                                                    position += 1
                                                                                }
                                                                            }
                                                                            reader.endArray().also { position += 1 }
                                                                        }
                                                                        "userColor" -> {
                                                                            when (reader.peek()) {
                                                                                JsonToken.STRING -> color = reader.nextString().also { position += it.length + 2 }
                                                                                else -> position += skipJsonValue(reader)
                                                                            }
                                                                        }
                                                                        else -> position += skipJsonValue(reader)
                                                                    }
                                                                    if (reader.peek() != JsonToken.END_OBJECT) {
                                                                        position += 1
                                                                    }
                                                                }
                                                                messages.add(VideoChatMessage(
                                                                    id = id,
                                                                    offsetSeconds = offsetSeconds,
                                                                    userId = userId,
                                                                    userLogin = userLogin,
                                                                    userName = userName,
                                                                    message = message.toString(),
                                                                    color = color,
                                                                    emotes = emotesList,
                                                                    badges = badgesList,
                                                                    fullMsg = null
                                                                ))
                                                                reader.endObject().also { position += 1 }
                                                            }
                                                            else -> position += skipJsonValue(reader)
                                                        }
                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                            position += 1
                                                        }
                                                    }
                                                    reader.endObject().also { position += 1 }
                                                    if (reader.peek() != JsonToken.END_ARRAY) {
                                                        position += 1
                                                    }
                                                }
                                                reader.endArray().also { position += 1 }
                                            }
                                            "twitchEmotes" -> {
                                                reader.beginArray().also { position += 1 }
                                                while (reader.hasNext()) {
                                                    reader.beginObject().also { position += 1 }
                                                    var id: String? = null
                                                    var data: Pair<Long, Int>? = null
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                            "data" -> {
                                                                position += 1
                                                                val length = reader.nextString().length
                                                                data = Pair(position, length)
                                                                position += length + 1
                                                            }
                                                            "id" -> id = reader.nextString().also { position += it.length + 2 }
                                                            else -> position += skipJsonValue(reader)
                                                        }
                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                            position += 1
                                                        }
                                                    }
                                                    if (!id.isNullOrBlank() && data != null) {
                                                        twitchEmotes.add(TwitchEmote(
                                                            id = id,
                                                            localData = data
                                                        ))
                                                    }
                                                    reader.endObject().also { position += 1 }
                                                    if (reader.peek() != JsonToken.END_ARRAY) {
                                                        position += 1
                                                    }
                                                }
                                                reader.endArray().also { position += 1 }
                                            }
                                            "twitchBadges" -> {
                                                reader.beginArray().also { position += 1 }
                                                while (reader.hasNext()) {
                                                    reader.beginObject().also { position += 1 }
                                                    var setId: String? = null
                                                    var version: String? = null
                                                    var data: Pair<Long, Int>? = null
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                            "data" -> {
                                                                position += 1
                                                                val length = reader.nextString().length
                                                                data = Pair(position, length)
                                                                position += length + 1
                                                            }
                                                            "setId" -> setId = reader.nextString().also { position += it.length + 2 }
                                                            "version" -> version = reader.nextString().also { position += it.length + 2 }
                                                            else -> position += skipJsonValue(reader)
                                                        }
                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                            position += 1
                                                        }
                                                    }
                                                    if (!setId.isNullOrBlank() && !version.isNullOrBlank() && data != null) {
                                                        twitchBadges.add(TwitchBadge(
                                                            setId = setId,
                                                            version = version,
                                                            localData = data
                                                        ))
                                                    }
                                                    reader.endObject().also { position += 1 }
                                                    if (reader.peek() != JsonToken.END_ARRAY) {
                                                        position += 1
                                                    }
                                                }
                                                reader.endArray().also { position += 1 }
                                            }
                                            "cheerEmotes" -> {
                                                reader.beginArray().also { position += 1 }
                                                while (reader.hasNext()) {
                                                    reader.beginObject().also { position += 1 }
                                                    var name: String? = null
                                                    var data: Pair<Long, Int>? = null
                                                    var minBits: Int? = null
                                                    var color: String? = null
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                            "data" -> {
                                                                position += 1
                                                                val length = reader.nextString().length
                                                                data = Pair(position, length)
                                                                position += length + 1
                                                            }
                                                            "name" -> name = reader.nextString().also { position += it.length + 2 }
                                                            "minBits" -> minBits = reader.nextInt().also { position += it.toString().length }
                                                            "color" -> {
                                                                when (reader.peek()) {
                                                                    JsonToken.STRING -> color = reader.nextString().also { position += it.length + 2 }
                                                                    else -> position += skipJsonValue(reader)
                                                                }
                                                            }
                                                            else -> position += skipJsonValue(reader)
                                                        }
                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                            position += 1
                                                        }
                                                    }
                                                    if (!name.isNullOrBlank() && minBits != null && data != null) {
                                                        cheerEmotesList.add(CheerEmote(
                                                            name = name,
                                                            localData = data,
                                                            minBits = minBits,
                                                            color = color
                                                        ))
                                                    }
                                                    reader.endObject().also { position += 1 }
                                                    if (reader.peek() != JsonToken.END_ARRAY) {
                                                        position += 1
                                                    }
                                                }
                                                reader.endArray().also { position += 1 }
                                            }
                                            "emotes" -> {
                                                reader.beginArray().also { position += 1 }
                                                while (reader.hasNext()) {
                                                    reader.beginObject().also { position += 1 }
                                                    var data: Pair<Long, Int>? = null
                                                    var name: String? = null
                                                    var isZeroWidth = false
                                                    while (reader.hasNext()) {
                                                        when (reader.nextName().also { position += it.length + 3 }) {
                                                            "data" -> {
                                                                position += 1
                                                                val length = reader.nextString().length
                                                                data = Pair(position, length)
                                                                position += length + 1
                                                            }
                                                            "name" -> name = reader.nextString().also { position += it.length + 2 }
                                                            "isZeroWidth" -> isZeroWidth = reader.nextBoolean().also { position += it.toString().length }
                                                            else -> position += skipJsonValue(reader)
                                                        }
                                                        if (reader.peek() != JsonToken.END_OBJECT) {
                                                            position += 1
                                                        }
                                                    }
                                                    if (!name.isNullOrBlank() && data != null) {
                                                        emotes.add(Emote(
                                                            name = name,
                                                            localData = data,
                                                            isZeroWidth = isZeroWidth
                                                        ))
                                                    }
                                                    reader.endObject().also { position += 1 }
                                                    if (reader.peek() != JsonToken.END_ARRAY) {
                                                        position += 1
                                                    }
                                                }
                                                reader.endArray().also { position += 1 }
                                            }
                                            "startTime" -> { startTimeMs = reader.nextInt().also { position += it.toString().length }.times(1000L) }
                                            else -> position += skipJsonValue(reader)
                                        }
                                    }
                                    else -> position += skipJsonValue(reader)
                                }
                                if (reader.peek() != JsonToken.END_OBJECT) {
                                    position += 1
                                }
                            }
                            reader.endObject().also { position += 1 }
                        }
                    }
                    localTwitchEmotes.postValue(twitchEmotes)
                    channelBadges.postValue(twitchBadges)
                    cheerEmotes.postValue(cheerEmotesList)
                    channelStvEmotes.postValue(emotes)
                    if (emotes.isEmpty()) {
                        viewModelScope.launch {
                            loadEmotes(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
                        }
                    }
                    if (messages.isNotEmpty()) {
                        viewModelScope.launch {
                            chatReplayManagerLocal = ChatReplayManagerLocal(messages, startTimeMs, getCurrentPosition, getCurrentSpeed, this@VideoChatController, { _chatMessages.postValue(ArrayList()) }, viewModelScope).apply { start() }
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }

        private fun skipJsonValue(reader: JsonReader): Int {
            var length = 0
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray().also { length += 1 }
                    while (reader.hasNext()) {
                        when (reader.peek()) {
                            JsonToken.NAME -> length += reader.nextName().length + 3
                            else -> {
                                length += skipJsonValue(reader)
                                if (reader.peek() != JsonToken.END_ARRAY) {
                                    length += 1
                                }
                            }
                        }
                    }
                    reader.endArray().also { length += 1 }
                }
                JsonToken.END_ARRAY -> length += 1
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject().also { length += 1 }
                    while (reader.hasNext()) {
                        when (reader.peek()) {
                            JsonToken.NAME -> length += reader.nextName().length + 3
                            else -> {
                                length += skipJsonValue(reader)
                                if (reader.peek() != JsonToken.END_OBJECT) {
                                    length += 1
                                }
                            }
                        }
                    }
                    reader.endObject().also { length += 1 }
                }
                JsonToken.END_OBJECT -> length += 1
                JsonToken.STRING -> reader.nextString().let { length += it.length + 2 + it.count { c -> c == '"' || c == '\\' } }
                JsonToken.NUMBER -> length += reader.nextString().length
                JsonToken.BOOLEAN -> length += reader.nextBoolean().toString().length
                else -> reader.skipValue()
            }
            return length
        }
    }

    abstract inner class ChatController : OnChatMessageReceivedListener {
        abstract fun send(message: CharSequence)
        abstract fun start()
        abstract fun pause()
        abstract fun stop()

        override fun onMessage(message: ChatMessage) {
            _chatMessages.value?.add(message)
            _newMessage.postValue(message)
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"

        private var savedEmoteSets: List<String>? = null
        private var savedUserEmotes: List<TwitchEmote>? = null
        private var savedGlobalBadges: List<TwitchBadge>? = null
        private var savedGlobalStvEmotes: List<Emote>? = null
        private var savedGlobalBttvEmotes: List<Emote>? = null
        private var savedGlobalFfzEmotes: List<Emote>? = null
    }
}