package com.github.andreyasadchy.xtra.ui.player

import android.app.PictureInPictureParams
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.trackPipAnimationHintView
import androidx.core.content.edit
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.RadioButtonDialogFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.clip.ClipPlayerFragment
import com.github.andreyasadchy.xtra.ui.player.offline.OfflinePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.stream.StreamPlayerFragment
import com.github.andreyasadchy.xtra.ui.view.CustomPlayerView
import com.github.andreyasadchy.xtra.ui.view.SlidingLayout
import com.github.andreyasadchy.xtra.util.*
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Suppress("PLUGIN_WARNING")
abstract class BasePlayerFragment : BaseNetworkFragment(), LifecycleListener, SlidingLayout.Listener, SleepTimerDialog.OnSleepTimerStartedListener, RadioButtonDialogFragment.OnSortOptionChanged, PlayerVolumeDialog.PlayerVolumeListener {

    companion object {
        val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val SPEED_LABELS = listOf(R.string.speed0_25, R.string.speed0_5, R.string.speed0_75, R.string.speed1, R.string.speed1_25, R.string.speed1_5, R.string.speed1_75, R.string.speed2)
        private const val REQUEST_CODE_QUALITY = 0
        private const val REQUEST_CODE_SPEED = 1
    }

    lateinit var slidingLayout: SlidingLayout
    private lateinit var playerView: CustomPlayerView
    private lateinit var aspectRatioFrameLayout: AspectRatioFrameLayout
    private lateinit var chatLayout: ViewGroup

    protected abstract val viewModel: PlayerViewModel

    var isPortrait = false
        private set
    private var isKeyboardShown = false

    protected abstract val shouldEnterPictureInPicture: Boolean
    open val controllerAutoShow: Boolean = true
    open val controllerShowTimeoutMs: Int = 3000
    private var resizeMode = 0

    protected lateinit var prefs: SharedPreferences

    private var chatWidthLandscape = 0

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            minimize()
        }
    }

    private var systemUiFlags = (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = requireActivity()
        prefs = activity.prefs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            systemUiFlags = systemUiFlags or (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        isPortrait = activity.isInPortraitOrientation
        activity.onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MainActivity
        slidingLayout = view as SlidingLayout
        slidingLayout.addListener(activity)
        slidingLayout.addListener(this)
        slidingLayout.maximizedSecondViewVisibility = if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) View.VISIBLE else View.GONE //TODO
        playerView = view.findViewById(R.id.playerView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    requireActivity().trackPipAnimationHintView(playerView)
                }
            }
        }
        chatLayout = if (this is ClipPlayerFragment) view.findViewById(R.id.clipChatContainer) else view.findViewById(R.id.chatFragmentContainer)
        aspectRatioFrameLayout = view.findViewById(R.id.aspectRatioFrameLayout)
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
        playerView.controllerAutoShow = controllerAutoShow
        if (this !is OfflinePlayerFragment) {
            view.findViewById<ImageButton>(R.id.playerSettings)?.disable()
            if (this !is StreamPlayerFragment) {
                view.findViewById<ImageButton>(R.id.playerDownload)?.disable()
            }
            if (this !is ClipPlayerFragment) {
                view.findViewById<ImageButton>(R.id.playerMode)?.disable()
            }
        }
        if (prefs.getBoolean(C.PLAYER_DOUBLETAP, true) && !prefs.getBoolean(C.CHAT_DISABLE, false)) {
            playerView.setOnDoubleTapListener {
                if (!isPortrait && slidingLayout.isMaximized && this !is OfflinePlayerFragment) {
                    if (chatLayout.isVisible) {
                        hideChat()
                    } else {
                        showChat()
                    }
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
        if (this is StreamPlayerFragment) {
            if (!Account.get(activity).login.isNullOrBlank() && (!Account.get(activity).gqlToken.isNullOrBlank() || !Account.get(activity).helixToken.isNullOrBlank())) {
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
        } else {
            if (prefs.getBoolean(C.PLAYER_SPEEDBUTTON, false)) {
                view.findViewById<ImageButton>(R.id.playerSpeed)?.apply {
                    visible()
                    setOnClickListener { showSpeedDialog() }
                }
            }
        }
        viewModel.playerUpdated.observe(viewLifecycleOwner) {
            playerView.player = viewModel.player
        }
        viewModel.playerMode.observe(viewLifecycleOwner) {
            if (it == PlayerMode.NORMAL) {
                playerView.controllerHideOnTouch = true
                playerView.controllerShowTimeoutMs = controllerShowTimeoutMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.getString(C.PLAYER_BACKGROUND_PLAYBACK, "0") == "0") {
                    activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(true).build())
                }
            } else {
                playerView.controllerHideOnTouch = false
                playerView.controllerShowTimeoutMs = -1
                playerView.showController()
                view.keepScreenOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
                }
            }
        }
        if (this !is ClipPlayerFragment) {
            viewModel.sleepTimer.observe(viewLifecycleOwner) {
                onMinimize()
                activity.closePlayer()
                if (prefs.getBoolean(C.SLEEP_TIMER_LOCK, true)) {
                    lockScreen()
                }
            }
            if (prefs.getBoolean(C.PLAYER_SLEEP, false)) {
                view.findViewById<ImageButton>(R.id.playerSleepTimer)?.apply {
                    visible()
                    setOnClickListener { showSleepTimerDialog() }
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false)) {
            view.keepScreenOn = true
        } else {
            viewModel.isPlaying.observe(viewLifecycleOwner) {
                view.keepScreenOn = it
            }
        }
        viewModel.subtitlesAvailable.observe(viewLifecycleOwner) {
            setSubtitles(available = it)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !requireActivity().isInPictureInPictureMode) {
            chatLayout.hideKeyboard()
            chatLayout.clearFocus()
            initLayout()
        }
        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.dismiss()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            playerView.useController = false
            chatLayout.gone()
            if (!slidingLayout.isMaximized) {
                slidingLayout.maximize()
            }
            // player dialog
            (childFragmentManager.findFragmentByTag("closeOnPip") as? BottomSheetDialogFragment?)?.dismiss()
            // player chat message dialog
            (childFragmentManager.findFragmentById(R.id.chatFragmentContainer)?.childFragmentManager?.findFragmentByTag("closeOnPip") as? BottomSheetDialogFragment?)?.dismiss()
        } else {
            playerView.useController = true
        }
    }

    override fun onMinimize() {
        if (this@BasePlayerFragment is StreamPlayerFragment && emoteMenuIsVisible()) {
            toggleBackPressedCallback(false)
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

    override fun onMaximize() {
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        if (this@BasePlayerFragment is StreamPlayerFragment && emoteMenuIsVisible()) {
            toggleBackPressedCallback(true)
        }
        playerView.useController = true
        if (!playerView.controllerHideOnTouch) { //TODO
            playerView.showController()
        }
        if (!isPortrait) {
            hideStatusBar()
        }
    }

    override fun onClose() {}

    override fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean) {
        val context = requireContext()
        if (durationMs > 0L) {
            context.toast(when {
                hours == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.minutes, minutes, minutes))
                minutes == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.hours, hours, hours))
                else -> getString(R.string.playback_will_stop_hours_minutes, resources.getQuantityString(R.plurals.hours, hours, hours), resources.getQuantityString(R.plurals.minutes, minutes, minutes))
            })
        } else if (viewModel.timerTimeLeft > 0L) {
            context.toast(R.string.timer_canceled)
        }
        if (lockScreen != prefs.getBoolean(C.SLEEP_TIMER_LOCK, true)) {
            prefs.edit { putBoolean(C.SLEEP_TIMER_LOCK, lockScreen) }
        }
        viewModel.setTimer(durationMs)
    }

    override fun onChange(requestCode: Int, index: Int, text: CharSequence, tag: Int?) {
        when (requestCode) {
            REQUEST_CODE_QUALITY -> {
                if ((viewModel as? HlsPlayerViewModel)?.usingPlaylist == false && index == 0) {
                    // TODO
                } else {
                    viewModel.changeQuality(index)
                    (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setQuality(viewModel.qualities?.getOrNull(index))
                }
            }
            REQUEST_CODE_SPEED -> {
                SPEEDS.getOrNull(index)?.let {
                    viewModel.player?.setPlaybackSpeed(it)
                    prefs.edit { putFloat(C.PLAYER_SPEED, it) }
                }
                SPEED_LABELS.getOrNull(index)?.let {
                    (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSpeed(requireContext().getString(it))
                }
            }
        }
    }

    override fun changeVolume(volume: Float) {
        viewModel.player?.volume = volume
    }

    //    abstract fun play(obj: Parcelable) //TODO instead maybe add livedata in mainactivity and observe it

    fun isSleepTimerActive(): Boolean {
        return viewModel.timerTimeLeft > 0L
    }

    fun setResizeMode() {
        resizeMode = (resizeMode + 1).let { if (it < 5) it else 0 }
        playerView.resizeMode = resizeMode
        prefs.edit { putInt(C.ASPECT_RATIO_LANDSCAPE, resizeMode) }
    }

    fun showSleepTimerDialog() {
        SleepTimerDialog.show(childFragmentManager, viewModel.timerTimeLeft)
    }

    fun showQualityDialog() {
        viewModel.qualities?.let {
            FragmentUtils.showRadioButtonDialogFragment(childFragmentManager, it, viewModel.qualityIndex, REQUEST_CODE_QUALITY)
        }
    }

    fun showSpeedDialog() {
        viewModel.player?.playbackParameters?.speed?.let {
            FragmentUtils.showRadioButtonDialogFragment(requireContext(), childFragmentManager, SPEED_LABELS, SPEEDS.indexOf(it), REQUEST_CODE_SPEED)
        }
    }

    fun showVolumeDialog() {
        FragmentUtils.showPlayerVolumeDialog(childFragmentManager, viewModel.player?.volume)
    }

    fun minimize() {
        slidingLayout.minimize()
    }

    fun maximize() {
        slidingLayout.maximize()
    }

    fun enterPictureInPicture(): Boolean {
        return shouldEnterPictureInPicture
    }

    private fun initLayout() {
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
            requireView().findViewById<ImageButton>(R.id.playerAspectRatio)?.let {
                if (it.isVisible) {
                    it.gone()
                }
            }
            requireView().findViewById<ImageButton>(R.id.playerChatToggle)?.let {
                if (it.isVisible) {
                    it.gone()
                }
            }
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
            if (this !is OfflinePlayerFragment) {
                chatLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = chatWidthLandscape
                    height = LinearLayout.LayoutParams.MATCH_PARENT
                    weight = 0f
                }
                if (prefs.getBoolean(C.CHAT_DISABLE, false)) {
                    chatLayout.gone()
                    slidingLayout.maximizedSecondViewVisibility = View.GONE
                } else {
                    setPreferredChatVisibility()
                }
                val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
                val btnDown = requireView().findViewById<Button>(R.id.btnDown)
                if (chatLayout.isVisible && btnDown != null && !btnDown.isVisible && recyclerView.adapter?.itemCount != null) {
                    recyclerView.scrollToPosition(recyclerView.adapter?.itemCount!! - 1) // scroll down
                }
            } else {
                chatLayout.gone()
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

    private fun setPreferredChatVisibility() {
        if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) showChat() else hideChat()
    }

    fun toggleChatBar() {
        requireView().findViewById<LinearLayout>(R.id.messageView)?.let {
            if (it.isVisible) {
                chatLayout.hideKeyboard()
                chatLayout.clearFocus()
                if (this@BasePlayerFragment is StreamPlayerFragment && emoteMenuIsVisible()) {
                    toggleEmoteMenu(false)
                }
                it.gone()
                prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, false) }
            } else {
                it.visible()
                prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, true) }
            }
        }
    }

    fun hideChat() {
        if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            requireView().findViewById<ImageButton>(R.id.playerChatToggle)?.apply {
                visible()
                setImageResource(R.drawable.baseline_speaker_notes_black_24)
                setOnClickListener { showChat() }
            }
        }
        chatLayout.gone()
        prefs.edit { putBoolean(C.KEY_CHAT_OPENED, false) }
        slidingLayout.maximizedSecondViewVisibility = View.GONE
    }

    fun showChat() {
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
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
        val btnDown = requireView().findViewById<Button>(R.id.btnDown)
        if (chatLayout.isVisible && btnDown != null && !btnDown.isVisible && recyclerView.adapter?.itemCount != null) {
            recyclerView.scrollToPosition(recyclerView.adapter?.itemCount!! - 1) // scroll down
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
            WindowInsetsControllerCompat(requireActivity().window, requireActivity().window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (isAdded) {
                    requireActivity().window.decorView.systemUiVisibility = 0
                }
            }
        }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
            WindowInsetsControllerCompat(requireActivity().window, requireActivity().window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (isAdded) {
                    requireActivity().window.decorView.systemUiVisibility = systemUiFlags
                }
            }
        }
    }

    private fun lockScreen() {
        if ((requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager).isScreenOn) {
            try {
                (requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
            } catch (e: SecurityException) {}
        }
    }

    fun setSubtitles(available: Boolean? = null, enabled: Boolean? = null) {
        requireView().findViewById<ImageButton>(R.id.playerSubtitleToggle)?.apply {
            if (available ?: (viewModel.subtitlesAvailable.value == true) && prefs.getBoolean(C.PLAYER_SUBTITLES, false)) {
                visible()
                if (enabled ?: viewModel.subtitlesEnabled()) {
                    setImageResource(R.drawable.exo_ic_subtitle_on)
                    setOnClickListener { toggleSubtitles(false) }
                } else {
                    setImageResource(R.drawable.exo_ic_subtitle_off)
                    setOnClickListener { toggleSubtitles(true) }
                }
            } else {
                gone()
            }
        }
        (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSubtitles(
            available = available ?: (viewModel.subtitlesAvailable.value == true),
            enabled = enabled ?: viewModel.subtitlesEnabled()
        )
    }

    fun toggleSubtitles(enabled: Boolean) {
        setSubtitles(enabled = enabled)
        viewModel.toggleSubtitles(enabled)
    }

    override fun onMovedToForeground() {
        viewModel.onResume()
    }

    override fun onMovedToBackground() {
        viewModel.onPause()
    }
}
