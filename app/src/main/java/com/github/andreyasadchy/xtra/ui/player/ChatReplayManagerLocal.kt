package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.VideoChatMessage
import com.github.andreyasadchy.xtra.util.chat.OnChatMessageReceivedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.max

class ChatReplayManagerLocal @Inject constructor(
    private val messages: List<VideoChatMessage>,
    private val startTime: Long,
    private val getCurrentPosition: () -> Long?,
    private val getCurrentSpeed: () -> Float?,
    private val messageListener: OnChatMessageReceivedListener,
    private val clearMessages: () -> Unit,
    private val coroutineScope: CoroutineScope) {

    private val list = mutableListOf<VideoChatMessage>()
    private var isLoading = false
    private var loadJob: Job? = null
    private var messageJob: Job? = null
    private var lastCheckedPosition = 0L
    private var playbackSpeed: Float? = null

    fun start() {
        val currentPosition = getCurrentPosition() ?: 0
        lastCheckedPosition = currentPosition
        playbackSpeed = getCurrentSpeed()
        list.clear()
        clearMessages()
        load(currentPosition + startTime)
    }

    fun stop() {
        loadJob?.cancel()
        messageJob?.cancel()
    }

    private fun load(position: Long) {
        isLoading = true
        loadJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                messageJob?.cancel()
                list.addAll(messages.filter { it.offsetSeconds != null && it.offsetSeconds >= (max((position / 1000) - 20, 0)) })
                isLoading = false
                startJob()
            } catch (e: Exception) {

            }
        }
    }

    private fun startJob() {
        messageJob = coroutineScope.launch(Dispatchers.IO) {
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
                load(position + startTime)
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