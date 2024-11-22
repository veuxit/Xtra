package com.github.andreyasadchy.xtra.ui.player.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerStreamBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlaybackService
import com.github.andreyasadchy.xtra.ui.player.PlayerMode
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FragmentUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.PlaybackMessage
import com.github.andreyasadchy.xtra.util.enable
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.isNetworkAvailable
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.visible
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

@AndroidEntryPoint
class StreamPlayerFragment : BasePlayerFragment(), HasDownloadDialog {

    private var _binding: FragmentPlayerStreamBinding? = null
    private val binding get() = _binding!!
    override val viewModel: StreamPlayerViewModel by viewModels()
    private lateinit var item: Stream

    override val controllerAutoShow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_STREAM, Stream::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_STREAM)!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerStreamBinding.inflate(inflater, container, false).also {
            (it.slidingLayout as LinearLayout).orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = requireView().findViewById<ImageButton>(R.id.playerSettings)
        val download = requireView().findViewById<ImageButton>(R.id.playerDownload)
        val mode = requireView().findViewById<ImageButton>(R.id.playerMode)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loaded.collectLatest {
                    if (it) {
                        settings?.enable()
                        download?.enable()
                        mode?.enable()
                        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.let { setQualityText() }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stream.collectLatest {
                    if (it != null) {
                        chatFragment?.updateStreamId(it.id)
                        if (prefs.getBoolean(C.CHAT_DISABLE, false) || !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) || requireView().findViewById<TextView>(R.id.playerViewersText)?.text.isNullOrBlank()) {
                            updateViewerCount(it.viewerCount)
                        }
                        if (prefs.getBoolean(C.CHAT_DISABLE, false) || !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                            requireView().findViewById<TextView>(R.id.playerTitle)?.text.isNullOrBlank() ||
                            requireView().findViewById<TextView>(R.id.playerCategory)?.text.isNullOrBlank()) {
                            updateStreamInfo(it.title, it.gameId, it.gameSlug, it.gameName)
                        }
                        if (prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true) && requireView().findViewById<LinearLayout>(R.id.playerUptime)?.isVisible == false) {
                            it.startedAt?.let { date ->
                                TwitchApiHelper.parseIso8601DateUTC(date)?.let { startedAtMs ->
                                    updateUptime(startedAtMs)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_MENU, true)) {
            requireView().findViewById<ImageButton>(R.id.playerMenu)?.apply {
                visible()
                setOnClickListener {
                    FragmentUtils.showPlayerSettingsDialog(
                        fragmentManager = childFragmentManager,
                    )
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_DOWNLOAD, false)) {
            download?.apply {
                visible()
                setOnClickListener { showDownloadDialog() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_RESTART, true)) {
            requireView().findViewById<ImageButton>(R.id.playerRestart)?.apply {
                visible()
                setOnClickListener { restartPlayer() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_SEEKLIVE, false)) {
            requireView().findViewById<ImageButton>(R.id.playerSeekLive)?.apply {
                visible()
                setOnClickListener { player?.seekToDefaultPosition() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_VIEWERLIST, false)) {
            requireView().findViewById<LinearLayout>(R.id.playerViewers)?.apply {
                setOnClickListener { openViewerList() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_CHANNEL, true)) {
            requireView().findViewById<TextView>(R.id.playerChannel)?.apply {
                visible()
                text = if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                    when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                        "0" -> "${item.channelName}(${item.channelLogin})"
                        "1" -> item.channelName
                        else -> item.channelLogin
                    }
                } else {
                    item.channelName
                }
                setOnClickListener {
                    findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelId = item.channelId,
                        channelLogin = item.channelLogin,
                        channelName = item.channelName,
                        channelLogo = item.channelLogo
                    ))
                    slidingLayout.minimize()
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true)) {
            item.startedAt?.let {
                TwitchApiHelper.parseIso8601DateUTC(it)?.let { startedAtMs ->
                    updateUptime(startedAtMs)
                }
            }
        }
        updateStreamInfo(item.title, item.gameId, item.gameSlug, item.gameName)
        val activity = requireActivity() as MainActivity
        val account = Account.get(activity)
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        if (prefs.getBoolean(C.PLAYER_FOLLOW, true) && (setting == 0 || setting == 1)) {
            val followButton = requireView().findViewById<ImageButton>(R.id.playerFollow)
            followButton?.visible()
            followButton?.setOnClickListener {
                viewModel.isFollowing.value?.let {
                    if (it) {
                        FragmentUtils.showUnfollowDialog(requireContext(),
                            if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                                when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                    "0" -> "${item.channelName}(${item.channelLogin})"
                                    "1" -> item.channelName
                                    else -> item.channelLogin
                                }
                            } else {
                                item.channelName
                            }) {
                            viewModel.deleteFollowChannel(TwitchApiHelper.getGQLHeaders(requireContext(), true), setting, account.id, item.channelId)
                        }
                    } else {
                        viewModel.saveFollowChannel(requireContext().filesDir.path, TwitchApiHelper.getGQLHeaders(requireContext(), true), setting, account.id, item.channelId, item.channelLogin, item.channelName, item.channelLogo, requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false), item.startedAt)
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.isFollowing.collectLatest {
                        if (it != null) {
                            followButton?.apply {
                                if (it) {
                                    setImageResource(R.drawable.baseline_favorite_black_24)
                                } else {
                                    setImageResource(R.drawable.baseline_favorite_border_black_24)
                                }
                            }
                        }
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.follow.collectLatest { pair ->
                        if (pair != null) {
                            val following = pair.first
                            val errorMessage = pair.second
                            if (!errorMessage.isNullOrBlank()) {
                                requireContext().shortToast(errorMessage)
                            } else {
                                if (following) {
                                    requireContext().shortToast(requireContext().getString(R.string.now_following,
                                        if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                                            when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                                "0" -> "${item.channelName}(${item.channelLogin})"
                                                "1" -> item.channelName
                                                else -> item.channelLogin
                                            }
                                        } else {
                                            item.channelName
                                        }
                                    ))
                                } else {
                                    requireContext().shortToast(requireContext().getString(R.string.unfollowed,
                                        if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                                            when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                                "0" -> "${item.channelName}(${item.channelLogin})"
                                                "1" -> item.channelName
                                                else -> item.channelLogin
                                            }
                                        } else {
                                            item.channelName
                                        }
                                    ))
                                }
                            }
                            viewModel.follow.value = null
                        }
                    }
                }
            }
        }
        chatFragment = childFragmentManager.findFragmentById(R.id.chatFragmentContainer).let {
            if (it != null) {
                it as ChatFragment
            } else {
                val fragment = ChatFragment.newInstance(item.channelId, item.channelLogin, item.channelName, item.id)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun initialize() {
        super.initialize()
        val activity = requireActivity() as MainActivity
        val account = Account.get(activity)
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        viewModel.isFollowingChannel(TwitchApiHelper.getHelixHeaders(requireContext()), account, TwitchApiHelper.getGQLHeaders(requireContext(), true), setting, item.channelId, item.channelLogin)
    }

    override fun startPlayer() {
        super.startPlayer()
        viewModel.useProxy = prefs.getBoolean(C.PLAYER_STREAM_PROXY, false)
        if (viewModel.stream.value == null) {
            viewModel.stream.value = item
            loadStream(item)
            val account = Account.get(requireContext())
            viewModel.loadStream(
                stream = item,
                loop = requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) ||
                        !requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                        (requireContext().prefs().getBoolean(C.CHAT_POINTS_COLLECT, true) && !account.id.isNullOrBlank() && !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank()),
                helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
            )
        }
    }

    private fun loadStream(stream: Stream) {
        player?.prepare()
        try {
            stream.channelLogin?.let { channelLogin ->
                val proxyUrl = prefs.getString(C.PLAYER_PROXY_URL, "https://api.ttv.lol/playlist/\$channel.m3u8?allow_source=true&allow_audio_only=true&fast_bread=true")
                val headers = prefs.getString(C.PLAYER_STREAM_HEADERS, null)?.let {
                    try {
                        val json = JSONObject(it)
                        hashMapOf<String, String>().apply {
                            json.keys().forEach { key ->
                                put(key, json.optString(key))
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (viewModel.useProxy && !proxyUrl.isNullOrBlank()) {
                    player?.sendCustomCommand(SessionCommand(PlaybackService.START_STREAM, bundleOf(
                        PlaybackService.ITEM to stream,
                        PlaybackService.URI to proxyUrl.replace("\$channel", channelLogin),
                        PlaybackService.HEADERS_KEYS to headers?.keys?.toTypedArray(),
                        PlaybackService.HEADERS_VALUES to headers?.values?.toTypedArray()
                    )), Bundle.EMPTY)
                    player?.prepare()
                } else {
                    if (viewModel.useProxy) {
                        viewModel.useProxy = false
                    }
                    val proxyHost = prefs.getString(C.PROXY_HOST, null)
                    val proxyPort = prefs.getString(C.PROXY_PORT, null)?.toIntOrNull()
                    val proxyMultivariantPlaylist = prefs.getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, true) && !proxyHost.isNullOrBlank() && proxyPort != null
                    viewModel.load(
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                        channelLogin = channelLogin,
                        randomDeviceId = prefs.getBoolean(C.TOKEN_RANDOM_DEVICEID, true),
                        xDeviceId = prefs.getString(C.TOKEN_XDEVICEID, "twitch-web-wall-mason"),
                        playerType = prefs.getString(C.TOKEN_PLAYERTYPE, "site"),
                        supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                        proxyPlaybackAccessToken = prefs.getBoolean(C.PROXY_PLAYBACK_ACCESS_TOKEN, false),
                        proxyMultivariantPlaylist = proxyMultivariantPlaylist,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort,
                        proxyUser = prefs.getString(C.PROXY_USER, null),
                        proxyPassword = prefs.getString(C.PROXY_PASSWORD, null),
                        enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.result.collectLatest {
                                if (it != null) {
                                    player?.sendCustomCommand(SessionCommand(PlaybackService.START_STREAM, bundleOf(
                                        PlaybackService.ITEM to stream,
                                        PlaybackService.URI to it,
                                        PlaybackService.HEADERS_KEYS to headers?.keys?.toTypedArray(),
                                        PlaybackService.HEADERS_VALUES to headers?.values?.toTypedArray(),
                                        PlaybackService.PLAYLIST_AS_DATA to proxyMultivariantPlaylist
                                    )), Bundle.EMPTY)
                                    player?.prepare()
                                    viewModel.result.value = null
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            requireContext().toast(R.string.error_stream)
        }
    }

    fun checkAds() {
        player?.sendCustomCommand(SessionCommand(PlaybackService.GET_LAST_TAG, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val tag = result.get().extras.getString(PlaybackService.RESULT)
                    val usingProxy = result.get().extras.getBoolean(PlaybackService.USING_PROXY)
                    val stopProxy = result.get().extras.getBoolean(PlaybackService.STOP_PROXY)
                    val playlist = result.get().extras.getString(PlaybackService.ITEM)
                    val oldValue = viewModel.playingAds
                    viewModel.playingAds = tag == "ads=true"
                    if (viewModel.playingAds) {
                        if (usingProxy) {
                            player?.sendCustomCommand(SessionCommand(PlaybackService.TOGGLE_PROXY, bundleOf(
                                PlaybackService.USING_PROXY to false,
                                PlaybackService.STOP_PROXY to true
                            )), Bundle.EMPTY)
                        } else {
                            if (!oldValue) {
                                if (!stopProxy && !playlist.isNullOrBlank() && prefs.getBoolean(C.PROXY_MEDIA_PLAYLIST, true) && !prefs.getString(C.PROXY_HOST, null).isNullOrBlank() && prefs.getString(C.PROXY_PORT, null)?.toIntOrNull() != null) {
                                    player?.sendCustomCommand(SessionCommand(PlaybackService.TOGGLE_PROXY, bundleOf(PlaybackService.USING_PROXY to true)), Bundle.EMPTY)
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        for (i in 0 until 10) {
                                            delay(10000)
                                            if (!viewModel.checkPlaylist(playlist)) {
                                                break
                                            }
                                        }
                                        player?.sendCustomCommand(SessionCommand(PlaybackService.TOGGLE_PROXY, bundleOf(PlaybackService.USING_PROXY to false)), Bundle.EMPTY)
                                    }
                                } else {
                                    if (prefs.getBoolean(C.PLAYER_HIDE_ADS, false)) {
                                        requireContext().toast(R.string.waiting_ads)
                                    }
                                }
                            }
                        }
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun onError(error: PlaybackException) {
        Log.e(tag, "Player error", error)
        player?.sendCustomCommand(SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val responseCode = result.get().extras.getInt(PlaybackService.RESULT)
                    if (requireContext().isNetworkAvailable) {
                        when {
                            responseCode == 404 -> {
                                requireContext().toast(R.string.stream_ended)
                            }
                            viewModel.useProxy && responseCode >= 400 -> {
                                requireContext().toast(R.string.proxy_error)
                                viewModel.useProxy = false
                                viewLifecycleOwner.lifecycleScope.launch {
                                    delay(1500L)
                                    try {
                                        restartPlayer()
                                    } catch (e: Exception) {}
                                }
                            }
                            else -> {
                                requireContext().shortToast(R.string.player_error)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    delay(1500L)
                                    try {
                                        restartPlayer()
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    fun updateViewerCount(viewerCount: Int?) {
        val viewers = requireView().findViewById<TextView>(R.id.playerViewersText)
        val viewerIcon = requireView().findViewById<ImageView>(R.id.playerViewersIcon)
        if (viewerCount != null) {
            viewers?.text = TwitchApiHelper.formatCount(requireContext(), viewerCount)
            if (prefs.getBoolean(C.PLAYER_VIEWERICON, true)) {
                viewerIcon?.visible()
            }
        } else {
            viewers?.text = null
            viewerIcon?.gone()
        }
    }

    fun updateLiveStatus(playbackMessage: PlaybackMessage, channelLogin: String?) {
        if (channelLogin == item.channelLogin) {
            playbackMessage.live?.let {
                if (it) {
                    restartPlayer()
                }
                updateUptime(playbackMessage.serverTime?.times(1000))
            }
        }
    }

    private fun updateUptime(uptimeMs: Long?) {
        val layout = requireView().findViewById<LinearLayout>(R.id.playerUptime)
        val uptime = requireView().findViewById<Chronometer>(R.id.playerUptimeText)
        uptime?.stop()
        if (uptimeMs != null && prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true)) {
            layout?.visible()
            uptime?.apply {
                base = SystemClock.elapsedRealtime() + uptimeMs - System.currentTimeMillis()
                start()
            }
            requireView().findViewById<ImageView>(R.id.playerUptimeIcon)?.apply {
                if (prefs.getBoolean(C.PLAYER_VIEWERICON, true)) {
                    visible()
                } else {
                    gone()
                }
            }
        } else {
            layout?.gone()
        }
    }

    fun updateStreamInfo(title: String?, gameId: String?, gameSlug: String?, gameName: String?) {
        requireView().findViewById<TextView>(R.id.playerTitle)?.apply {
            if (!title.isNullOrBlank() && prefs.getBoolean(C.PLAYER_TITLE, true)) {
                text = title.trim()
                visible()
            } else {
                text = null
                gone()
            }
        }
        requireView().findViewById<TextView>(R.id.playerCategory)?.apply {
            if (!gameName.isNullOrBlank() && prefs.getBoolean(C.PLAYER_CATEGORY, true)) {
                text = gameName
                visible()
                setOnClickListener {
                    findNavController().navigate(
                        if (prefs.getBoolean(C.UI_GAMEPAGER, true)) {
                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                gameId = gameId,
                                gameSlug = gameSlug,
                                gameName = gameName
                            )
                        } else {
                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                gameId = gameId,
                                gameSlug = gameSlug,
                                gameName = gameName
                            )
                        }
                    )
                    slidingLayout.minimize()
                }
            } else {
                text = null
                gone()
            }
        }
    }

    fun restartPlayer() {
        if (viewModel.playerMode != PlayerMode.DISABLED) {
            loadStream(item)
        }
    }

    fun openViewerList() {
        item.channelLogin?.let { login -> FragmentUtils.showPlayerViewerListDialog(childFragmentManager, login) }
    }

    fun showPlaylistTags(mediaPlaylist: Boolean) {
        player?.sendCustomCommand(SessionCommand(if (mediaPlaylist) PlaybackService.GET_MEDIA_PLAYLIST else PlaybackService.GET_MULTIVARIANT_PLAYLIST, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val tags = result.get().extras.getString(PlaybackService.RESULT)
                    if (!tags.isNullOrBlank()) {
                        requireContext().getAlertDialogBuilder().apply {
                            setView(NestedScrollView(context).apply {
                                addView(HorizontalScrollView(context).apply {
                                    addView(TextView(context).apply {
                                        text = tags
                                        textSize = 12F
                                        setTextIsSelectable(true)
                                    })
                                })
                            })
                            setNegativeButton(R.string.copy_clip) { _, _ ->
                                val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("label", tags))
                            }
                            setPositiveButton(android.R.string.ok, null)
                        }.show()
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    fun isActive() = chatFragment?.isActive()

    fun disconnect() = chatFragment?.disconnect()

    fun reconnect() = chatFragment?.reconnect()

    fun emoteMenuIsVisible() = chatFragment?.emoteMenuIsVisible() == true

    fun toggleEmoteMenu(enable: Boolean) = chatFragment?.toggleEmoteMenu(enable)

    fun toggleBackPressedCallback(enable: Boolean) = chatFragment?.toggleBackPressedCallback(enable)

//    override fun play(obj: Parcelable) {
//        val stream = obj as Stream
//        if (viewModel.stream != stream) {
//            viewModel.player.playWhenReady = false
//            chatView.adapter.submitList(null)
//        }
//        viewModel.stream = stream
//        draggableView?.maximize()
//    }

    override fun showDownloadDialog() {
        if (viewModel.loaded.value) {
            player?.sendCustomCommand(SessionCommand(PlaybackService.GET_URLS, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
                result.addListener({
                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                        result.get().extras.getStringArray(PlaybackService.URLS_KEYS)?.let { keys ->
                            result.get().extras.getStringArray(PlaybackService.URLS_VALUES)?.let { values ->
                                DownloadDialog.newInstance(item, keys, values).show(childFragmentManager, null)
                            }
                        }
                    }
                }, MoreExecutors.directExecutor())
            }
        }
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            restartPlayer()
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    when (callback) {
                        "refresh" -> {
                            item.channelLogin?.let { channelLogin ->
                                val proxyHost = prefs.getString(C.PROXY_HOST, null)
                                val proxyPort = prefs.getString(C.PROXY_PORT, null)?.toIntOrNull()
                                val proxyMultivariantPlaylist = prefs.getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, true) && !proxyHost.isNullOrBlank() && proxyPort != null
                                viewModel.load(
                                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                                    channelLogin = channelLogin,
                                    randomDeviceId = prefs.getBoolean(C.TOKEN_RANDOM_DEVICEID, true),
                                    xDeviceId = prefs.getString(C.TOKEN_XDEVICEID, "twitch-web-wall-mason"),
                                    playerType = prefs.getString(C.TOKEN_PLAYERTYPE, "site"),
                                    supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                                    proxyPlaybackAccessToken = prefs.getBoolean(C.PROXY_PLAYBACK_ACCESS_TOKEN, false),
                                    proxyMultivariantPlaylist = proxyMultivariantPlaylist,
                                    proxyHost = proxyHost,
                                    proxyPort = proxyPort,
                                    proxyUser = prefs.getString(C.PROXY_USER, null),
                                    proxyPassword = prefs.getString(C.PROXY_PASSWORD, null),
                                    enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
                                )
                            }
                            viewModel.isFollowingChannel(TwitchApiHelper.getHelixHeaders(requireContext()), Account.get(requireContext()), TwitchApiHelper.getGQLHeaders(requireContext(), true), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, item.channelId, item.channelLogin)
                        }
                        "follow" -> viewModel.saveFollowChannel(requireContext().filesDir.path, TwitchApiHelper.getGQLHeaders(requireContext(), true), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, Account.get(requireContext()).id, item.channelId, item.channelLogin, item.channelName, item.channelLogo, requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false), item.startedAt)
                        "unfollow" -> viewModel.deleteFollowChannel(TwitchApiHelper.getGQLHeaders(requireContext(), true), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, Account.get(requireContext()).id, item.channelId)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_STREAM = "stream"

        fun newInstance(stream: Stream): StreamPlayerFragment {
            return StreamPlayerFragment().apply { arguments = bundleOf(KEY_STREAM to stream) }
        }
    }
}
