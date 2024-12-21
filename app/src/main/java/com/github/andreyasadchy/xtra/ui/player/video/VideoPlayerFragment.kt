package com.github.andreyasadchy.xtra.ui.player.video

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerVideoBinding
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlaybackService
import com.github.andreyasadchy.xtra.ui.player.PlayerGamesDialog
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.enable
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.isNetworkAvailable
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoPlayerFragment : BasePlayerFragment(), HasDownloadDialog, PlayerGamesDialog.PlayerSeekListener {

    private var _binding: FragmentPlayerVideoBinding? = null
    private val binding get() = _binding!!
    override val viewModel: VideoPlayerViewModel by viewModels()
    private lateinit var item: Video

    override val controllerShowTimeoutMs: Int = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_VIDEO, Video::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_VIDEO)!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerVideoBinding.inflate(inflater, container, false).also {
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
        if (requireContext().prefs().getBoolean(C.PLAYER_MENU_BOOKMARK, true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.isBookmarked.collectLatest {
                        if (it != null) {
                            (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setBookmarkText(it)
                            viewModel.isBookmarked.value = null
                        }
                    }
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_MENU, true)) {
            requireView().findViewById<ImageButton>(R.id.playerMenu)?.apply {
                visible()
                setOnClickListener {
                    PlayerSettingsDialog.newInstance(
                        speedText = prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.find { it == player?.playbackParameters?.speed.toString() },
                        vodGames = !viewModel.gamesList.value.isNullOrEmpty()
                    ).show(childFragmentManager, "closeOnPip")
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_DOWNLOAD, false)) {
            download?.apply {
                visible()
                setOnClickListener { showDownloadDialog() }
            }
        }
        if ((prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false)) && !item.id.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.gamesList.collectLatest { list ->
                        if (!list.isNullOrEmpty()) {
                            if (prefs.getBoolean(C.PLAYER_GAMESBUTTON, true)) {
                                requireView().findViewById<ImageButton>(R.id.playerGames)?.apply {
                                    visible()
                                    setOnClickListener { showVodGames() }
                                }
                            }
                            (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setVodGames()
                        }
                    }
                }
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
        if (!item.title.isNullOrBlank() && prefs.getBoolean(C.PLAYER_TITLE, true)) {
            requireView().findViewById<TextView>(R.id.playerTitle)?.apply {
                visible()
                text = item.title
            }
        }
        if (!item.gameName.isNullOrBlank() && prefs.getBoolean(C.PLAYER_CATEGORY, true)) {
            requireView().findViewById<TextView>(R.id.playerCategory)?.apply {
                visible()
                text = item.gameName
                setOnClickListener {
                    findNavController().navigate(
                        if (prefs.getBoolean(C.UI_GAMEPAGER, true)) {
                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                gameId = item.gameId,
                                gameSlug = item.gameSlug,
                                gameName = item.gameName
                            )
                        } else {
                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                gameId = item.gameId,
                                gameSlug = item.gameSlug,
                                gameName = item.gameName
                            )
                        }
                    )
                    slidingLayout.minimize()
                }
            }
        }
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        if (prefs.getBoolean(C.PLAYER_FOLLOW, true) && (setting == 0 || setting == 1)) {
            val followButton = requireView().findViewById<ImageButton>(R.id.playerFollow)
            followButton?.visible()
            followButton?.setOnClickListener {
                viewModel.isFollowing.value?.let {
                    if (it) {
                        requireContext().getAlertDialogBuilder()
                            .setMessage(requireContext().getString(R.string.unfollow_channel,
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
                            .setNegativeButton(getString(R.string.no), null)
                            .setPositiveButton(getString(R.string.yes)) { _, _ -> viewModel.deleteFollowChannel(TwitchApiHelper.getGQLHeaders(requireContext(), true), setting, requireContext().tokenPrefs().getString(C.USER_ID, null), item.channelId) }
                            .show()
                    } else {
                        viewModel.saveFollowChannel(requireContext().filesDir.path, TwitchApiHelper.getGQLHeaders(requireContext(), true), setting, requireContext().tokenPrefs().getString(C.USER_ID, null), item.channelId, item.channelLogin, item.channelName, item.channelLogo, requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false))
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
                val fragment = ChatFragment.newInstance(item.channelId, item.channelLogin, item.id, 0)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun initialize() {
        super.initialize()
        viewModel.isFollowingChannel(TwitchApiHelper.getHelixHeaders(requireContext()), TwitchApiHelper.getGQLHeaders(requireContext(), true), requireContext().tokenPrefs().getString(C.USER_ID, null), requireContext().tokenPrefs().getString(C.USERNAME, null), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, item.channelId, item.channelLogin)
        if ((prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false)) && !item.id.isNullOrBlank()) {
            viewModel.loadGamesList(TwitchApiHelper.getGQLHeaders(requireContext()), item.id)
        }
    }

    override fun startPlayer() {
        super.startPlayer()
        if (requireArguments().getBoolean(KEY_IGNORE_SAVED_POSITION) && !viewModel.loaded.value) {
            playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, requireArguments().getDouble(KEY_OFFSET).toLong())
        } else {
            val id = item.id?.toLongOrNull()
            if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && id != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.savedPosition.collectLatest {
                            playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, it?.position ?: 0)
                        }
                    }
                }
                viewModel.getPosition(id)
            } else {
                playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, 0)
            }
        }
    }

    private fun playVideo(skipAccessToken: Boolean, playbackPosition: Long?) {
        if (skipAccessToken && !item.animatedPreviewURL.isNullOrBlank()) {
            player?.sendCustomCommand(SessionCommand(PlaybackService.START_VIDEO, bundleOf(
                PlaybackService.ITEM to item,
                PlaybackService.USING_PLAYLIST to false,
                PlaybackService.PLAYBACK_POSITION to playbackPosition,
            )), Bundle.EMPTY)
        } else {
            viewModel.load(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                videoId = item.id,
                playerType = prefs.getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
            )
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.result.collectLatest {
                        if (it != null) {
                            player?.sendCustomCommand(SessionCommand(PlaybackService.START_VIDEO, bundleOf(
                                PlaybackService.ITEM to item,
                                PlaybackService.URI to it.toString(),
                                PlaybackService.USING_PLAYLIST to true,
                                PlaybackService.PLAYBACK_POSITION to playbackPosition,
                            )), Bundle.EMPTY)
                            viewModel.result.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onError(error: PlaybackException) {
        Log.e(tag, "Player error", error)
        player?.sendCustomCommand(SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val responseCode = result.get().extras.getInt(PlaybackService.RESULT)
                    if (requireContext().isNetworkAvailable) {
                        val skipAccessToken = prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                        when {
                            skipAccessToken == 1 && viewModel.shouldRetry && responseCode != 0 -> {
                                viewModel.shouldRetry = false
                                playVideo(false, player?.currentPosition)
                            }
                            skipAccessToken == 2 && viewModel.shouldRetry && responseCode != 0 -> {
                                viewModel.shouldRetry = false
                                playVideo(true, player?.currentPosition)
                            }
                            responseCode == 403 -> {
                                requireContext().toast(R.string.video_subscribers_only)
                            }
                            else -> {
                                requireContext().shortToast(R.string.player_error)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    delay(1500L)
                                    try {
                                        player?.prepare()
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    fun showVodGames() {
        viewModel.gamesList.value?.let { PlayerGamesDialog.newInstance(it).show(childFragmentManager, "closeOnPip") }
    }

    fun checkBookmark() {
        item.id?.let { viewModel.checkBookmark(it) }
    }

    fun saveBookmark() {
        viewModel.saveBookmark(requireContext().filesDir.path, TwitchApiHelper.getHelixHeaders(requireContext()), TwitchApiHelper.getGQLHeaders(requireContext()), item)
    }

    override fun seek(position: Long) {
        player?.seekTo(position)
    }

    override fun showDownloadDialog() {
        if (viewModel.loaded.value) {
            player?.sendCustomCommand(SessionCommand(PlaybackService.GET_VIDEO_DOWNLOAD_INFO, Bundle.EMPTY), Bundle.EMPTY)?.let { result ->
                result.addListener({
                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                        result.get().extras.getStringArray(PlaybackService.URLS_KEYS)?.let { keys ->
                            result.get().extras.getStringArray(PlaybackService.URLS_VALUES)?.let { values ->
                                val totalDuration = result.get().extras.getLong(PlaybackService.TOTAL_DURATION)
                                val currentPosition = result.get().extras.getLong(PlaybackService.CURRENT_POSITION)
                                DownloadDialog.newInstance(item, keys, values, totalDuration, currentPosition).show(childFragmentManager, null)
                            }
                        }
                    }
                }, MoreExecutors.directExecutor())
            }
        }
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            player?.prepare()
        }
    }

    override fun onNetworkLost() {
        if (isResumed) {
            player?.stop()
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    when (callback) {
                        "refresh" -> {
                            viewModel.load(
                                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                                videoId = item.id,
                                playerType = prefs.getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
                            )
                            viewModel.isFollowingChannel(TwitchApiHelper.getHelixHeaders(requireContext()), TwitchApiHelper.getGQLHeaders(requireContext(), true), requireContext().tokenPrefs().getString(C.USER_ID, null), requireContext().tokenPrefs().getString(C.USERNAME, null), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, item.channelId, item.channelLogin)
                            if ((prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false)) && !item.id.isNullOrBlank()) {
                                viewModel.loadGamesList(TwitchApiHelper.getGQLHeaders(requireContext()), item.id)
                            }
                        }
                        "follow" -> viewModel.saveFollowChannel(requireContext().filesDir.path, TwitchApiHelper.getGQLHeaders(requireContext(), true), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, requireContext().tokenPrefs().getString(C.USER_ID, null), item.channelId, item.channelLogin, item.channelName, item.channelLogo, requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false))
                        "unfollow" -> viewModel.deleteFollowChannel(TwitchApiHelper.getGQLHeaders(requireContext(), true), prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0, requireContext().tokenPrefs().getString(C.USER_ID, null), item.channelId)
                    }
                }
            }
        }
    }

    override fun onClose() {
        if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            item.id?.toLongOrNull()?.let { id ->
                player?.currentPosition?.let { position ->
                    viewModel.savePosition(id, position)
                }
            }
        }
        super.onClose()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_VIDEO = "video"
        private const val KEY_OFFSET = "offset"
        private const val KEY_IGNORE_SAVED_POSITION = "ignoreSavedPosition"

        fun newInstance(video: Video, offset: Double? = null, ignoreSavedPosition: Boolean = false): VideoPlayerFragment {
            return VideoPlayerFragment().apply { arguments = bundleOf(KEY_VIDEO to video, KEY_OFFSET to offset, KEY_IGNORE_SAVED_POSITION to ignoreSavedPosition) }
        }
    }
}
