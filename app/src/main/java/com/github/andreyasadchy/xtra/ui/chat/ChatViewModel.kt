package com.github.andreyasadchy.xtra.ui.chat

import android.content.ContentResolver
import android.content.Context
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
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
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.ChatReplayManager
import com.github.andreyasadchy.xtra.ui.player.ChatReplayManagerLocal
import com.github.andreyasadchy.xtra.ui.view.chat.ChatView
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.ChatListener
import com.github.andreyasadchy.xtra.util.chat.ChatReadIRC
import com.github.andreyasadchy.xtra.util.chat.ChatReadWebSocket
import com.github.andreyasadchy.xtra.util.chat.ChatUtils
import com.github.andreyasadchy.xtra.util.chat.ChatWriteIRC
import com.github.andreyasadchy.xtra.util.chat.ChatWriteWebSocket
import com.github.andreyasadchy.xtra.util.chat.EventSubListener
import com.github.andreyasadchy.xtra.util.chat.EventSubUtils
import com.github.andreyasadchy.xtra.util.chat.EventSubWebSocket
import com.github.andreyasadchy.xtra.util.chat.OnChatMessageReceivedListener
import com.github.andreyasadchy.xtra.util.chat.PlaybackMessage
import com.github.andreyasadchy.xtra.util.chat.PubSubListener
import com.github.andreyasadchy.xtra.util.chat.PubSubUtils
import com.github.andreyasadchy.xtra.util.chat.PubSubWebSocket
import com.github.andreyasadchy.xtra.util.chat.Raid
import com.github.andreyasadchy.xtra.util.chat.RecentMessageUtils
import com.github.andreyasadchy.xtra.util.chat.RoomState
import com.github.andreyasadchy.xtra.util.chat.StreamInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
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
    private val graphQLRepository: GraphQLRepository,
    private val playerRepository: PlayerRepository,
    private val okHttpClient: OkHttpClient) : ViewModel(), ChatView.ChatViewCallback {

    val integrity = MutableStateFlow<String?>(null)

    val recentEmotes by lazy { playerRepository.loadRecentEmotesFlow() }
    val hasRecentEmotes = MutableStateFlow(false)
    private val _userEmotes = MutableStateFlow<List<Emote>?>(null)
    val userEmotes: StateFlow<List<Emote>?> = _userEmotes
    private var loadedUserEmotes = false
    private val _localTwitchEmotes = MutableStateFlow<List<TwitchEmote>?>(null)
    val localTwitchEmotes: StateFlow<List<TwitchEmote>?> = _localTwitchEmotes
    private val _globalStvEmotes = MutableStateFlow<List<Emote>?>(null)
    val globalStvEmotes: StateFlow<List<Emote>?> = _globalStvEmotes
    private val _channelStvEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelStvEmotes: StateFlow<List<Emote>?> = _channelStvEmotes
    private val _globalBttvEmotes = MutableStateFlow<List<Emote>?>(null)
    val globalBttvEmotes: StateFlow<List<Emote>?> = _globalBttvEmotes
    private val _channelBttvEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelBttvEmotes: StateFlow<List<Emote>?> = _channelBttvEmotes
    private val _globalFfzEmotes = MutableStateFlow<List<Emote>?>(null)
    val globalFfzEmotes: StateFlow<List<Emote>?> = _globalFfzEmotes
    private val _channelFfzEmotes = MutableStateFlow<List<Emote>?>(null)
    val channelFfzEmotes: StateFlow<List<Emote>?> = _channelFfzEmotes
    private val _globalBadges = MutableStateFlow<List<TwitchBadge>?>(null)
    val globalBadges: StateFlow<List<TwitchBadge>?> = _globalBadges
    private val _channelBadges = MutableStateFlow<List<TwitchBadge>?>(null)
    val channelBadges: StateFlow<List<TwitchBadge>?> = _channelBadges
    private val _cheerEmotes = MutableStateFlow<List<CheerEmote>?>(null)
    val cheerEmotes: StateFlow<List<CheerEmote>?> = _cheerEmotes

    val roomState = MutableStateFlow<RoomState?>(null)
    val raid = MutableStateFlow<Raid?>(null)
    val raidClicked = MutableStateFlow(false)
    var raidClosed = false
    private val _streamInfo = MutableStateFlow<StreamInfo?>(null)
    val streamInfo: StateFlow<StreamInfo?> = _streamInfo
    private val _playbackMessage = MutableStateFlow<PlaybackMessage?>(null)
    val playbackMessage: StateFlow<PlaybackMessage?> = _playbackMessage
    var streamId: String? = null
    private val rewardList = mutableListOf<ChatMessage>()

    val reloadMessages = MutableStateFlow(false)
    val scrollDown = MutableStateFlow(false)
    val hideRaid = MutableStateFlow(false)

    private var messageLimit = 600
    private val _chatMessages = MutableStateFlow<MutableList<ChatMessage>>(Collections.synchronizedList(ArrayList(messageLimit + 1)))
    val chatMessages: StateFlow<MutableList<ChatMessage>> = _chatMessages
    val newMessage = MutableStateFlow(false)

    var chat: ChatController? = null

    val newChatter = MutableStateFlow<Chatter?>(null)

    val chatters: Collection<Chatter>?
        get() = (chat as? LiveChatController)?.chatters?.values

    fun startLive(useChatWebSocket: Boolean, useSSL: Boolean, usePubSub: Boolean, account: Account, isLoggedIn: Boolean, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, channelName: String?, streamId: String?, messageLimit: Int,emoteQuality: String, animateGifs: Boolean, showUserNotice: Boolean, showClearMsg: Boolean, showClearChat: Boolean, collectPoints: Boolean, notifyPoints: Boolean, showRaids: Boolean, enableRecentMsg: Boolean, recentMsgLimit: String, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean, useApiCommands: Boolean, useApiChatMessages: Boolean, useEventSubChat: Boolean) {
        if (chat == null && channelLogin != null) {
            this.messageLimit = messageLimit
            this.streamId = streamId
            chat = LiveChatController(useChatWebSocket, useSSL, usePubSub, account, isLoggedIn, helixHeaders, gqlHeaders, channelId, channelLogin, channelName, animateGifs, showUserNotice, showClearMsg, showClearChat, collectPoints, notifyPoints, showRaids, useApiCommands, useApiChatMessages, useEventSubChat, checkIntegrity)
            chat?.start()
            loadEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
            if (enableRecentMsg) {
                loadRecentMessages(channelLogin, recentMsgLimit, showUserNotice, showClearMsg, showClearChat)
            }
            if (isLoggedIn) {
                (chat as? LiveChatController)?.loadUserEmotes()
            }
        }
    }

    fun startReplay(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, localUri: String?, videoId: String?, startTime: Int, getCurrentPosition: () -> Long?, getCurrentSpeed: () -> Float?, messageLimit: Int,emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        if (chat == null) {
            this.messageLimit = messageLimit
            chat = VideoChatController(gqlHeaders, videoId, startTime, localUri, getCurrentPosition, getCurrentSpeed, helixHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
            chat?.start()
            if (videoId != null) {
                loadEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
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
        raidClicked.value = true
    }

    override fun onRaidClose() {
        raidClosed = true
    }

    override fun onCleared() {
        chat?.stop()
        super.onCleared()
    }

    private fun loadEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        savedGlobalBadges.also { saved ->
            if (!saved.isNullOrEmpty()) {
                _globalBadges.value = saved
                if (!reloadMessages.value) {
                    reloadMessages.value = true
                }
            } else {
                viewModelScope.launch {
                    try {
                        repository.loadGlobalBadges(helixHeaders, gqlHeaders, emoteQuality, checkIntegrity).let { badges ->
                            if (badges.isNotEmpty()) {
                                savedGlobalBadges = badges
                                _globalBadges.value = badges
                                if (!reloadMessages.value) {
                                    reloadMessages.value = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global badges", e)
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
                        }
                    }
                }
            }
        }
        if (enableStv) {
            savedGlobalStvEmotes.also { saved ->
                if (!saved.isNullOrEmpty()) {
                    (chat as? LiveChatController)?.addEmotes(saved)
                    _globalStvEmotes.value = saved
                    if (!reloadMessages.value) {
                        reloadMessages.value = true
                    }
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalStvEmotes().let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalStvEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    _globalStvEmotes.value = emotes
                                    if (!reloadMessages.value) {
                                        reloadMessages.value = true
                                    }
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
                        playerRepository.loadStvEmotes(channelId).let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                _channelStvEmotes.value = it
                                if (!reloadMessages.value) {
                                    reloadMessages.value = true
                                }
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
                    _globalBttvEmotes.value = saved
                    if (!reloadMessages.value) {
                        reloadMessages.value = true
                    }
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalBttvEmotes().let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalBttvEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    _globalBttvEmotes.value = emotes
                                    if (!reloadMessages.value) {
                                        reloadMessages.value = true
                                    }
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
                        playerRepository.loadBttvEmotes(channelId).let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                _channelBttvEmotes.value = it
                                if (!reloadMessages.value) {
                                    reloadMessages.value = true
                                }
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
                    _globalFfzEmotes.value = saved
                    if (!reloadMessages.value) {
                        reloadMessages.value = true
                    }
                } else {
                    viewModelScope.launch {
                        try {
                            playerRepository.loadGlobalFfzEmotes().let { emotes ->
                                if (emotes.isNotEmpty()) {
                                    savedGlobalFfzEmotes = emotes
                                    (chat as? LiveChatController)?.addEmotes(emotes)
                                    _globalFfzEmotes.value = emotes
                                    if (!reloadMessages.value) {
                                        reloadMessages.value = true
                                    }
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
                        playerRepository.loadFfzEmotes(channelId).let {
                            if (it.isNotEmpty()) {
                                (chat as? LiveChatController)?.addEmotes(it)
                                _channelFfzEmotes.value = it
                                if (!reloadMessages.value) {
                                    reloadMessages.value = true
                                }
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
                    repository.loadChannelBadges(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, checkIntegrity).let {
                        if (it.isNotEmpty()) {
                            _channelBadges.value = it
                            if (!reloadMessages.value) {
                                reloadMessages.value = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load badges for channel $channelId", e)
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
            viewModelScope.launch {
                try {
                    repository.loadCheerEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, animateGifs, checkIntegrity).let {
                        if (it.isNotEmpty()) {
                            _cheerEmotes.value = it
                            if (!reloadMessages.value) {
                                reloadMessages.value = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cheermotes for channel $channelId", e)
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun loadRecentMessages(channelLogin: String, recentMsgLimit: String, showUserNotice: Boolean, showClearMsg: Boolean, showClearChat: Boolean) {
        viewModelScope.launch {
            try {
                val list = mutableListOf<ChatMessage>()
                playerRepository.loadRecentMessages(channelLogin, recentMsgLimit).messages.forEach { message ->
                    when {
                        message.contains("PRIVMSG") -> RecentMessageUtils.parseChatMessage(message, false)
                        message.contains("USERNOTICE") -> {
                            if (showUserNotice) {
                                RecentMessageUtils.parseChatMessage(message, true)
                            } else null
                        }
                        message.contains("CLEARMSG") -> {
                            if (showClearMsg) {
                                RecentMessageUtils.parseClearMessage(applicationContext, message)
                            } else null
                        }
                        message.contains("CLEARCHAT") -> {
                            if (showClearChat) {
                                RecentMessageUtils.parseClearChat(applicationContext, message)
                            } else null
                        }
                        message.contains("NOTICE") -> RecentMessageUtils.parseNotice(applicationContext, message)
                        else -> null
                    }?.let { list.add(it) }
                }
                if (list.isNotEmpty()) {
                    _chatMessages.value.addAll(0, list)
                    if (!scrollDown.value) {
                        scrollDown.value = true
                    }
                    if (!reloadMessages.value) {
                        reloadMessages.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recent messages for channel $channelLogin", e)
            }
        }
    }

    fun reloadEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, animateGifs: Boolean, enableStv: Boolean, enableBttv: Boolean, enableFfz: Boolean, checkIntegrity: Boolean) {
        savedGlobalBadges = null
        savedGlobalStvEmotes = null
        savedGlobalBttvEmotes = null
        savedGlobalFfzEmotes = null
        loadEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
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

    fun loadRecentEmotes() {
        viewModelScope.launch {
            hasRecentEmotes.value = playerRepository.loadRecentEmotes().isNotEmpty()
        }
    }

    inner class LiveChatController(
        private val useChatWebSocket: Boolean,
        private val useSSL: Boolean,
        private val usePubSub: Boolean,
        private val account: Account,
        private val isLoggedIn: Boolean,
        private val helixHeaders: Map<String, String>,
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
        private val showRaids: Boolean,
        private val useApiCommands: Boolean,
        private val useApiChatMessages: Boolean,
        private val useEventSubChat: Boolean,
        private val checkIntegrity: Boolean) : ChatController(), ChatListener, PubSubListener, EventSubListener {

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
                newChatter.value = chatter
            }
        }

        override fun send(message: CharSequence) {
            if (useApiCommands) {
                if (message.toString().startsWith("/")) {
                    try {
                        sendCommand(message)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
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
            val helixToken = helixHeaders[C.HEADER_TOKEN]?.removePrefix("Bearer ")
            if (useEventSubChat && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                eventSub = EventSubWebSocket(okHttpClient, viewModelScope, this).apply { connect() }
            } else {
                if (useChatWebSocket) {
                    chatReadWebSocket = ChatReadWebSocket(isLoggedIn, channelLogin, okHttpClient, viewModelScope, this).apply { connect() }
                    if (isLoggedIn && (!gqlToken.isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && !useApiChatMessages)) {
                        chatWriteWebSocket = ChatWriteWebSocket(account.login, gqlToken?.takeIf { it.isNotBlank() } ?: helixToken, channelLogin, okHttpClient, viewModelScope, this).apply { connect() }
                    }
                } else {
                    chatReadIRC = ChatReadIRC(useSSL, isLoggedIn, channelLogin, this).apply { start() }
                    if (isLoggedIn && (!gqlToken.isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && !useApiChatMessages)) {
                        chatWriteIRC = ChatWriteIRC(useSSL, account.login, gqlToken?.takeIf { it.isNotBlank() } ?: helixToken, channelLogin, this).apply { start() }
                    }
                }
            }
            if (usePubSub && !channelId.isNullOrBlank()) {
                pubSub = PubSubWebSocket(channelId, account.id, gqlHeaders[C.HEADER_TOKEN]?.removePrefix("OAuth "), collectPoints, notifyPoints, showRaids, okHttpClient, viewModelScope, this).apply { connect() }
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

        private fun onChatMessage(message: ChatMessage) {
            onMessage(message)
            addChatter(message.userName)
        }

        override fun onConnect() {
            onMessage(ChatMessage(
                message = ContextCompat.getString(applicationContext, R.string.chat_join).format(channelLogin),
                color = "#999999",
                isAction = true,
            ))
        }

        override fun onDisconnect(message: String, fullMsg: String) {
            onMessage(ChatMessage(
                message = ContextCompat.getString(applicationContext, R.string.chat_disconnect).format(channelLogin, message),
                color = "#999999",
                isAction = true,
                fullMsg = fullMsg
            ))
        }

        override fun onSendMessageError(message: String, fullMsg: String) {
            onMessage(ChatMessage(
                message = ContextCompat.getString(applicationContext, R.string.chat_send_msg_error).format(message),
                color = "#999999",
                isAction = true,
                fullMsg = fullMsg
            ))
        }

        override fun onChatMessage(message: String, userNotice: Boolean) {
            if (!userNotice || showUserNotice) {
                val chatMessage = ChatUtils.parseChatMessage(message, userNotice)
                if (usePubSub && chatMessage.reward != null && !chatMessage.reward.id.isNullOrBlank()) {
                    onRewardMessage(chatMessage)
                } else {
                    onChatMessage(chatMessage)
                }
            }
        }

        override fun onClearMessage(message: String) {
            if (showClearMsg) {
                onMessage(ChatUtils.parseClearMessage(applicationContext, message))
            }
        }

        override fun onClearChat(message: String) {
            if (showClearChat) {
                onMessage(ChatUtils.parseClearChat(applicationContext, message))
            }
        }

        override fun onNotice(message: String) {
            val result = ChatUtils.parseNotice(applicationContext, message)
            onMessage(result.first)
            if (result.second) {
                if (!hideRaid.value) {
                    hideRaid.value = true
                }
            }
        }

        override fun onRoomState(message: String) {
            roomState.value = ChatUtils.parseRoomState(message)
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
                    _userEmotes.value = saved.sortedByDescending { it.ownerId == channelId }.map { Emote(
                        name = it.name,
                        url1x = it.url1x,
                        url2x = it.url2x,
                        url3x = it.url3x,
                        url4x = it.url4x,
                        format = it.format
                    ) }
                } else {
                    if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            try {
                                repository.loadUserEmotes(helixHeaders, gqlHeaders, channelId, account.id, animateGifs, checkIntegrity).let { emotes ->
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
                                        _userEmotes.value = sorted.sortedByDescending { it.ownerId == channelId }.map { Emote(
                                            name = it.name,
                                            url1x = it.url1x,
                                            url2x = it.url2x,
                                            url3x = it.url3x,
                                            url4x = it.url4x,
                                            format = it.format
                                        ) }
                                        loadedUserEmotes = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load user emotes", e)
                                if (e.message == "failed integrity check" && integrity.value == null) {
                                    integrity.value = "refresh"
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun loadEmoteSets() {
            if (!savedEmoteSets.isNullOrEmpty() && !helixHeaders[C.HEADER_CLIENT_ID].isNullOrBlank() && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val emotes = mutableListOf<TwitchEmote>()
                        savedEmoteSets?.chunked(25)?.forEach { list ->
                            repository.loadEmotesFromSet(helixHeaders, list, animateGifs).let { emotes.addAll(it) }
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
                            _userEmotes.value = sorted.sortedByDescending { it.ownerId == channelId }.map { Emote(
                                name = it.name,
                                url1x = it.url1x,
                                url2x = it.url2x,
                                url3x = it.url3x,
                                url4x = it.url4x,
                                format = it.format
                            ) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load emote sets", e)
                    }
                }
            }
        }

        override fun onUserState(message: String) {
            val emoteSets = ChatUtils.parseEmoteSets(message)
            if (emoteSets != null && savedEmoteSets != emoteSets) {
                savedEmoteSets = emoteSets
                if (!loadedUserEmotes) {
                    loadEmoteSets()
                }
            }
        }

        override fun onPlaybackMessage(message: JSONObject) {
            val playbackMessage = PubSubUtils.parsePlaybackMessage(message)
            if (playbackMessage != null) {
                playbackMessage.live?.let {
                    if (it) {
                        onMessage(ChatMessage(
                            message = ContextCompat.getString(applicationContext, R.string.stream_live).format(channelLogin),
                            color = "#999999",
                            isAction = true,
                        ))
                    } else {
                        onMessage(ChatMessage(
                            message = ContextCompat.getString(applicationContext, R.string.stream_offline).format(channelLogin),
                            color = "#999999",
                            isAction = true,
                        ))
                    }
                }
                _playbackMessage.value = playbackMessage
            }
        }

        override fun onStreamInfo(message: JSONObject) {
            _streamInfo.value = PubSubUtils.parseStreamInfo(message)
        }

        private fun onRewardMessage(message: ChatMessage) {
            if (message.reward?.id != null) {
                val item = rewardList.find { it.reward?.id == message.reward.id && it.userId == message.userId }
                if (item != null) {
                    rewardList.remove(item)
                    onChatMessage(ChatMessage(
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
                onChatMessage(message)
            }
        }

        override fun onRewardMessage(message: JSONObject) {
            val chatMessage = PubSubUtils.parseRewardMessage(message)
            if (!chatMessage.message.isNullOrBlank()) {
                onRewardMessage(chatMessage)
            } else {
                onChatMessage(chatMessage)
            }
        }

        override fun onPointsEarned(message: JSONObject) {
            val points = PubSubUtils.parsePointsEarned(message)
            onMessage(ChatMessage(
                message = ContextCompat.getString(applicationContext, R.string.points_earned).format(points.pointsGained),
                color = "#999999",
                isAction = true,
                timestamp = points.timestamp,
                fullMsg = points.fullMsg
            ))
        }

        override fun onClaimAvailable() {
            if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val response = graphQLRepository.loadChannelPointsContext(gqlHeaders, channelLogin)
                        response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                        response.data?.community?.channel?.self?.communityPoints?.availableClaim?.id?.let { claimId ->
                            graphQLRepository.loadClaimPoints(gqlHeaders, channelId, claimId).errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
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

        override fun onRaidUpdate(message: JSONObject, openStream: Boolean) {
            PubSubUtils.onRaidUpdate(message, openStream)?.let {
                if (it.raidId != usedRaidId) {
                    usedRaidId = it.raidId
                    raidClosed = false
                    if (collectPoints && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            try {
                                repository.loadJoinRaid(gqlHeaders, it.raidId)
                            } catch (e: Exception) {
                                if (e.message == "failed integrity check" && integrity.value == null) {
                                    integrity.value = "refresh"
                                }
                            }
                        }
                    }
                }
                raid.value = it
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
                    repository.createChatEventSubSubscription(helixHeaders, account.id, channelId, it, sessionId)?.let {
                        onMessage(ChatMessage(message = it, color = "#999999", isAction = true))
                    }
                }
            }
        }

        override fun onChatMessage(json: JSONObject, timestamp: String?) {
            val chatMessage = EventSubUtils.parseChatMessage(json, timestamp)
            if (usePubSub && chatMessage.reward != null && !chatMessage.reward.id.isNullOrBlank()) {
                onRewardMessage(chatMessage)
            } else {
                onChatMessage(chatMessage)
            }
        }

        override fun onUserNotice(json: JSONObject, timestamp: String?) {
            if (showUserNotice) {
                onChatMessage(EventSubUtils.parseUserNotice(json, timestamp))
            }
        }

        override fun onClearChat(json: JSONObject, timestamp: String?) {
            if (showClearChat) {
                onMessage(EventSubUtils.parseClearChat(applicationContext, json, timestamp))
            }
        }

        override fun onRoomState(json: JSONObject, timestamp: String?) {
            roomState.value = EventSubUtils.parseRoomState(json)
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
                _chatMessages.value = arrayListOf(
                    ChatMessage(
                        message = ContextCompat.getString(applicationContext, R.string.disconnected),
                        color = "#999999",
                        isAction = true,
                    )
                )
                if (!hideRaid.value) {
                    hideRaid.value = true
                }
                roomState.value = RoomState("0", "-1", "0", "0", "0")
            }
        }

        private fun sendMessage(message: CharSequence) {
            if (useApiChatMessages && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                viewModelScope.launch {
                    repository.sendMessage(helixHeaders, account.id, channelId, message.toString())?.let {
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/clear", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.deleteMessages(
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                color = splits[1]
                            )
                        } else {
                            repository.getChatColor(
                                helixHeaders = helixHeaders,
                                userId = account.id
                            )
                        }?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/commercial", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val splits = message.split(" ")
                        if (splits.size >= 2) {
                            viewModelScope.launch {
                                repository.startCommercial(
                                    helixHeaders = helixHeaders,
                                    channelId = channelId,
                                    length = splits[1]
                                )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                            }
                        }
                    } else sendMessage(message)
                }
                command.equals("/delete", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val splits = message.split(" ")
                        if (splits.size >= 2) {
                            viewModelScope.launch {
                                repository.deleteMessages(
                                    helixHeaders = helixHeaders,
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
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                emote = true
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/emoteonlyoff", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                emote = false
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/followers", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val splits = message.split(" ")
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                followers = true,
                                followersDuration = if (splits.size >= 2) splits[1] else null
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/followersoff", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
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
                            helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                            helixHeaders = helixHeaders,
                            gqlHeaders = gqlHeaders,
                            channelId = channelId
                        )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                    }
                }
                command.equals("/slow", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        val splits = message.split(" ")
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                slow = true,
                                slowDuration = if (splits.size >= 2) splits[1].toIntOrNull() else null
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/slowoff", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                slow = false
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/subscribers", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                subs = true,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/subscribersoff", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
                                userId = account.id,
                                gqlHeaders = gqlHeaders,
                                channelId = channelId,
                                targetLogin = splits[1]
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    }
                }
                command.equals("/uniquechat", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
                                channelId = channelId,
                                userId = account.id,
                                unique = true,
                            )?.let { onMessage(ChatMessage(message = it, color = "#999999", isAction = true)) }
                        }
                    } else sendMessage(message)
                }
                command.equals("/uniquechatoff", true) -> {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.updateChatSettings(
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
                                helixHeaders = helixHeaders,
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
        private val helixHeaders: Map<String, String>,
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
                    chatReplayManager = ChatReplayManager(gqlHeaders, repository, videoId, startTime, getCurrentPosition, getCurrentSpeed, this, { _chatMessages.value = ArrayList() }, { if (integrity.value == null) integrity.value = "refresh" }, viewModelScope).apply { start() }
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
                    val messages = mutableListOf<ChatMessage>()
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
                            reader.isLenient = true
                            var position = 0L
                            var token: JsonToken
                            do {
                                token = reader.peek()
                                when (token) {
                                    JsonToken.END_DOCUMENT -> {}
                                    JsonToken.BEGIN_OBJECT -> {
                                        reader.beginObject().also { position += 1 }
                                        while (reader.hasNext()) {
                                            when (reader.peek()) {
                                                JsonToken.NAME -> {
                                                    when (reader.nextName().also { position += it.length + 3 }) {
                                                        "liveStartTime" -> { TwitchApiHelper.parseIso8601DateUTC(reader.nextString().also { position += it.length + 2 })?.let { startTimeMs = it } }
                                                        "liveComments" -> {
                                                            reader.beginArray().also { position += 1 }
                                                            while (reader.hasNext()) {
                                                                val message = reader.nextString().also { position += it.length + 2 + it.count { c -> c == '"' || c == '\\' } }
                                                                when {
                                                                    message.contains("PRIVMSG") -> messages.add(ChatUtils.parseChatMessage(message, false))
                                                                    message.contains("USERNOTICE") -> messages.add(ChatUtils.parseChatMessage(message, true))
                                                                    message.contains("CLEARMSG") -> messages.add(ChatUtils.parseClearMessage(applicationContext, message))
                                                                    message.contains("CLEARCHAT") -> messages.add(ChatUtils.parseClearChat(applicationContext, message))
                                                                }
                                                                if (reader.peek() != JsonToken.END_ARRAY) {
                                                                    position += 1
                                                                }
                                                            }
                                                            reader.endArray().also { position += 1 }
                                                        }
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
                                                                            messages.add(ChatMessage(
                                                                                id = id,
                                                                                userId = userId,
                                                                                userLogin = userLogin,
                                                                                userName = userName,
                                                                                message = message.toString(),
                                                                                color = color,
                                                                                emotes = emotesList,
                                                                                badges = badgesList,
                                                                                bits = 0,
                                                                                timestamp = offsetSeconds?.times(1000L),
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
                                    else -> position += skipJsonValue(reader)
                                }
                            } while (token != JsonToken.END_DOCUMENT)
                        }
                    }
                    _localTwitchEmotes.value = twitchEmotes
                    _channelBadges.value = twitchBadges
                    _cheerEmotes.value = cheerEmotesList
                    _channelStvEmotes.value = emotes
                    if (emotes.isEmpty()) {
                        viewModelScope.launch {
                            loadEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
                        }
                    }
                    if (messages.isNotEmpty()) {
                        viewModelScope.launch {
                            chatReplayManagerLocal = ChatReplayManagerLocal(messages, startTimeMs, getCurrentPosition, getCurrentSpeed, this@VideoChatController, { _chatMessages.value = ArrayList() }, viewModelScope).apply { start() }
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
            _chatMessages.value.add(message)
            newMessage.value = true
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