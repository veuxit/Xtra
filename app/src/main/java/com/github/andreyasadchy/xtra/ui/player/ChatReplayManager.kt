package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.chat.VideoChatMessage
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.util.chat.OnChatMessageReceivedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.max

class ChatReplayManager @Inject constructor(
    private val gqlHeaders: Map<String, String>,
    private val repository: ApiRepository,
    private val videoId: String,
    private val startTimeSeconds: Int,
    private val getCurrentPosition: () -> Long?,
    private val getCurrentSpeed: () -> Float?,
    private val messageListener: OnChatMessageReceivedListener,
    private val clearMessages: () -> Unit,
    private val getIntegrityToken: () -> Unit,
    private val coroutineScope: CoroutineScope) {

    private var cursor: String? = null
    private val list = mutableListOf<VideoChatMessage>()
    private var isLoading = false
    private var loadJob: Job? = null
    private var messageJob: Job? = null
    private var startTime = 0
    private var lastCheckedPosition = 0L
    private var playbackSpeed: Float? = null

    fun start() {
        startTime = startTimeSeconds.times(1000)
        val currentPosition = getCurrentPosition() ?: 0
        lastCheckedPosition = currentPosition
        playbackSpeed = getCurrentSpeed()
        list.clear()
        clearMessages()
        load(currentPosition.div(1000).toInt() + startTimeSeconds)
    }

    fun stop() {
        loadJob?.cancel()
        messageJob?.cancel()
    }

    private fun load(offsetSeconds: Int? = null) {
        isLoading = true
        loadJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = if (offsetSeconds != null) {
                    repository.loadVideoMessages(gqlHeaders, videoId, offset = offsetSeconds)
                } else {
                    repository.loadVideoMessages(gqlHeaders, videoId, cursor = cursor)
                }
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                val comments = response.data!!.video.comments
                val messages = comments.edges.mapNotNull { comment ->
                    comment.node.let { item ->
                        item.message?.let { message ->
                            val chatMessage = StringBuilder()
                            val emotes = message.fragments?.mapNotNull { fragment ->
                                fragment.text?.let { text ->
                                    fragment.emote?.emoteID?.let { id ->
                                        TwitchEmote(
                                            id = id,
                                            begin = chatMessage.codePointCount(0, chatMessage.length),
                                            end = chatMessage.codePointCount(0, chatMessage.length) + text.lastIndex
                                        )
                                    }.also { chatMessage.append(text) }
                                }
                            }
                            val badges = message.userBadges?.mapNotNull { badge ->
                                badge.setID?.let { setId ->
                                    badge.version?.let { version ->
                                        Badge(
                                            setId = setId,
                                            version = version,
                                        )
                                    }
                                }
                            }
                            VideoChatMessage(
                                id = item.id,
                                offsetSeconds = item.contentOffsetSeconds,
                                userId = item.commenter?.id,
                                userLogin = item.commenter?.login,
                                userName = item.commenter?.displayName,
                                message = chatMessage.toString(),
                                color = message.userColor,
                                emotes = emotes,
                                badges = badges,
                                fullMsg = item.toString()
                            )
                        }
                    }
                }
                messageJob?.cancel()
                list.addAll(messages)
                cursor = if (comments.pageInfo?.hasNextPage != false) comments.edges.lastOrNull()?.cursor else null
                isLoading = false
                startJob()
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    getIntegrityToken()
                }
            }
        }
    }

    private fun startJob() {
        coroutineScope.launch(Dispatchers.IO) {
            messageJob?.join()
            messageJob = this as Job
            while (isActive) {
                val message = list.firstOrNull() ?: break
                if (message.offsetSeconds != null) {
                    var currentPosition: Long
                    val messageOffset = message.offsetSeconds.times(1000)
                    while (((runBlocking(Dispatchers.Main) { getCurrentPosition() } ?: 0).also { lastCheckedPosition = it } + startTime).also { currentPosition = it } < messageOffset) {
                        delay(max((messageOffset - currentPosition).div(playbackSpeed ?: 1f).toLong(), 0))
                    }
                    messageListener.onMessage(ChatMessage(
                        id = message.id,
                        userId = message.userId,
                        userLogin = message.userLogin,
                        userName = message.userName,
                        message = message.message,
                        color = message.color,
                        emotes = message.emotes,
                        badges = message.badges,
                        bits = 0,
                        fullMsg = message.fullMsg
                    ))
                    if (list.size <= 25 && !cursor.isNullOrBlank() && !isLoading) {
                        load()
                    }
                }
                list.remove(message)
            }
        }
    }

    fun updatePosition(position: Long) {
        if (lastCheckedPosition != position) {
            if (position - lastCheckedPosition !in 0..20000) {
                loadJob?.cancel()
                messageJob?.cancel()
                list.clear()
                clearMessages()
                load(position.div(1000).toInt() + startTimeSeconds)
            } else {
                messageJob?.cancel()
                startJob()
            }
            lastCheckedPosition = position
        }
    }

    fun updateSpeed(speed: Float) {
        if (playbackSpeed != speed) {
            playbackSpeed = speed
            messageJob?.cancel()
            startJob()
        }
    }
}