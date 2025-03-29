package com.github.andreyasadchy.xtra.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.trackPipAnimationHintView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.RadioButtonDialogFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.view.SlidingLayout
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.disable
import com.github.andreyasadchy.xtra.util.enable
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.hideKeyboard
import com.github.andreyasadchy.xtra.util.isInPortraitOrientation
import com.github.andreyasadchy.xtra.util.isKeyboardShown
import com.github.andreyasadchy.xtra.util.isNetworkAvailable
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerFragment : BaseNetworkFragment(), SlidingLayout.Listener, HasDownloadDialog, PlayerGamesDialog.PlayerSeekListener, SleepTimerDialog.OnSleepTimerStartedListener, RadioButtonDialogFragment.OnSortOptionChanged, PlayerVolumeDialog.PlayerVolumeListener, IntegrityDialog.CallbackListener {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by viewModels()
    private var chatFragment: ChatFragment? = null

    var stream: Stream? = null
    var video: Video? = null
    var clip: Clip? = null
    var offlineVideo: OfflineVideo? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val player: MediaController?
        get() = controllerFuture?.let { if (it.isDone && !it.isCancelled) it.get() else null }

    private var isPortrait = false
    private var isKeyboardShown = false
    private var resizeMode = 0
    private var chatWidthLandscape = 0

    private lateinit var prefs: SharedPreferences

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            minimize()
        }
    }

    @Suppress("DEPRECATION")
    private var systemUiFlags = (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_STREAM, Stream::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_STREAM)
        }?.let { stream = it } ?:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_VIDEO)
        }?.let { video = it } ?:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_CLIP, Clip::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_CLIP)
        }?.let { clip = it } ?:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_OFFLINE_VIDEO, OfflineVideo::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_OFFLINE_VIDEO)
        }?.let {
            enableNetworkCheck = false
            offlineVideo = it
        }
        super.onCreate(savedInstanceState)
        val activity = requireActivity()
        prefs = activity.prefs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            systemUiFlags = systemUiFlags or (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        isPortrait = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            (activity as? MainActivity)?.orientation == 1
        } else {
            activity.isInPortraitOrientation
        }
        activity.onBackPressedDispatcher.addCallback(this, backPressedCallback)
        WindowCompat.getInsetsController(
            requireActivity().window,
            requireActivity().window.decorView
        ).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false).also {
            it.slidingLayout.orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            slidingLayout.updateBackgroundColor(isPortrait)
            val ignoreCutouts = prefs.getBoolean(C.UI_DRAW_BEHIND_CUTOUTS, false)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = if (!isPortrait && ignoreCutouts) {
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                } else {
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                }
                if (isPortrait) {
                    view.updatePadding(left = 0, top = insets.top, right = 0)
                } else {
                    if (ignoreCutouts) {
                        view.updatePadding(left = 0, top = 0, right = 0)
                    } else {
                        val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                        view.updatePadding(left = cutoutInsets.left, top = 0, right = cutoutInsets.right)
                    }
                }
                slidingLayout.apply {
                    savedInsets = insets
                    if (!isMaximized && !isPortrait) {
                        init()
                    }
                }
                chatLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom
                }
                WindowInsetsCompat.CONSUMED
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.integrity.collectLatest {
                        if (it != null &&
                            it != "done" &&
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                            requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                        ) {
                            IntegrityDialog.show(childFragmentManager, it)
                            viewModel.integrity.value = "done"
                        }
                    }
                }
            }
            val activity = requireActivity() as MainActivity
            slidingLayout.addListener(activity)
            slidingLayout.addListener(this@PlayerFragment)
            slidingLayout.maximizedSecondViewVisibility = if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) View.VISIBLE else View.GONE //TODO
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        requireActivity().trackPipAnimationHintView(playerView)
                    }
                }
            }
            aspectRatioFrameLayout.setAspectRatio(16f / 9f)
            chatWidthLandscape = prefs.getInt(C.LANDSCAPE_CHAT_WIDTH, 0)
            if (prefs.getBoolean(C.PLAYER_FULLSCREEN, true)) {
                view.findViewById<ImageButton>(R.id.playerFullscreenToggle)?.apply {
                    visible()
                    setOnClickListener {
                        activity.apply {
                            if (isPortrait) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }
                }
            }
            if (prefs.getBoolean(C.PLAYER_ASPECT, true)) {
                view.findViewById<ImageButton>(R.id.playerAspectRatio)?.apply {
                    setOnClickListener { setResizeMode() }
                }
            }
            initLayout()
            playerView.controllerAutoShow = stream == null
            if (prefs.getBoolean(C.PLAYER_DOUBLETAP, true) && !prefs.getBoolean(C.CHAT_DISABLE, false)) {
                playerView.setOnDoubleTapListener {
                    if (!isPortrait && slidingLayout.isMaximized) {
                        if (chatLayout.isVisible) {
                            hideChat()
                        } else {
                            showChat()
                        }
                    }
                }
            }
            if (prefs.getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false)) {
                view.keepScreenOn = true
            }
            changePlayerMode()
            var channelId: String? = null
            var channelLogin: String? = null
            var channelName: String? = null
            var title: String? = null
            var gameId: String? = null
            var gameSlug: String? = null
            var gameName: String? = null
            stream?.let {
                channelId = it.channelId
                channelLogin = it.channelLogin
                channelName = it.channelName
                title = it.title
                gameId = it.gameId
                gameSlug = it.gameSlug
                gameName = it.gameName
            } ?:
            video?.let {
                channelId = it.channelId
                channelLogin = it.channelLogin
                channelName = it.channelName
                title = it.title
                gameId = it.gameId
                gameSlug = it.gameSlug
                gameName = it.gameName
            } ?:
            clip?.let {
                channelId = it.channelId
                channelLogin = it.channelLogin
                channelName = it.channelName
                title = it.title
                gameId = it.gameId
                gameSlug = it.gameSlug
                gameName = it.gameName
            } ?:
            offlineVideo?.let {
                channelId = it.channelId
                channelLogin = it.channelLogin
                channelName = it.channelName
                title = it.name
                gameId = it.gameId
                gameSlug = it.gameSlug
                gameName = it.gameName
            }
            if (prefs.getBoolean(C.PLAYER_CHANNEL, true)) {
                requireView().findViewById<TextView>(R.id.playerChannel)?.apply {
                    visible()
                    text = if (channelLogin != null && !channelLogin.equals(channelName, true)) {
                        when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                            "0" -> "${channelName}(${channelLogin})"
                            "1" -> channelName
                            else -> channelLogin
                        }
                    } else {
                        channelName
                    }
                    setOnClickListener {
                        findNavController().navigate(
                            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                channelId = channelId,
                                channelLogin = channelLogin,
                                channelName = channelName,
                            )
                        )
                        slidingLayout.minimize()
                    }
                }
            }
            if (!title.isNullOrBlank() && prefs.getBoolean(C.PLAYER_TITLE, true)) {
                requireView().findViewById<TextView>(R.id.playerTitle)?.apply {
                    visible()
                    text = title
                }
            }
            if (!gameName.isNullOrBlank() && prefs.getBoolean(C.PLAYER_CATEGORY, true)) {
                requireView().findViewById<TextView>(R.id.playerCategory)?.apply {
                    visible()
                    text = gameName
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
                }
            }
            if (prefs.getBoolean(C.PLAYER_MINIMIZE, true)) {
                view.findViewById<ImageButton>(R.id.playerMinimize)?.apply {
                    visible()
                    setOnClickListener { minimize() }
                }
            }
            if (prefs.getBoolean(C.PLAYER_VOLUMEBUTTON, true)) {
                view.findViewById<ImageButton>(R.id.playerVolume)?.apply {
                    visible()
                    setOnClickListener { showVolumeDialog() }
                }
            }
            if (prefs.getBoolean(C.PLAYER_SETTINGS, true)) {
                view.findViewById<ImageButton>(R.id.playerSettings)?.apply {
                    visible()
                    setOnClickListener { showQualityDialog() }
                }
            }
            if (prefs.getBoolean(C.PLAYER_MODE, false)) {
                view.findViewById<ImageButton>(R.id.playerMode)?.apply {
                    visible()
                    setOnClickListener {
                        if (viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) == AUDIO_ONLY_QUALITY) {
                            changeQuality(viewModel.previousIndex)
                        } else {
                            changeQuality(viewModel.qualities.keys.indexOf(AUDIO_ONLY_QUALITY))
                        }
                        changePlayerMode()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(C.PLAYER_AUDIO_COMPRESSOR_BUTTON, true)) {
                view.findViewById<ImageButton>(R.id.playerAudioCompressor)?.apply {
                    visible()
                    if (prefs.getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                        setImageResource(R.drawable.baseline_audio_compressor_on_24dp)
                    } else {
                        setImageResource(R.drawable.baseline_audio_compressor_off_24dp)
                    }
                    setOnClickListener {
                        player?.sendCustomCommand(
                            SessionCommand(
                                PlaybackService.TOGGLE_DYNAMICS_PROCESSING,
                                Bundle.EMPTY
                            ), Bundle.EMPTY
                        )?.let { result ->
                            result.addListener({
                                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                    val state = result.get().extras.getBoolean(PlaybackService.RESULT)
                                    if (state) {
                                        setImageResource(R.drawable.baseline_audio_compressor_on_24dp)
                                    } else {
                                        setImageResource(R.drawable.baseline_audio_compressor_off_24dp)
                                    }
                                }
                            }, MoreExecutors.directExecutor())
                        }
                    }
                }
            }
            if (prefs.getBoolean(C.PLAYER_MENU, true)) {
                requireView().findViewById<ImageButton>(R.id.playerMenu)?.apply {
                    visible()
                    setOnClickListener {
                        PlayerSettingsDialog.newInstance(
                            speedText = prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")
                                ?.split("\n")?.find { it == player?.playbackParameters?.speed.toString() },
                            vodGames = !viewModel.gamesList.value.isNullOrEmpty()
                        ).show(childFragmentManager, "closeOnPip")
                    }
                }
            }
            stream.let { stream ->
                if (stream != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.streamResult.collectLatest {
                                if (it != null) {
                                    val proxyHost = prefs.getString(C.PROXY_HOST, null)
                                    val proxyPort = prefs.getString(C.PROXY_PORT, null)?.toIntOrNull()
                                    val proxyMultivariantPlaylist = prefs.getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, false) && !proxyHost.isNullOrBlank() && proxyPort != null
                                    player?.sendCustomCommand(
                                        SessionCommand(
                                            PlaybackService.START_STREAM, bundleOf(
                                                PlaybackService.URI to it,
                                                PlaybackService.PLAYLIST_AS_DATA to proxyMultivariantPlaylist,
                                                PlaybackService.TITLE to stream.title,
                                                PlaybackService.CHANNEL_NAME to stream.channelName,
                                                PlaybackService.CHANNEL_LOGO to stream.channelLogo,
                                            )
                                        ), Bundle.EMPTY
                                    )
                                    player?.prepare()
                                    viewModel.streamResult.value = null
                                }
                            }
                        }
                    }
                    if (!requireContext().tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                        (!TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank())
                    ) {
                        if (prefs.getBoolean(C.PLAYER_CHATBARTOGGLE, false) && !prefs.getBoolean(C.CHAT_DISABLE, false)) {
                            view.findViewById<ImageButton>(R.id.playerChatBarToggle)?.apply {
                                visible()
                                setOnClickListener { toggleChatBar() }
                            }
                        }
                        slidingLayout.viewTreeObserver.addOnGlobalLayoutListener {
                            if (slidingLayout.isKeyboardShown) {
                                if (!isKeyboardShown) {
                                    isKeyboardShown = true
                                    if (!isPortrait) {
                                        chatLayout.updateLayoutParams { width = (slidingLayout.width / 1.8f).toInt() }
                                        showStatusBar()
                                    }
                                }
                            } else {
                                if (isKeyboardShown) {
                                    isKeyboardShown = false
                                    chatLayout.clearFocus()
                                    if (!isPortrait) {
                                        chatLayout.updateLayoutParams { width = chatWidthLandscape }
                                        if (slidingLayout.isMaximized) {
                                            hideStatusBar()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.stream.collectLatest {
                                if (it != null) {
                                    chatFragment?.updateStreamId(it.id)
                                    if (prefs.getBoolean(C.CHAT_DISABLE, false) ||
                                        !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                                        requireView().findViewById<TextView>(R.id.playerViewersText)?.text.isNullOrBlank()
                                    ) {
                                        updateViewerCount(it.viewerCount)
                                    }
                                    if (prefs.getBoolean(C.CHAT_DISABLE, false) ||
                                        !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                                        requireView().findViewById<TextView>(R.id.playerTitle)?.text.isNullOrBlank() ||
                                        requireView().findViewById<TextView>(R.id.playerCategory)?.text.isNullOrBlank()
                                    ) {
                                        updateStreamInfo(it.title, it.gameId, it.gameSlug, it.gameName)
                                    }
                                    if (prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true) &&
                                        requireView().findViewById<LinearLayout>(R.id.playerUptime)?.isVisible == false
                                    ) {
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
                    if (prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true)) {
                        stream.startedAt?.let {
                            TwitchApiHelper.parseIso8601DateUTC(it)?.let { startedAtMs ->
                                updateUptime(startedAtMs)
                            }
                        }
                    }
                    requireView().findViewById<Button>(androidx.media3.ui.R.id.exo_rew_with_amount)?.gone()
                    requireView().findViewById<Button>(androidx.media3.ui.R.id.exo_ffwd_with_amount)?.gone()
                    requireView().findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.gone()
                    requireView().findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.gone()
                    requireView().findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)?.gone()
                    updateStreamInfo(stream.title, stream.gameId, stream.gameSlug, stream.gameName)
                } else {
                    if (prefs.getBoolean(C.PLAYER_SPEEDBUTTON, true)) {
                        view.findViewById<ImageButton>(R.id.playerSpeed)?.apply {
                            visible()
                            setOnClickListener { showSpeedDialog() }
                        }
                    }
                }
            }
            video.let { video ->
                if (video != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.videoResult.collectLatest {
                                if (it != null) {
                                    player?.sendCustomCommand(
                                        SessionCommand(
                                            PlaybackService.START_VIDEO, bundleOf(
                                                PlaybackService.URI to it.toString(),
                                                PlaybackService.PLAYBACK_POSITION to viewModel.playbackPosition,
                                                PlaybackService.VIDEO_ID to video.id?.toLongOrNull(),
                                                PlaybackService.TITLE to video.title,
                                                PlaybackService.CHANNEL_NAME to video.channelName,
                                                PlaybackService.CHANNEL_LOGO to video.channelLogo,
                                            )
                                        ), Bundle.EMPTY
                                    )
                                    viewModel.videoResult.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.savedPosition.collectLatest {
                                playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, it?.position ?: 0)
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
                    if (!video.id.isNullOrBlank() && (prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false))) {
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
                }
            }
            clip.let { clip ->
                if (clip != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.clipUrls.collectLatest { map ->
                                if (map != null) {
                                    val urls = map.ifEmpty {
                                        if ((prefs.getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) == 2
                                            && !clip.thumbnailUrl.isNullOrBlank()
                                        ) {
                                            TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                                        } else {
                                            emptyMap()
                                        }
                                    }
                                    val map = mutableMapOf<String, Pair<String, String?>>()
                                    urls.forEach {
                                        if (it.key == "source") {
                                            map[it.key] = Pair(requireContext().getString(R.string.source), it.value)
                                        } else {
                                            map[it.key] = Pair(it.key, it.value)
                                        }
                                    }
                                    map.put(AUDIO_ONLY_QUALITY, Pair(requireContext().getString(R.string.audio_only), null))
                                    viewModel.qualities = map
                                    setQualityIndex()
                                    player?.let { player ->
                                        val quality = viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)
                                        if (quality?.key == AUDIO_ONLY_QUALITY) {
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                            }.build()
                                        } else {
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                            }.build()
                                        }
                                        (quality?.value?.second ?: viewModel.qualities.values.firstOrNull()?.second)?.let { url ->
                                            player.sendCustomCommand(
                                                SessionCommand(
                                                    PlaybackService.START_CLIP, bundleOf(
                                                        PlaybackService.URI to url,
                                                        PlaybackService.TITLE to clip.title,
                                                        PlaybackService.CHANNEL_NAME to clip.channelName,
                                                        PlaybackService.CHANNEL_LOGO to clip.channelLogo,
                                                    )
                                                ), Bundle.EMPTY
                                            )
                                        }
                                    }
                                    viewModel.clipUrls.value = null
                                }
                            }
                        }
                    }
                    if (!clip.videoId.isNullOrBlank()) {
                        watchVideo.visible()
                        watchVideo.setOnClickListener {
                            (requireActivity() as MainActivity).startVideo(
                                Video(
                                    id = clip.videoId,
                                    channelId = clip.channelId,
                                    channelLogin = clip.channelLogin,
                                    channelName = clip.channelName,
                                    profileImageUrl = clip.profileImageUrl,
                                    animatedPreviewURL = clip.videoAnimatedPreviewURL
                                ),
                                clip.vodOffset?.let { (it.toDouble() * 1000.0) + (player?.currentPosition ?: 0) } ?: 0.0,
                                true
                            )
                        }
                    }
                } else {
                    if (prefs.getBoolean(C.PLAYER_SLEEP, false)) {
                        view.findViewById<ImageButton>(R.id.playerSleepTimer)?.apply {
                            visible()
                            setOnClickListener { showSleepTimerDialog() }
                        }
                    }
                }
            }
            offlineVideo.let { offlineVideo ->
                if (offlineVideo != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.offlineVideo.collectLatest {
                                viewModel.qualities = mapOf(
                                    "source" to Pair(requireContext().getString(R.string.source), it?.url ?: offlineVideo.url),
                                    AUDIO_ONLY_QUALITY to Pair(requireContext().getString(R.string.audio_only), null)
                                )
                                setQualityIndex()
                                player?.let { player ->
                                    val quality = viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)
                                    if (quality?.key == AUDIO_ONLY_QUALITY) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                        }.build()
                                    } else {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                        }.build()
                                    }
                                    player.sendCustomCommand(
                                        SessionCommand(
                                            PlaybackService.START_OFFLINE_VIDEO, bundleOf(
                                                PlaybackService.URI to offlineVideo.url,
                                                PlaybackService.VIDEO_ID to offlineVideo.id,
                                                PlaybackService.PLAYBACK_POSITION to (it?.lastWatchPosition ?: 0L),
                                                PlaybackService.TITLE to offlineVideo.name,
                                                PlaybackService.CHANNEL_NAME to offlineVideo.channelName,
                                                PlaybackService.CHANNEL_LOGO to offlineVideo.channelLogo,
                                            )
                                        ), Bundle.EMPTY
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val settings = requireView().findViewById<ImageButton>(R.id.playerSettings)
                    val download = requireView().findViewById<ImageButton>(R.id.playerDownload)
                    val mode = requireView().findViewById<ImageButton>(R.id.playerMode)
                    settings?.disable()
                    download?.disable()
                    mode?.disable()
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.loaded.collectLatest {
                                if (it) {
                                    settings?.enable()
                                    download?.enable()
                                    mode?.enable()
                                    setQualityText()
                                }
                            }
                        }
                    }
                    if (prefs.getBoolean(C.PLAYER_DOWNLOAD, false)) {
                        download?.apply {
                            visible()
                            setOnClickListener { showDownloadDialog() }
                        }
                    }
                    val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
                    if (prefs.getBoolean(C.PLAYER_FOLLOW, false) && (setting == 0 || setting == 1)) {
                        val followButton = requireView().findViewById<ImageButton>(R.id.playerFollow)
                        followButton?.visible()
                        followButton?.setOnClickListener {
                            viewModel.isFollowing.value?.let {
                                if (it) {
                                    requireContext().getAlertDialogBuilder()
                                        .setMessage(requireContext().getString(R.string.unfollow_channel,
                                            if (channelLogin != null && !channelLogin.equals(channelName, true)) {
                                                when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                                    "0" -> "${channelName}(${channelLogin})"
                                                    "1" -> channelName
                                                    else -> channelLogin
                                                }
                                            } else {
                                                channelName
                                            }
                                        ))
                                        .setNegativeButton(getString(R.string.no), null)
                                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                                            viewModel.deleteFollowChannel(
                                                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                                setting,
                                                requireContext().tokenPrefs().getString(C.USER_ID, null),
                                                channelId
                                            )
                                        }
                                        .show()
                                } else {
                                    viewModel.saveFollowChannel(
                                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                        setting,
                                        requireContext().tokenPrefs().getString(C.USER_ID, null),
                                        channelId,
                                        channelLogin,
                                        channelName,
                                        requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                                        stream?.startedAt
                                    )
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
                                                    if (channelLogin != null && !channelLogin.equals(channelName, true)) {
                                                        when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                                            "0" -> "${channelName}(${channelLogin})"
                                                            "1" -> channelName
                                                            else -> channelLogin
                                                        }
                                                    } else {
                                                        channelName
                                                    }
                                                ))
                                            } else {
                                                requireContext().shortToast(requireContext().getString(R.string.unfollowed,
                                                    if (channelLogin != null && !channelLogin.equals(channelName, true)) {
                                                        when (prefs.getString(C.UI_NAME_DISPLAY, "0")) {
                                                            "0" -> "${channelName}(${channelLogin})"
                                                            "1" -> channelName
                                                            else -> channelLogin
                                                        }
                                                    } else {
                                                        channelName
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
                }
            }
            chatFragment = (childFragmentManager.findFragmentById(R.id.chatFragmentContainer) as? ChatFragment) ?: (
                    stream?.let { stream -> ChatFragment.newInstance(stream.channelId, stream.channelLogin, stream.channelName, stream.id) }
                        ?: video?.let { video -> ChatFragment.newInstance(video.channelId, video.channelLogin, video.id, 0) }
                        ?: clip?.let { clip -> ChatFragment.newInstance(clip.channelId, clip.channelLogin, clip.videoId, clip.vodOffset) }
                        ?: offlineVideo?.let { offlineVideo -> ChatFragment.newLocalInstance(offlineVideo.channelId, offlineVideo.channelLogin, offlineVideo.chatUrl) }
                    )?.also { fragment -> childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit() }
        }
    }

    private fun initLayout() {
        with(binding) {
            if (isPortrait) {
                requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener(null)
                aspectRatioFrameLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = LinearLayout.LayoutParams.MATCH_PARENT
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
                chatLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = LinearLayout.LayoutParams.MATCH_PARENT
                    height = 0
                    weight = 1f
                }
                chatLayout.visible()
                requireView().findViewById<ImageButton>(R.id.playerFullscreenToggle)?.let {
                    if (it.isVisible) {
                        it.setImageResource(R.drawable.baseline_fullscreen_black_24)
                    }
                }
                requireView().findViewById<ImageButton>(R.id.playerAspectRatio)?.gone()
                requireView().findViewById<ImageButton>(R.id.playerChatToggle)?.gone()
                showStatusBar()
                aspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener {
                    if (!isKeyboardShown && slidingLayout.isMaximized) {
                        hideStatusBar()
                    }
                }
                aspectRatioFrameLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = 0
                    height = LinearLayout.LayoutParams.MATCH_PARENT
                    weight = 1f
                }
                chatLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = chatWidthLandscape
                    height = LinearLayout.LayoutParams.MATCH_PARENT
                    weight = 0f
                }
                if (prefs.getBoolean(C.CHAT_DISABLE, false)) {
                    chatLayout.gone()
                    slidingLayout.maximizedSecondViewVisibility = View.GONE
                } else {
                    if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) {
                        showChat()
                    } else {
                        hideChat()
                    }
                }
                if (chatLayout.isVisible && requireView().findViewById<Button>(R.id.btnDown)?.isVisible == false) {
                    requireView().findViewById<RecyclerView>(R.id.recyclerView)?.let { recyclerView ->
                        recyclerView.adapter?.itemCount?.let { recyclerView.scrollToPosition(it - 1) }
                    }
                }
                requireView().findViewById<ImageButton>(R.id.playerFullscreenToggle)?.let {
                    if (it.isVisible) {
                        it.setImageResource(R.drawable.baseline_fullscreen_exit_black_24)
                    }
                }
                requireView().findViewById<ImageButton>(R.id.playerAspectRatio)?.let {
                    if (it.hasOnClickListeners()) {
                        it.visible()
                    }
                }
                slidingLayout.post {
                    if (slidingLayout.isMaximized) {
                        hideStatusBar()
                    } else {
                        showStatusBar()
                    }
                }
                aspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                resizeMode = prefs.getInt(C.ASPECT_RATIO_LANDSCAPE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
            }
            playerView.resizeMode = resizeMode
        }
    }

    fun setResizeMode() {
        resizeMode = (resizeMode + 1).let { if (it < 5) it else 0 }
        binding.playerView.resizeMode = resizeMode
        prefs.edit { putInt(C.ASPECT_RATIO_LANDSCAPE, resizeMode) }
    }

    fun showSleepTimerDialog() {
        if (requireContext().prefs().getBoolean(C.SLEEP_TIMER_USE_TIME_PICKER, false)) {
            if (((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L) {
                requireContext().getAlertDialogBuilder()
                    .setMessage(getString(R.string.stop_sleep_timer_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        onSleepTimerChanged(-1L, 0, 0, requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false))
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            } else {
                val savedValue = requireContext().prefs().getInt(C.SLEEP_TIMER_TIME, 15)
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .setHour(savedValue / 60)
                    .setMinute(savedValue % 60)
                    .build()
                picker.addOnPositiveButtonClickListener {
                    val minutes = TwitchApiHelper.getMinutesLeft(picker.hour, picker.minute)
                    onSleepTimerChanged(minutes * 60_000L, minutes / 60, minutes % 60, requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false))
                    requireContext().prefs().edit {
                        putInt(C.SLEEP_TIMER_TIME, picker.hour * 60 + picker.minute)
                    }
                }
                picker.show(childFragmentManager, null)
            }
        } else {
            SleepTimerDialog.show(childFragmentManager, (activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
        }
    }

    fun showQualityDialog() {
        if (viewModel.qualities.isNotEmpty()) {
            RadioButtonDialogFragment.newInstance(REQUEST_CODE_QUALITY, viewModel.qualities.values.map { it.first }, null, viewModel.qualityIndex).show(childFragmentManager, "closeOnPip")
        }
    }

    fun showSpeedDialog() {
        player?.playbackParameters?.speed?.let {
            prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.let { speeds ->
                RadioButtonDialogFragment.newInstance(REQUEST_CODE_SPEED, speeds, null, speeds.indexOf(it.toString())).show(childFragmentManager, "closeOnPip")
            }
        }
    }

    fun showVolumeDialog() {
        PlayerVolumeDialog.newInstance(player?.volume).show(childFragmentManager, "closeOnPip")
    }

    fun toggleChatBar() {
        with(binding) {
            requireView().findViewById<LinearLayout>(R.id.messageView)?.let {
                if (it.isVisible) {
                    chatLayout.hideKeyboard()
                    chatLayout.clearFocus()
                    if (stream != null && chatFragment?.emoteMenuIsVisible() == true) {
                        chatFragment?.toggleEmoteMenu(false)
                    }
                    it.gone()
                    prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, false) }
                } else {
                    it.visible()
                    prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, true) }
                }
            }
        }
    }

    fun hideChat() {
        with(binding) {
            if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
                requireView().findViewById<ImageButton>(R.id.playerChatToggle)?.apply {
                    visible()
                    setImageResource(R.drawable.baseline_speaker_notes_black_24)
                    setOnClickListener { showChat() }
                }
            }
            chatLayout.hideKeyboard()
            chatLayout.clearFocus()
            chatLayout.gone()
            prefs.edit { putBoolean(C.KEY_CHAT_OPENED, false) }
            slidingLayout.maximizedSecondViewVisibility = View.GONE
        }
    }

    fun showChat() {
        with(binding) {
            if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
                requireView().findViewById<ImageButton>(R.id.playerChatToggle)?.apply {
                    visible()
                    setImageResource(R.drawable.baseline_speaker_notes_off_black_24)
                    setOnClickListener { hideChat() }
                }
            }
            chatLayout.visible()
            prefs.edit { putBoolean(C.KEY_CHAT_OPENED, true) }
            slidingLayout.maximizedSecondViewVisibility = View.VISIBLE
            if (requireView().findViewById<Button>(R.id.btnDown)?.isVisible == false) {
                requireView().findViewById<RecyclerView>(R.id.recyclerView)?.let { recyclerView ->
                    recyclerView.adapter?.itemCount?.let { recyclerView.scrollToPosition(it - 1) }
                }
            }
        }
    }

    fun setSubtitles() {
        val subtitles = player?.currentTracks?.groups?.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
        requireView().findViewById<ImageButton>(R.id.playerSubtitleToggle)?.apply {
            if (subtitles != null && prefs.getBoolean(C.PLAYER_SUBTITLES, false)) {
                visible()
                if (subtitles.isSelected) {
                    setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_on)
                    setOnClickListener {
                        toggleSubtitles(false)
                        prefs.edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, false) }
                    }
                } else {
                    setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_off)
                    setOnClickListener {
                        toggleSubtitles(true)
                        prefs.edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, true) }
                    }
                }
            } else {
                gone()
            }
        }
        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSubtitles(subtitles)
    }

    fun toggleSubtitles(enabled: Boolean) {
        player?.let { player ->
            if (enabled) {
                player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }?.let {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                        .build()
                }
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .build()
            }
        }
    }

    fun minimize() {
        binding.slidingLayout.minimize()
    }

    fun maximize() {
        binding.slidingLayout.maximize()
    }

    fun setQualityText() {
        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setQuality(
            viewModel.qualities.values.elementAtOrNull(viewModel.qualityIndex)?.first
        )
    }

    fun updateViewerCount(viewerCount: Int?) {
        val viewers = requireView().findViewById<TextView>(R.id.playerViewersText)
        val viewerIcon = requireView().findViewById<ImageView>(R.id.playerViewersIcon)
        if (viewerCount != null) {
            viewers?.text = TwitchApiHelper.formatCount(viewerCount, requireContext().prefs().getBoolean(C.UI_TRUNCATEVIEWCOUNT, true))
            if (prefs.getBoolean(C.PLAYER_VIEWERICON, true)) {
                viewerIcon?.visible()
            }
        } else {
            viewers?.text = null
            viewerIcon?.gone()
        }
    }

    fun updateLiveStatus(live: Boolean, serverTime: Long?, channelLogin: String?) {
        if (channelLogin == stream?.channelLogin) {
            if (live) {
                restartPlayer()
            }
            updateUptime(serverTime?.times(1000))
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
                    binding.slidingLayout.minimize()
                }
            } else {
                text = null
                gone()
            }
        }
    }

    fun restartPlayer() {
        if (viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) != CHAT_ONLY_QUALITY) {
            loadStream(stream)
        }
    }

    fun openViewerList() {
        stream?.channelLogin?.let { login ->
            PlayerViewerListDialog.newInstance(login).show(childFragmentManager, "closeOnPip")
        }
    }

    fun showPlaylistTags(mediaPlaylist: Boolean) {
        player?.sendCustomCommand(
            SessionCommand(
                if (mediaPlaylist) {
                    PlaybackService.GET_MEDIA_PLAYLIST
                } else {
                    PlaybackService.GET_MULTIVARIANT_PLAYLIST
                },
                Bundle.EMPTY
            ), Bundle.EMPTY
        )?.let { result ->
            result.addListener({
                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                    val tags = result.get().extras.getStringArray(PlaybackService.RESULT)?.joinToString("\n")
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

    fun showVodGames() {
        viewModel.gamesList.value?.let {
            PlayerGamesDialog.newInstance(it).show(childFragmentManager, "closeOnPip")
        }
    }

    fun checkBookmark() {
        video?.id?.let { viewModel.checkBookmark(it) }
    }

    fun saveBookmark() {
        video?.let { video ->
            viewModel.saveBookmark(
                requireContext().filesDir.path,
                TwitchApiHelper.getHelixHeaders(requireContext()),
                TwitchApiHelper.getGQLHeaders(requireContext()),
                video
            )
        }
    }

    private fun setQualityIndex() {
        val defaultQuality = prefs.getString(C.PLAYER_DEFAULTQUALITY, "saved")?.substringBefore(" ")
        viewModel.qualityIndex = when (defaultQuality) {
            "saved" -> {
                val savedQuality = prefs.getString(C.PLAYER_QUALITY, "720p60")?.substringBefore(" ")
                when (savedQuality) {
                    AUTO_QUALITY -> 0
                    AUDIO_ONLY_QUALITY -> viewModel.qualities.keys.indexOf(AUDIO_ONLY_QUALITY).takeIf { it != -1 }
                    CHAT_ONLY_QUALITY -> viewModel.qualities.keys.indexOf(CHAT_ONLY_QUALITY).takeIf { it != -1 }
                    else -> findQualityIndex(savedQuality)
                }
            }
            "Auto" -> 0
            "Source" -> {
                if (viewModel.qualities.containsKey(AUTO_QUALITY)) {
                    1
                } else {
                    0
                }
            }
            AUDIO_ONLY_QUALITY -> {
                viewModel.qualities.keys.indexOf(AUDIO_ONLY_QUALITY).takeIf { it != -1 }
            }
            CHAT_ONLY_QUALITY -> {
                viewModel.qualities.keys.indexOf(CHAT_ONLY_QUALITY).takeIf { it != -1 }
            }
            else -> findQualityIndex(defaultQuality)
        } ?: 0
    }

    private fun findQualityIndex(targetQualityString: String?): Int? {
        return targetQualityString?.split("p")?.let { targetQuality ->
            targetQuality[0].filter(Char::isDigit).toIntOrNull()?.let { targetRes ->
                val targetFps = if (targetQuality.size >= 2) targetQuality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                val qualities = viewModel.qualities.keys
                qualities.indexOf(qualities.find { qualityString ->
                    qualityString.split("p").let { quality ->
                        quality[0].filter(Char::isDigit).toIntOrNull()?.let { qualityRes ->
                            val qualityFps = if (quality.size >= 2) quality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                            (targetRes == qualityRes && targetFps >= qualityFps) || targetRes > qualityRes || qualities.indexOf(qualityString) == qualities.indexOf(AUDIO_ONLY_QUALITY) - 1
                        } == true
                    }
                }).let { if (it != -1) it else null }
            }
        }
    }

    private fun changeQuality(index: Int) {
        viewModel.previousIndex = viewModel.qualityIndex
        viewModel.qualityIndex = index
        viewModel.qualities.entries.elementAtOrNull(index)?.let { quality ->
            player?.let { player ->
                player.currentMediaItem?.let { mediaItem ->
                    when (quality.key) {
                        AUTO_QUALITY -> {
                            viewModel.playlistUrl?.let { uri ->
                                if (mediaItem.localConfiguration?.uri != uri) {
                                    val position = player.currentPosition
                                    player.setMediaItem(mediaItem.buildUpon().setUri(uri).build())
                                    player.prepare()
                                    player.seekTo(position)
                                }
                                viewModel.playlistUrl = null
                            } ?: player.prepare()
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                            }.build()
                        }
                        AUDIO_ONLY_QUALITY -> {
                            if (viewModel.usingProxy) {
                                player.sendCustomCommand(
                                    SessionCommand(
                                        PlaybackService.TOGGLE_PROXY, bundleOf(
                                            PlaybackService.USING_PROXY to false
                                        )
                                    ), Bundle.EMPTY
                                )
                                viewModel.usingProxy = false
                            }
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                            quality.value.second?.let {
                                val position = player.currentPosition
                                if (viewModel.qualities.containsKey(AUTO_QUALITY)) {
                                    viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(it).build())
                                player.prepare()
                                player.seekTo(position)
                            }
                        }
                        CHAT_ONLY_QUALITY -> {
                            if (viewModel.usingProxy) {
                                player.sendCustomCommand(
                                    SessionCommand(
                                        PlaybackService.TOGGLE_PROXY, bundleOf(
                                            PlaybackService.USING_PROXY to false
                                        )
                                    ), Bundle.EMPTY
                                )
                                viewModel.usingProxy = false
                            }
                            player.stop()
                        }
                        else -> {
                            if (viewModel.qualities.containsKey(AUTO_QUALITY)) {
                                viewModel.playlistUrl?.let { uri ->
                                    player.currentMediaItem?.let {
                                        val position = player.currentPosition
                                        player.setMediaItem(it.buildUpon().setUri(uri).build())
                                        player.prepare()
                                        player.seekTo(position)
                                        viewModel.playlistUrl = null
                                    }
                                } ?: player.prepare()
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                    if (!player.currentTracks.isEmpty) {
                                        player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.let {
                                            val trackIndex = index - 1
                                            if (trackIndex <= it.length - 1) {
                                                setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, trackIndex))
                                            }
                                        }
                                    }
                                }.build()
                            } else {
                                player.currentMediaItem?.let {
                                    if (it.localConfiguration?.uri?.toString() != quality.value.second) {
                                        val position = player.currentPosition
                                        player.setMediaItem(it.buildUpon().setUri(quality.value.second).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                }.build()
                            }
                        }
                    }
                    if (prefs.getString(C.PLAYER_DEFAULTQUALITY, "saved") == "saved") {
                        prefs.edit { putString(C.PLAYER_QUALITY, quality.key) }
                    }
                }
            }
        }
    }

    private fun changePlayerMode() {
        with(binding) {
            if (enterPictureInPicture()) {
                playerView.controllerHideOnTouch = true
                playerView.controllerShowTimeoutMs = 3000
                if (requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    prefs.getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)
                ) {
                    requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(true).build())
                }
            } else {
                playerView.controllerHideOnTouch = false
                playerView.controllerShowTimeoutMs = -1
                playerView.showController()
                requireView().keepScreenOn = true
                if (requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
                }
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.getInsetsController(
                requireActivity().window,
                requireActivity().window.decorView
            ).show(WindowInsetsCompat.Type.systemBars())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (isAdded) {
                    @Suppress("DEPRECATION")
                    requireActivity().window.decorView.systemUiVisibility = 0
                }
            }
        }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.getInsetsController(
                requireActivity().window,
                requireActivity().window.decorView
            ).hide(WindowInsetsCompat.Type.systemBars())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (isAdded) {
                    @Suppress("DEPRECATION")
                    requireActivity().window.decorView.systemUiVisibility = systemUiFlags
                }
            }
        }
    }

    fun getCurrentSpeed() = player?.playbackParameters?.speed

    fun getCurrentPosition() = player?.currentPosition

    fun getIsPortrait() = isPortrait

    fun reloadEmotes() = chatFragment?.reloadEmotes()

    fun isActive() = chatFragment?.isActive()

    fun disconnect() = chatFragment?.disconnect()

    fun reconnect() = chatFragment?.reconnect()

    fun secondViewIsHidden() = binding.slidingLayout.secondView?.isVisible == false

    fun enterPictureInPicture(): Boolean {
        val quality = viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex)
        return quality != AUDIO_ONLY_QUALITY && quality != CHAT_ONLY_QUALITY
    }

    override fun onStart() {
        super.onStart()
        controllerFuture = MediaController.Builder(
            requireContext(),
            SessionToken(
                requireContext(),
                ComponentName(requireContext(), PlaybackService::class.java)
            )
        ).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            binding.playerView.player = controller
            if (!viewModel.started && offlineVideo == null) {
                player?.removeMediaItem(0)
            }
            controller?.addListener(object : Player.Listener {

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        chatFragment?.updatePosition(newPosition.positionMs)
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    chatFragment?.updateSpeed(playbackParameters.speed)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!prefs.getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false)) {
                        requireView().keepScreenOn = isPlaying
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (!tracks.isEmpty && !viewModel.loaded.value) {
                        viewModel.loaded.value = true
                        toggleSubtitles(prefs.getBoolean(C.PLAYER_SUBTITLES_ENABLED, false))
                    }
                    setSubtitles()
                    if (!tracks.isEmpty
                        && viewModel.qualities.containsKey(AUTO_QUALITY)
                        && viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) != AUDIO_ONLY_QUALITY
                        && !viewModel.hidden) {
                        changeQuality(viewModel.qualityIndex)
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (viewModel.qualities.isEmpty()) {
                        player?.sendCustomCommand(
                            SessionCommand(PlaybackService.GET_QUALITIES, Bundle.EMPTY),
                            Bundle.EMPTY
                        )?.let { result ->
                            result.addListener({
                                if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                    val names = result.get().extras.getStringArray(PlaybackService.NAMES)
                                    val codecs = result.get().extras.getStringArray(PlaybackService.CODECS)?.map { codec ->
                                        codec.substringBefore('.').let {
                                            when (it) {
                                                "av01" -> "AV1"
                                                "hvc1" -> "H.265"
                                                "avc1" -> "H.264"
                                                else -> it
                                            }
                                        }
                                    }?.takeUnless { it.all { it == "H.264" || it == "mp4a" } }
                                    val urls = result.get().extras.getStringArray(PlaybackService.URLS)
                                    if (!names.isNullOrEmpty() && !urls.isNullOrEmpty()) {
                                        val map = mutableMapOf<String, Pair<String, String?>>()
                                        map[AUTO_QUALITY] = Pair(requireContext().getString(R.string.auto), null)
                                        names.forEachIndexed { index, quality ->
                                            urls.getOrNull(index)?.let { url ->
                                                if (quality.startsWith("audio", true)) {
                                                    map[AUDIO_ONLY_QUALITY] = Pair(requireContext().getString(R.string.audio_only), url)
                                                } else {
                                                    map[quality] = Pair(codecs?.getOrNull(index)?.let { "$quality $it" } ?: quality, url)
                                                }
                                            }
                                        }
                                        map.put(AUDIO_ONLY_QUALITY, map.remove(AUDIO_ONLY_QUALITY) //move audio option to bottom
                                            ?: Pair(requireContext().getString(R.string.audio_only), null))
                                        if (stream != null) {
                                            map[CHAT_ONLY_QUALITY] = Pair(requireContext().getString(R.string.chat_only), null)
                                        }
                                        viewModel.qualities = map
                                        setQualityIndex()
                                        if (viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) == AUDIO_ONLY_QUALITY) {
                                            changeQuality(viewModel.qualityIndex)
                                        }
                                    }
                                }
                            }, MoreExecutors.directExecutor())
                        }
                    }
                    if (stream != null) {
                        val hideAds = prefs.getBoolean(C.PLAYER_HIDE_ADS, false)
                        val useProxy = prefs.getBoolean(C.PROXY_MEDIA_PLAYLIST, true)
                                && !prefs.getString(C.PROXY_HOST, null).isNullOrBlank()
                                && prefs.getString(C.PROXY_PORT, null)?.toIntOrNull() != null
                        if (hideAds || useProxy) {
                            player?.sendCustomCommand(
                                SessionCommand(PlaybackService.CHECK_ADS, Bundle.EMPTY),
                                Bundle.EMPTY
                            )?.let { result ->
                                result.addListener({
                                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                        val playingAds = result.get().extras.getBoolean(PlaybackService.RESULT)
                                        val oldValue = viewModel.playingAds
                                        viewModel.playingAds = playingAds
                                        if (playingAds) {
                                            if (viewModel.usingProxy) {
                                                if (!viewModel.stopProxy) {
                                                    player?.sendCustomCommand(
                                                        SessionCommand(
                                                            PlaybackService.TOGGLE_PROXY, bundleOf(
                                                                PlaybackService.USING_PROXY to false
                                                            )
                                                        ), Bundle.EMPTY
                                                    )
                                                    viewModel.usingProxy = false
                                                    viewModel.stopProxy = true
                                                }
                                            } else {
                                                if (!oldValue) {
                                                    val playlist = viewModel.qualities.values.elementAtOrNull(viewModel.qualityIndex)?.second
                                                    if (!viewModel.stopProxy && !playlist.isNullOrBlank() && useProxy) {
                                                        player?.sendCustomCommand(
                                                            SessionCommand(
                                                                PlaybackService.TOGGLE_PROXY, bundleOf(
                                                                    PlaybackService.USING_PROXY to true
                                                                )
                                                            ), Bundle.EMPTY
                                                        )
                                                        viewModel.usingProxy = true
                                                        viewLifecycleOwner.lifecycleScope.launch {
                                                            for (i in 0 until 10) {
                                                                delay(10000)
                                                                if (!viewModel.checkPlaylist(playlist)) {
                                                                    break
                                                                }
                                                            }
                                                            player?.sendCustomCommand(
                                                                SessionCommand(
                                                                    PlaybackService.TOGGLE_PROXY, bundleOf(
                                                                        PlaybackService.USING_PROXY to false
                                                                    )
                                                                ), Bundle.EMPTY
                                                            )
                                                            viewModel.usingProxy = false
                                                        }
                                                    } else {
                                                        if (hideAds) {
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
                    }
                }
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                        if (stream != null && !prefs.getBoolean(C.PLAYER_PAUSE, false)) {
                            requireView().findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.apply {
                                if (player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady) {
                                    gone()
                                } else {
                                    visible()
                                }
                            }
                        }
                        setPipActions(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(tag, "Player error", error)
                    when {
                        stream != null -> {
                            player?.sendCustomCommand(
                                SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY),
                                Bundle.EMPTY
                            )?.let { result ->
                                result.addListener({
                                    if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                                        val responseCode = result.get().extras.getInt(PlaybackService.RESULT)
                                        if (requireContext().isNetworkAvailable) {
                                            when {
                                                responseCode == 404 -> {
                                                    requireContext().toast(R.string.stream_ended)
                                                }
                                                viewModel.useCustomProxy && responseCode >= 400 -> {
                                                    requireContext().toast(R.string.proxy_error)
                                                    viewModel.useCustomProxy = false
                                                    viewLifecycleOwner.lifecycleScope.launch {
                                                        delay(1500L)
                                                        try {
                                                            restartPlayer()
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    requireContext().shortToast(R.string.player_error)
                                                    viewLifecycleOwner.lifecycleScope.launch {
                                                        delay(1500L)
                                                        try {
                                                            restartPlayer()
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }, MoreExecutors.directExecutor())
                            }
                        }
                        video != null -> {
                            player?.sendCustomCommand(
                                SessionCommand(PlaybackService.GET_ERROR_CODE, Bundle.EMPTY),
                                Bundle.EMPTY
                            )?.let { result ->
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
                                                        } catch (e: Exception) {
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }, MoreExecutors.directExecutor())
                            }
                        }
                    }
                }
            })
            if (viewModel.restoreQuality) {
                viewModel.restoreQuality = false
                changeQuality(viewModel.previousIndex)
            }
            if (viewModel.background) {
                viewModel.background = false
                player?.sendCustomCommand(
                    SessionCommand(
                        PlaybackService.SET_SLEEP_TIMER,
                        Bundle.EMPTY
                    ), Bundle.EMPTY
                )
            }
            if (viewModel.resume) {
                viewModel.resume = false
                player?.playWhenReady = true
                player?.prepare()
            }
            player?.let { player ->
                if (viewModel.loaded.value && player.currentMediaItem == null) {
                    viewModel.started = false
                }
            }
            if ((isInitialized || !enableNetworkCheck) && !viewModel.started) {
                startPlayer()
            }
            player?.let { player ->
                setPipActions(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setPipActions(playing: Boolean) {
        if (requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            prefs.getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)
        ) {
            requireActivity().setPictureInPictureParams(
                PictureInPictureParams.Builder().apply {
                    setActions(listOf(
                        RemoteAction(
                            Icon.createWithResource(requireContext(), R.drawable.baseline_audiotrack_black_24),
                            requireContext().getString(R.string.audio_only),
                            requireContext().getString(R.string.audio_only),
                            PendingIntent.getBroadcast(
                                requireContext(),
                                REQUEST_CODE_AUDIO_ONLY,
                                Intent(MainActivity.INTENT_START_AUDIO_ONLY).setPackage(requireContext().packageName),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ),
                        if (playing) {
                            RemoteAction(
                                Icon.createWithResource(requireContext(), R.drawable.baseline_pause_black_48),
                                requireContext().getString(R.string.pause),
                                requireContext().getString(R.string.pause),
                                PendingIntent.getBroadcast(
                                    requireContext(),
                                    REQUEST_CODE_PLAY_PAUSE,
                                    Intent(MainActivity.INTENT_PLAY_PAUSE_PLAYER).setPackage(requireContext().packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        } else {
                            RemoteAction(
                                Icon.createWithResource(requireContext(), R.drawable.baseline_play_arrow_black_48),
                                requireContext().getString(R.string.resume),
                                requireContext().getString(R.string.resume),
                                PendingIntent.getBroadcast(
                                    requireContext(),
                                    REQUEST_CODE_PLAY_PAUSE,
                                    Intent(MainActivity.INTENT_PLAY_PAUSE_PLAYER).setPackage(requireContext().packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    ))
                }.build()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requireActivity().isInPictureInPictureMode) {
            binding.playerView.useController = false
        }
    }

    override fun initialize() {
        if (player != null && !viewModel.started) {
            startPlayer()
        }
        if (offlineVideo == null) {
            viewModel.isFollowingChannel(
                TwitchApiHelper.getHelixHeaders(requireContext()),
                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                requireContext().tokenPrefs().getString(C.USER_ID, null),
                stream?.channelId ?: video?.channelId ?: clip?.channelId,
                stream?.channelLogin ?: video?.channelLogin ?: clip?.channelLogin,
            )
            if (!video?.id.isNullOrBlank() && (prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false))) {
                viewModel.loadGamesList(TwitchApiHelper.getGQLHeaders(requireContext()), video?.id)
            }
        }
    }

    private fun startPlayer() {
        viewModel.started = true
        stream?.let { stream ->
            viewModel.useCustomProxy = prefs.getBoolean(C.PLAYER_STREAM_PROXY, false)
            if (viewModel.stream.value == null) {
                viewModel.stream.value = stream
                loadStream(stream)
                viewModel.loadStream(
                    stream = stream,
                    loop = requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) ||
                            !requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                            (requireContext().prefs().getBoolean(C.CHAT_POINTS_COLLECT, true) &&
                                    !requireContext().tokenPrefs().getString(C.USER_ID, null).isNullOrBlank() &&
                                    !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank()),
                    helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                    checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                            requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                )
            }
        } ?:
        video?.let { video ->
            if (requireArguments().getBoolean(KEY_IGNORE_SAVED_POSITION) && !viewModel.loaded.value) {
                playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, requireArguments().getDouble(KEY_OFFSET).toLong())
            } else {
                val id = video.id?.toLongOrNull()
                if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && id != null) {
                    viewModel.getVideoPosition(id)
                } else {
                    playVideo((prefs.getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, 0)
                }
            }
        } ?:
        clip?.let { clip ->
            val skipAccessToken = prefs.getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
            if (skipAccessToken >= 2 || clip.thumbnailUrl.isNullOrBlank()) {
                viewModel.loadClip(TwitchApiHelper.getGQLHeaders(requireContext()), clip.id)
            } else {
                val urls = TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                val map = mutableMapOf<String, Pair<String, String?>>()
                urls.forEach {
                    if (it.key == "source") {
                        map[it.key] = Pair(requireContext().getString(R.string.source), it.value)
                    } else {
                        map[it.key] = Pair(it.key, it.value)
                    }
                }
                map.put(AUDIO_ONLY_QUALITY, Pair(requireContext().getString(R.string.audio_only), null))
                viewModel.qualities = map
                setQualityIndex()
                player?.let { player ->
                    val quality = viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)
                    if (quality?.key == AUDIO_ONLY_QUALITY) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                        }.build()
                    } else {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                        }.build()
                    }
                    (quality?.value?.second ?: viewModel.qualities.values.firstOrNull()?.second)?.let { url ->
                        player.sendCustomCommand(
                            SessionCommand(
                                PlaybackService.START_CLIP, bundleOf(
                                    PlaybackService.URI to url,
                                    PlaybackService.TITLE to clip.title,
                                    PlaybackService.CHANNEL_NAME to clip.channelName,
                                    PlaybackService.CHANNEL_LOGO to clip.channelLogo,
                                )
                            ), Bundle.EMPTY
                        )
                    }
                }
            }
        } ?:
        offlineVideo?.let { offlineVideo ->
            if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                viewModel.getOfflineVideo(offlineVideo.id)
            } else {
                viewModel.qualities = mapOf(
                    "source" to Pair(requireContext().getString(R.string.source), offlineVideo.url),
                    AUDIO_ONLY_QUALITY to Pair(requireContext().getString(R.string.audio_only), null)
                )
                setQualityIndex()
                player?.let { player ->
                    val quality = viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)
                    if (quality?.key == AUDIO_ONLY_QUALITY) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                        }.build()
                    } else {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                        }.build()
                    }
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.START_OFFLINE_VIDEO, bundleOf(
                                PlaybackService.URI to offlineVideo.url,
                                PlaybackService.VIDEO_ID to offlineVideo.id,
                                PlaybackService.PLAYBACK_POSITION to 0L,
                                PlaybackService.TITLE to offlineVideo.name,
                                PlaybackService.CHANNEL_NAME to offlineVideo.channelName,
                                PlaybackService.CHANNEL_LOGO to offlineVideo.channelLogo,
                            )
                        ), Bundle.EMPTY
                    )
                }
            }
        }
    }

    private fun loadStream(stream: Stream?) {
        player?.prepare()
        try {
            stream?.channelLogin?.let { channelLogin ->
                val proxyUrl = prefs.getString(C.PLAYER_PROXY_URL, "")
                if (viewModel.useCustomProxy && !proxyUrl.isNullOrBlank()) {
                    player?.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.START_STREAM, bundleOf(
                                PlaybackService.URI to proxyUrl.replace("\$channel", channelLogin),
                                PlaybackService.TITLE to stream.title,
                                PlaybackService.CHANNEL_NAME to stream.channelName,
                                PlaybackService.CHANNEL_LOGO to stream.channelLogo,
                            )
                        ), Bundle.EMPTY
                    )
                    player?.prepare()
                } else {
                    if (viewModel.useCustomProxy) {
                        viewModel.useCustomProxy = false
                    }
                    val proxyHost = prefs.getString(C.PROXY_HOST, null)
                    val proxyPort = prefs.getString(C.PROXY_PORT, null)?.toIntOrNull()
                    val proxyMultivariantPlaylist = prefs.getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, false) && !proxyHost.isNullOrBlank() && proxyPort != null
                    viewModel.loadStreamResult(
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
            }
        } catch (e: Exception) {
            requireContext().toast(R.string.error_stream)
        }
    }

    private fun playVideo(skipAccessToken: Boolean, playbackPosition: Long?) {
        if (skipAccessToken && !video?.animatedPreviewURL.isNullOrBlank()) {
            video?.animatedPreviewURL?.let { preview ->
                val qualityMap = TwitchApiHelper.getVideoUrlMapFromPreview(preview, video?.type)
                val map = mutableMapOf<String, Pair<String, String?>>()
                qualityMap.forEach {
                    if (it.key == "source") {
                        map[it.key] = Pair(requireContext().getString(R.string.source), it.value)
                    } else {
                        map[it.key] = Pair(it.key, it.value)
                    }
                }
                map.put(AUDIO_ONLY_QUALITY, map.remove(AUDIO_ONLY_QUALITY) //move audio option to bottom
                    ?: Pair(requireContext().getString(R.string.audio_only), null))
                viewModel.qualities = map
                viewModel.qualityIndex = 0
                map.values.firstOrNull()?.second
            }?.let { url ->
                player?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                    }.build()
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.START_VIDEO, bundleOf(
                                PlaybackService.URI to url,
                                PlaybackService.PLAYBACK_POSITION to playbackPosition,
                                PlaybackService.VIDEO_ID to video?.id?.toLongOrNull(),
                                PlaybackService.TITLE to video?.title,
                                PlaybackService.CHANNEL_NAME to video?.channelName,
                                PlaybackService.CHANNEL_LOGO to video?.channelLogo,
                            )
                        ), Bundle.EMPTY
                    )
                }
            }
        } else {
            viewModel.playbackPosition = playbackPosition
            viewModel.loadVideo(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                videoId = video?.id,
                playerType = prefs.getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        with(binding) {
            isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
            slidingLayout.updateBackgroundColor(isPortrait)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !requireActivity().isInPictureInPictureMode) {
                chatLayout.hideKeyboard()
                chatLayout.clearFocus()
                initLayout()
            }
            (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.dismiss()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                slidingLayout.apply {
                    orientation = if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    init()
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        with(binding) {
            if (isInPictureInPictureMode) {
                if (!slidingLayout.isMaximized) {
                    slidingLayout.maximize()
                }
                playerView.useController = false
                chatLayout.gone()
                // player dialog
                (childFragmentManager.findFragmentByTag("closeOnPip") as? BottomSheetDialogFragment)?.dismiss()
                // player chat message dialog
                (chatFragment?.childFragmentManager?.findFragmentByTag("messageDialog") as? BottomSheetDialogFragment)?.dismiss()
                (chatFragment?.childFragmentManager?.findFragmentByTag("replyDialog") as? BottomSheetDialogFragment)?.dismiss()
                (chatFragment?.childFragmentManager?.findFragmentByTag("imageDialog") as? BottomSheetDialogFragment)?.dismiss()
            } else {
                playerView.useController = true
            }
        }
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            if (stream != null) {
                restartPlayer()
            } else {
                player?.prepare()
            }
        }
    }

    override fun onNetworkLost() {
        if (stream == null && isResumed) {
            player?.stop()
        }
    }

    fun handlePlayPauseAction() {
        Util.handlePlayPauseButtonAction(player)
    }

    fun startAudioOnly() {
        player?.let { player ->
            if (player.isConnected) {
                savePosition()
                if (viewModel.usingProxy) {
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.TOGGLE_PROXY, bundleOf(
                                PlaybackService.USING_PROXY to false
                            )
                        ), Bundle.EMPTY
                    )
                    viewModel.usingProxy = false
                }
                if (viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) != AUDIO_ONLY_QUALITY) {
                    viewModel.restoreQuality = true
                    viewModel.previousIndex = viewModel.qualityIndex
                    viewModel.qualityIndex = viewModel.qualities.keys.indexOf(AUDIO_ONLY_QUALITY)
                    viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)?.let { quality ->
                        player.currentMediaItem?.let { mediaItem ->
                            if (prefs.getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                            }
                            if (prefs.getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                quality.value.second?.let {
                                    val position = player.currentPosition
                                    if (viewModel.qualities.containsKey(AUTO_QUALITY)) {
                                        viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                    }
                                    player.setMediaItem(mediaItem.buildUpon().setUri(it).build())
                                    player.prepare()
                                    player.seekTo(position)
                                }
                            }
                        }
                    }
                }
                viewModel.background = true
                player.sendCustomCommand(
                    SessionCommand(
                        PlaybackService.SET_SLEEP_TIMER, bundleOf(
                            PlaybackService.DURATION to ((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
                        )
                    ), Bundle.EMPTY
                )
            }
        }
        releaseController()
    }

    override fun onStop() {
        super.onStop()
        player?.let { player ->
            if (player.isConnected) {
                savePosition()
                if (viewModel.usingProxy) {
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.TOGGLE_PROXY, bundleOf(
                                PlaybackService.USING_PROXY to false
                            )
                        ), Bundle.EMPTY
                    )
                    viewModel.usingProxy = false
                }
                if (prefs.getBoolean(C.PLAYER_BACKGROUND_AUDIO, true)
                    && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requireActivity().isInPictureInPictureMode)
                ) {
                    if (player.playWhenReady == true && viewModel.qualities.keys.elementAtOrNull(viewModel.qualityIndex) != AUDIO_ONLY_QUALITY) {
                        viewModel.restoreQuality = true
                        viewModel.previousIndex = viewModel.qualityIndex
                        viewModel.qualityIndex = viewModel.qualities.keys.indexOf(AUDIO_ONLY_QUALITY)
                        viewModel.qualities.entries.elementAtOrNull(viewModel.qualityIndex)?.let { quality ->
                            player.currentMediaItem?.let { mediaItem ->
                                if (prefs.getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                    }.build()
                                }
                                if (prefs.getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                    quality.value.second?.let {
                                        val position = player.currentPosition
                                        if (viewModel.qualities.containsKey(AUTO_QUALITY)) {
                                            viewModel.playlistUrl = mediaItem.localConfiguration?.uri
                                        }
                                        player.setMediaItem(mediaItem.buildUpon().setUri(it).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                            }
                        }
                    }
                    viewModel.background = true
                    player.sendCustomCommand(
                        SessionCommand(
                            PlaybackService.SET_SLEEP_TIMER, bundleOf(
                                PlaybackService.DURATION to ((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
                            )
                        ), Bundle.EMPTY
                    )
                } else {
                    viewModel.resume = player.playWhenReady
                    player.pause()
                }
            }
        }
        releaseController()
    }

    private fun savePosition() {
        video?.let { video ->
            if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                video.id?.toLongOrNull()?.let { id ->
                    player?.currentPosition?.let { position ->
                        viewModel.saveVideoPosition(id, position)
                    }
                }
            }
        } ?:
        offlineVideo?.let { offlineVideo ->
            if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                player?.currentPosition?.let { position ->
                    viewModel.saveOfflineVideoPosition(offlineVideo.id, position)
                }
            }
        }
    }

    private fun releaseController() {
        binding.playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    override fun onMinimize() {
        with(binding) {
            chatLayout.hideKeyboard()
            chatLayout.clearFocus()
            if (stream != null && chatFragment?.emoteMenuIsVisible() == true) {
                chatFragment?.toggleBackPressedCallback(false)
            }
            backPressedCallback.remove()
            playerView.useController = false
            if (!isPortrait) {
                showStatusBar()
                val activity = requireActivity()
                activity.lifecycleScope.launch {
                    delay(500L)
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    override fun onMaximize() {
        with(binding) {
            requireActivity().onBackPressedDispatcher.addCallback(this@PlayerFragment, backPressedCallback)
            if (stream != null && chatFragment?.emoteMenuIsVisible() == true) {
                chatFragment?.toggleBackPressedCallback(true)
            }
            playerView.useController = true
            if (!playerView.controllerHideOnTouch) { //TODO
                playerView.showController()
            }
            if (!isPortrait) {
                hideStatusBar()
            }
        }
    }

    override fun onClose() {
        savePosition()
        player?.pause()
        player?.stop()
        if (offlineVideo == null) {
            player?.removeMediaItem(0)
        }
        releaseController()
    }

    override fun showDownloadDialog() {
        if (viewModel.loaded.value && DownloadUtils.hasStoragePermission(requireActivity())) {
            stream?.let { stream ->
                val qualities = viewModel.qualities.values.mapNotNull { pair ->
                    pair.second?.let { pair.first to it }
                }
                DownloadDialog.newInstance(stream, qualities.map { it.first }.toTypedArray(), qualities.map { it.second }.toTypedArray()).show(childFragmentManager, null)
            } ?:
            video?.let { video ->
                player?.sendCustomCommand(
                    SessionCommand(PlaybackService.GET_DURATION, Bundle.EMPTY),
                    Bundle.EMPTY
                )?.let { result ->
                    result.addListener({
                        if (result.get().resultCode == SessionResult.RESULT_SUCCESS) {
                            val totalDuration = result.get().extras.getLong(PlaybackService.RESULT)
                            val qualities = viewModel.qualities.values.mapNotNull { pair ->
                                pair.second?.let { pair.first to it }
                            }
                            DownloadDialog.newInstance(video, qualities.map { it.first }.toTypedArray(), qualities.map { it.second }.toTypedArray(), totalDuration, player?.currentPosition).show(childFragmentManager, null)
                        }
                    }, MoreExecutors.directExecutor())
                }
            } ?:
            clip?.let { clip ->
                val qualities = viewModel.qualities.values.mapNotNull { pair ->
                    pair.second?.let { pair.first to it }
                }
                DownloadDialog.newInstance(clip, qualities.map { it.first }.toTypedArray(), qualities.map { it.second }.toTypedArray()).show(childFragmentManager, null)
            }
        }
    }

    override fun seek(position: Long) {
        player?.seekTo(position)
    }

    override fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean) {
        if (durationMs > 0L) {
            requireContext().toast(
                when {
                    hours == 0 -> getString(
                        R.string.playback_will_stop,
                        resources.getQuantityString(R.plurals.minutes, minutes, minutes)
                    )
                    minutes == 0 -> getString(
                        R.string.playback_will_stop,
                        resources.getQuantityString(R.plurals.hours, hours, hours)
                    )
                    else -> getString(
                        R.string.playback_will_stop_hours_minutes,
                        resources.getQuantityString(R.plurals.hours, hours, hours),
                        resources.getQuantityString(R.plurals.minutes, minutes, minutes)
                    )
                }
            )
        } else if (((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L) {
            requireContext().toast(R.string.timer_canceled)
        }
        if (lockScreen != prefs.getBoolean(C.SLEEP_TIMER_LOCK, false)) {
            prefs.edit { putBoolean(C.SLEEP_TIMER_LOCK, lockScreen) }
        }
        (activity as? MainActivity)?.setSleepTimer(durationMs)
    }

    override fun onChange(requestCode: Int, index: Int, text: CharSequence, tag: Int?) {
        when (requestCode) {
            REQUEST_CODE_QUALITY -> {
                changeQuality(index)
                changePlayerMode()
                setQualityText()
            }
            REQUEST_CODE_SPEED -> {
                prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.let { speeds ->
                    speeds.getOrNull(index)?.toFloatOrNull()?.let { speed ->
                        player?.setPlaybackSpeed(speed)
                        prefs.edit { putFloat(C.PLAYER_SPEED, speed) }
                        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSpeed(
                            speeds.find { it == player?.playbackParameters?.speed.toString() }
                        )
                    }
                }
            }
        }
    }

    override fun changeVolume(volume: Float) {
        player?.volume = volume
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    when (callback) {
                        "refreshStream" -> {
                            stream?.let { stream ->
                                stream.channelLogin?.let { channelLogin ->
                                    val proxyHost = prefs.getString(C.PROXY_HOST, null)
                                    val proxyPort = prefs.getString(C.PROXY_PORT, null)?.toIntOrNull()
                                    val proxyMultivariantPlaylist = prefs.getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, false) && !proxyHost.isNullOrBlank() && proxyPort != null
                                    viewModel.loadStreamResult(
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
                                viewModel.isFollowingChannel(
                                    TwitchApiHelper.getHelixHeaders(requireContext()),
                                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                    prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                                    stream.channelId,
                                    stream.channelLogin
                                )
                            }
                        }
                        "refreshVideo" -> {
                            video?.let { video ->
                                viewModel.loadVideo(
                                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                                    videoId = video.id,
                                    playerType = prefs.getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                                    supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                                    enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
                                )
                                viewModel.isFollowingChannel(
                                    TwitchApiHelper.getHelixHeaders(requireContext()),
                                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                    prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                                    video.channelId,
                                    video.channelLogin
                                )
                                if (!video.id.isNullOrBlank() && (prefs.getBoolean(C.PLAYER_GAMESBUTTON, true) || prefs.getBoolean(C.PLAYER_MENU_GAMES, false))) {
                                    viewModel.loadGamesList(TwitchApiHelper.getGQLHeaders(requireContext()), video.id)
                                }
                            }
                        }
                        "refreshClip" -> {
                            clip?.let { clip ->
                                viewModel.loadClip(TwitchApiHelper.getGQLHeaders(requireContext()), clip.id)
                                viewModel.isFollowingChannel(
                                    TwitchApiHelper.getHelixHeaders(requireContext()),
                                    TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                    prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                    requireContext().tokenPrefs().getString(C.USER_ID, null),
                                    clip.channelId,
                                    clip.channelLogin
                                )
                            }
                        }
                        "follow" -> viewModel.saveFollowChannel(
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().tokenPrefs().getString(C.USER_ID, null),
                            stream?.channelId ?: video?.channelId ?: clip?.channelId,
                            stream?.channelLogin ?: video?.channelLogin ?: clip?.channelLogin,
                            stream?.channelName ?: video?.channelName ?: clip?.channelName,
                            requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                            stream?.startedAt
                        )
                        "unfollow" -> viewModel.deleteFollowChannel(
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().tokenPrefs().getString(C.USER_ID, null),
                            stream?.channelId ?: video?.channelId ?: clip?.channelId
                        )
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
        private const val AUTO_QUALITY = "auto"
        private const val AUDIO_ONLY_QUALITY = "audio_only"
        private const val CHAT_ONLY_QUALITY = "chat_only"

        private const val REQUEST_CODE_QUALITY = 0
        private const val REQUEST_CODE_SPEED = 1
        private const val REQUEST_CODE_AUDIO_ONLY = 2
        private const val REQUEST_CODE_PLAY_PAUSE = 3

        private const val KEY_STREAM = "stream"
        private const val KEY_VIDEO = "video"
        private const val KEY_CLIP = "clip"
        private const val KEY_OFFLINE_VIDEO = "offlineVideo"
        private const val KEY_OFFSET = "offset"
        private const val KEY_IGNORE_SAVED_POSITION = "ignoreSavedPosition"

        fun newInstance(stream: Stream): PlayerFragment {
            return PlayerFragment().apply {
                arguments = bundleOf(
                    KEY_STREAM to stream
                )
            }
        }

        fun newInstance(video: Video, offset: Double? = null, ignoreSavedPosition: Boolean = false): PlayerFragment {
            return PlayerFragment().apply {
                arguments = bundleOf(
                    KEY_VIDEO to video,
                    KEY_OFFSET to offset,
                    KEY_IGNORE_SAVED_POSITION to ignoreSavedPosition
                )
            }
        }

        fun newInstance(clip: Clip): PlayerFragment {
            return PlayerFragment().apply {
                arguments = bundleOf(
                    KEY_CLIP to clip
                )
            }
        }

        fun newInstance(video: OfflineVideo): PlayerFragment {
            return PlayerFragment().apply {
                arguments = bundleOf(
                    KEY_OFFLINE_VIDEO to video
                )
            }
        }
    }
}
