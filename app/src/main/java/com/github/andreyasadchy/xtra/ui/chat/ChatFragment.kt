package com.github.andreyasadchy.xtra.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChatBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.stream.StreamPlayerFragment
import com.github.andreyasadchy.xtra.ui.view.chat.MessageClickedChatAdapter
import com.github.andreyasadchy.xtra.ui.view.chat.MessageClickedDialog
import com.github.andreyasadchy.xtra.ui.view.chat.ReplyClickedChatAdapter
import com.github.andreyasadchy.xtra.ui.view.chat.ReplyClickedDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.LifecycleListener
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.Raid
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : BaseNetworkFragment(), LifecycleListener, MessageClickedDialog.OnButtonClickListener, ReplyClickedDialog.OnButtonClickListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collectLatest {
                    if (it != null && it != "done" && requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                        IntegrityDialog.show(childFragmentManager, it)
                        viewModel.integrity.value = "done"
                    }
                }
            }
        }
        with(binding) {
            val args = requireArguments()
            val channelId = args.getString(KEY_CHANNEL_ID)
            val isLive = args.getBoolean(KEY_IS_LIVE)
            val account = Account.get(requireContext())
            val isLoggedIn = !account.login.isNullOrBlank() && (!TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() || !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank())
            val chatUrl = args.getString(KEY_CHAT_URL)
            val enableChat = when {
                requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) -> false
                isLive -> {
                    chatView.init(
                        fragment = this@ChatFragment,
                        channelId = channelId,
                        namePaints = viewModel.namePaints,
                        paintUsers = viewModel.paintUsers
                    )
                    chatView.setCallback(viewModel)
                    if (isLoggedIn) {
                        chatView.setUsername(account.login)
                        chatView.addToAutoCompleteList(viewModel.chatters)
                        viewModel.loadRecentEmotes()
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.hasRecentEmotes.collectLatest {
                                    if (it) {
                                        chatView.setRecentEmotes()
                                    }
                                }
                            }
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.userEmotes.collectLatest {
                                    chatView.addToAutoCompleteList(it)
                                }
                            }
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.newChatter.collectLatest {
                                    if (it != null) {
                                        chatView.addToAutoCompleteList(listOf(it))
                                        viewModel.newChatter.value = null
                                    }
                                }
                            }
                        }
                    }
                    true
                }
                chatUrl != null || (args.getString(KEY_VIDEO_ID) != null && !args.getBoolean(KEY_START_TIME_EMPTY)) -> {
                    chatView.init(
                        fragment = this@ChatFragment,
                        channelId = channelId,
                        getEmoteBytes = viewModel::getEmoteBytes,
                        chatUrl = chatUrl
                    )
                    true
                }
                else -> {
                    requireView().findViewById<TextView>(R.id.chatReplayUnavailable)?.visible()
                    false
                }
            }
            if (enableChat) {
                chatView.enableChatInteraction(isLive && isLoggedIn)
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.chatMessages.collect {
                            chatView.submitList(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.newMessage.collect {
                            if (it != null) {
                                chatView.notifyMessageAdded(it)
                                viewModel.newMessage.value = null
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.localTwitchEmotes.collectLatest {
                            chatView.addLocalTwitchEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.globalStvEmotes.collectLatest {
                            chatView.addGlobalStvEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.channelStvEmotes.collectLatest {
                            chatView.addChannelStvEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.globalBttvEmotes.collectLatest {
                            chatView.addGlobalBttvEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.channelBttvEmotes.collectLatest {
                            chatView.addChannelBttvEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.globalFfzEmotes.collectLatest {
                            chatView.addGlobalFfzEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.channelFfzEmotes.collectLatest {
                            chatView.addChannelFfzEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.globalBadges.collectLatest {
                            chatView.addGlobalBadges(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.channelBadges.collectLatest {
                            chatView.addChannelBadges(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.cheerEmotes.collectLatest {
                            chatView.addCheerEmotes(it)
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.roomState.collectLatest {
                            if (it != null) {
                                chatView.notifyRoomState(it)
                                viewModel.roomState.value = null
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.reloadMessages.collectLatest {
                            if (it) {
                                chatView.notifyEmotesLoaded()
                                viewModel.reloadMessages.value = false
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.scrollDown.collectLatest {
                            if (it) {
                                chatView.scrollToLastPosition()
                                viewModel.scrollDown.value = false
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.hideRaid.collectLatest {
                            if (it) {
                                chatView.hideRaid()
                                viewModel.hideRaid.value = false
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.raid.collectLatest {
                            if (it != null) {
                                onRaidUpdate(it)
                                viewModel.raid.value = null
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.raidClicked.collectLatest {
                            if (it != null) {
                                onRaidClicked(it)
                                viewModel.raidClicked.value = null
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.playbackMessage.collectLatest {
                            if (it != null) {
                                if (it.live != null) {
                                    (parentFragment as? StreamPlayerFragment)?.updateLiveStatus(it, args.getString(KEY_CHANNEL_LOGIN))
                                }
                                (parentFragment as? StreamPlayerFragment)?.updateViewerCount(it.viewers)
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.streamInfo.collectLatest {
                            if (it != null) {
                                (parentFragment as? StreamPlayerFragment)?.updateStreamInfo(it.title, it.gameId, null, it.gameName)
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.newPaint.collectLatest {
                            if (it != null) {
                                chatView.addPaint(it)
                                viewModel.newPaint.value = null
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.newPaintUser.collectLatest {
                            if (it != null) {
                                chatView.addPaintUser(it)
                                viewModel.newPaintUser.value = null
                            }
                        }
                    }
                }
            }
            if (chatUrl != null) {
                initialize()
            }
        }
    }

    override fun initialize() {
        val args = requireArguments()
        val channelId = args.getString(KEY_CHANNEL_ID)
        val channelLogin = args.getString(KEY_CHANNEL_LOGIN)
        val channelName = args.getString(KEY_CHANNEL_NAME)
        val account = Account.get(requireContext())
        val helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext())
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), true)
        val isLoggedIn = !account.login.isNullOrBlank() && (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank())
        val messageLimit = requireContext().prefs().getInt(C.CHAT_LIMIT, 600)
        val useChatWebSocket = requireContext().prefs().getBoolean(C.CHAT_USE_WEBSOCKET, false)
        val useSSL = requireContext().prefs().getBoolean(C.CHAT_USE_SSL, true)
        val usePubSub = requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true)
        val showNamePaints = requireContext().prefs().getBoolean(C.CHAT_SHOW_PAINTS, false)
        val emoteQuality =  requireContext().prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"
        val animateGifs =  requireContext().prefs().getBoolean(C.ANIMATED_EMOTES, true)
        val showUserNotice = requireContext().prefs().getBoolean(C.CHAT_SHOW_USERNOTICE, true)
        val showClearMsg = requireContext().prefs().getBoolean(C.CHAT_SHOW_CLEARMSG, true)
        val showClearChat = requireContext().prefs().getBoolean(C.CHAT_SHOW_CLEARCHAT, true)
        val collectPoints = requireContext().prefs().getBoolean(C.CHAT_POINTS_COLLECT, true)
        val notifyPoints = requireContext().prefs().getBoolean(C.CHAT_POINTS_NOTIFY, false)
        val showRaids = requireContext().prefs().getBoolean(C.CHAT_RAIDS_SHOW, true)
        val enableRecentMsg = requireContext().prefs().getBoolean(C.CHAT_RECENT, true)
        val recentMsgLimit = requireContext().prefs().getInt(C.CHAT_RECENT_LIMIT, 100)
        val enableStv = requireContext().prefs().getBoolean(C.CHAT_ENABLE_STV, true)
        val enableBttv = requireContext().prefs().getBoolean(C.CHAT_ENABLE_BTTV, true)
        val enableFfz = requireContext().prefs().getBoolean(C.CHAT_ENABLE_FFZ, true)
        val nameDisplay = requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")
        val checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
        val useApiCommands = requireContext().prefs().getBoolean(C.DEBUG_API_COMMANDS, true)
        val useApiChatMessages = requireContext().prefs().getBoolean(C.DEBUG_API_CHAT_MESSAGES, false)
        val useEventSubChat = requireContext().prefs().getBoolean(C.DEBUG_EVENTSUB_CHAT, false)
        val disableChat = requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)
        val isLive = args.getBoolean(KEY_IS_LIVE)
        if (!disableChat) {
            if (isLive) {
                val streamId = args.getString(KEY_STREAM_ID)
                viewModel.startLive(useChatWebSocket, useSSL, usePubSub, account, isLoggedIn, showNamePaints, helixHeaders, gqlHeaders, channelId, channelLogin, channelName, streamId, messageLimit, emoteQuality, animateGifs, showUserNotice, showClearMsg, showClearChat, collectPoints, notifyPoints, showRaids, enableRecentMsg, recentMsgLimit.toString(), enableStv, enableBttv, enableFfz, nameDisplay, checkIntegrity, useApiCommands, useApiChatMessages, useEventSubChat)
            } else {
                val chatUrl = args.getString(KEY_CHAT_URL)
                val videoId = args.getString(KEY_VIDEO_ID)
                if (chatUrl != null || (videoId != null && !args.getBoolean(KEY_START_TIME_EMPTY))) {
                    val startTime = args.getInt(KEY_START_TIME)
                    val getCurrentPosition = (parentFragment as BasePlayerFragment)::getCurrentPosition
                    val getCurrentSpeed = (parentFragment as BasePlayerFragment)::getCurrentSpeed
                    viewModel.startReplay(helixHeaders, gqlHeaders, channelId, channelLogin, chatUrl, videoId, startTime, getCurrentPosition, getCurrentSpeed, messageLimit, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, nameDisplay, checkIntegrity)
                }
            }
        }
    }

    fun isActive(): Boolean? {
        return (viewModel.chat as? ChatViewModel.LiveChatController)?.isActive()
    }

    fun disconnect() {
        (viewModel.chat as? ChatViewModel.LiveChatController)?.disconnect()
    }

    fun reconnect() {
        (viewModel.chat as? ChatViewModel.LiveChatController)?.start()
        val channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN)
        val enableRecentMsg = requireContext().prefs().getBoolean(C.CHAT_RECENT, true)
        val recentMsgLimit = requireContext().prefs().getInt(C.CHAT_RECENT_LIMIT, 100)
        val showUserNotice = requireContext().prefs().getBoolean(C.CHAT_SHOW_USERNOTICE, true)
        val showClearMsg = requireContext().prefs().getBoolean(C.CHAT_SHOW_CLEARMSG, true)
        val showClearChat = requireContext().prefs().getBoolean(C.CHAT_SHOW_CLEARCHAT, true)
        val nameDisplay = requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")
        if (channelLogin != null && enableRecentMsg) {
            viewModel.loadRecentMessages(channelLogin, recentMsgLimit.toString(), showUserNotice, showClearMsg, showClearChat, nameDisplay)
        }
    }

    fun reloadEmotes() {
        val channelId = requireArguments().getString(KEY_CHANNEL_ID)
        val channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN)
        val helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext())
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext())
        val emoteQuality =  requireContext().prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"
        val animateGifs =  requireContext().prefs().getBoolean(C.ANIMATED_EMOTES, true)
        val enableStv = requireContext().prefs().getBoolean(C.CHAT_ENABLE_STV, true)
        val enableBttv = requireContext().prefs().getBoolean(C.CHAT_ENABLE_BTTV, true)
        val enableFfz = requireContext().prefs().getBoolean(C.CHAT_ENABLE_FFZ, true)
        val checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
        viewModel.reloadEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, animateGifs, enableStv, enableBttv, enableFfz, checkIntegrity)
    }

    fun updatePosition(position: Long) {
        (viewModel.chat as? ChatViewModel.VideoChatController)?.updatePosition(position)
    }

    fun updateSpeed(speed: Float) {
        (viewModel.chat as? ChatViewModel.VideoChatController)?.updateSpeed(speed)
    }

    fun updateStreamId(id: String?) {
        viewModel.streamId = id
    }

    private fun onRaidUpdate(raid: Raid) {
        if (!viewModel.raidClosed) {
            if (raid.openStream) {
                if (requireContext().prefs().getBoolean(C.CHAT_RAIDS_AUTO_SWITCH, true) && parentFragment is BasePlayerFragment) {
                    onRaidClicked(raid)
                }
                binding.chatView.hideRaid()
            } else {
                binding.chatView.notifyRaid(raid)
            }
        }
    }

    private fun onRaidClicked(raid: Raid) {
        (requireActivity() as MainActivity).startStream(Stream(
            channelId = raid.targetId,
            channelLogin = raid.targetLogin,
            channelName = raid.targetName,
            profileImageUrl = raid.targetProfileImage,
        ))
    }

    override fun onCreateMessageClickedChatAdapter(): MessageClickedChatAdapter {
        return binding.chatView.createMessageClickedChatAdapter(viewModel.chatMessages.value.toList())
    }

    override fun onCreateReplyClickedChatAdapter(): ReplyClickedChatAdapter {
        return binding.chatView.createReplyClickedChatAdapter(viewModel.chatMessages.value.toList())
    }

    fun emoteMenuIsVisible() = binding.chatView.emoteMenuIsVisible()

    fun toggleEmoteMenu(enable: Boolean) = binding.chatView.toggleEmoteMenu(enable)

    fun toggleBackPressedCallback(enable: Boolean) = binding.chatView.toggleBackPressedCallback(enable)

    fun appendEmote(emote: Emote) {
        binding.chatView.appendEmote(emote)
    }

    override fun onReplyClicked(replyId: String?, userLogin: String?, userName: String?, message: String?) {
        val replyMessage = message?.let {
            val name = if (userName != null && userLogin != null && !userLogin.equals(userName, true)) {
                when (requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                    "0" -> "${userName}(${userLogin})"
                    "1" -> userName
                    else -> userLogin
                }
            } else {
                userName ?: userLogin
            }
            requireContext().getString(R.string.replying_to_message, name, message)
        }
        binding.chatView.reply(replyId, replyMessage)
    }

    override fun onCopyMessageClicked(message: String) {
        binding.chatView.setMessage(message)
    }

    override fun onViewProfileClicked(id: String?, login: String?, name: String?, channelLogo: String?) {
        findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
            channelId = id,
            channelLogin = login,
            channelName = name,
            channelLogo = channelLogo
        ))
        (parentFragment as? BasePlayerFragment)?.minimize()
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            viewModel.start()
        }
    }

    override fun onMovedToBackground() {
        if (!requireArguments().getBoolean(KEY_IS_LIVE) || !requireContext().prefs().getBoolean(C.PLAYER_KEEP_CHAT_OPEN, false) || requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
            viewModel.stop()
        }
    }

    override fun onMovedToForeground() {
        if (!requireArguments().getBoolean(KEY_IS_LIVE) || !requireContext().prefs().getBoolean(C.PLAYER_KEEP_CHAT_OPEN, false) || requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
            viewModel.start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_IS_LIVE = "isLive"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_LOGIN = "channel_login"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_STREAM_ID = "streamId"
        private const val KEY_VIDEO_ID = "videoId"
        private const val KEY_CHAT_URL = "chatUrl"
        private const val KEY_START_TIME_EMPTY = "startTime_empty"
        private const val KEY_START_TIME = "startTime"

        fun newInstance(channelId: String?, channelLogin: String?, channelName: String?, streamId: String?) = ChatFragment().apply {
            arguments = Bundle().apply {
                putBoolean(KEY_IS_LIVE, true)
                putString(KEY_CHANNEL_ID, channelId)
                putString(KEY_CHANNEL_LOGIN, channelLogin)
                putString(KEY_CHANNEL_NAME, channelName)
                putString(KEY_STREAM_ID, streamId)
            }
        }

        fun newInstance(channelId: String?, channelLogin: String?, videoId: String?, startTime: Int?) = ChatFragment().apply {
            arguments = Bundle().apply {
                putBoolean(KEY_IS_LIVE, false)
                putString(KEY_CHANNEL_ID, channelId)
                putString(KEY_CHANNEL_LOGIN, channelLogin)
                putString(KEY_VIDEO_ID, videoId)
                if (startTime != null) {
                    putBoolean(KEY_START_TIME_EMPTY, false)
                    putInt(KEY_START_TIME, startTime)
                } else {
                    putBoolean(KEY_START_TIME_EMPTY, true)
                }
            }
        }

        fun newLocalInstance(channelId: String?, channelLogin: String?, chatUrl: String?) = ChatFragment().apply {
            arguments = Bundle().apply {
                putString(KEY_CHANNEL_ID, channelId)
                putString(KEY_CHANNEL_LOGIN, channelLogin)
                putString(KEY_CHAT_URL, chatUrl)
            }
        }
    }
}