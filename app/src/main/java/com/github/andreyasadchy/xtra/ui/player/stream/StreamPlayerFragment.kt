package com.github.andreyasadchy.xtra.ui.player.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlayerMode
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.util.*
import com.google.android.exoplayer2.source.hls.HlsManifest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_player_stream.*
import kotlinx.android.synthetic.main.player_stream.*

@AndroidEntryPoint
class StreamPlayerFragment : BasePlayerFragment() {

    override val viewModel: StreamPlayerViewModel by viewModels()
    lateinit var chatFragment: ChatFragment
    private lateinit var stream: Stream
    override val channelId: String?
        get() = stream.channelId
    override val channelLogin: String?
        get() = stream.channelLogin
    override val channelName: String?
        get() = stream.channelName
    override val channelImage: String?
        get() = stream.channelLogo

    override val layoutId: Int
        get() = R.layout.fragment_player_stream
    override val chatContainerId: Int
        get() = R.id.chatFragmentContainer

    override val shouldEnterPictureInPicture: Boolean
        get() = viewModel.playerMode.value == PlayerMode.NORMAL

    override val controllerAutoShow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stream = requireArguments().getParcelable(KEY_STREAM)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatFragment = childFragmentManager.findFragmentById(R.id.chatFragmentContainer).let {
            if (it != null) {
                it as ChatFragment
            } else {
                val fragment = ChatFragment.newInstance(channelId, channelLogin, channelName, stream.id)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun initialize() {
        viewModel.startStream(stream)
        super.initialize()
        val settings = requireView().findViewById<ImageButton>(R.id.playerSettings)
        val playerMenu = requireView().findViewById<ImageButton>(R.id.playerMenu)
        val restart = requireView().findViewById<ImageButton>(R.id.playerRestart)
        val mode = requireView().findViewById<ImageButton>(R.id.playerMode)
        val viewersLayout = requireView().findViewById<LinearLayout>(R.id.viewersLayout)
        viewModel.loaded.observe(viewLifecycleOwner) {
            if (it) {
                settings.enable()
                mode.enable()
                (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setQuality(viewModel.qualities?.getOrNull(viewModel.qualityIndex))
            } else {
                settings.disable()
                mode.disable()
            }
        }
        viewModel.stream.observe(viewLifecycleOwner) {
            chatFragment.updateStreamId(it?.id)
            if (prefs.getBoolean(C.CHAT_DISABLE, false) || !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) || viewers.text.isNullOrBlank()) {
                updateViewerCount(it?.viewerCount)
            }
        }
        if (prefs.getBoolean(C.PLAYER_SETTINGS, true)) {
            settings.visible()
            settings.setOnClickListener { showQualityDialog() }
        }
        if (prefs.getBoolean(C.PLAYER_MENU, true)) {
            playerMenu.visible()
            playerMenu.setOnClickListener {
                FragmentUtils.showPlayerSettingsDialog(
                    fragmentManager = childFragmentManager,
                    quality = if (viewModel.loaded.value == true) viewModel.qualities?.getOrNull(viewModel.qualityIndex) else null
                )
            }
        }
        if (prefs.getBoolean(C.PLAYER_RESTART, true)) {
            restart.visible()
            restart.setOnClickListener { restartPlayer() }
        }
        if (prefs.getBoolean(C.PLAYER_SEEKLIVE, false)) {
            requireView().findViewById<ImageButton>(R.id.playerSeekLive).apply {
                visible()
                setOnClickListener { viewModel.player?.seekToDefaultPosition() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_MODE, false)) {
            mode.visible()
            mode.setOnClickListener {
                if (viewModel.playerMode.value != PlayerMode.AUDIO_ONLY) {
                    viewModel.qualities?.lastIndex?.minus(1)?.let { viewModel.changeQuality(it) }
                } else {
                    viewModel.changeQuality(viewModel.previousQuality)
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_VIEWERLIST, false)) {
            viewersLayout.setOnClickListener { openViewerList() }
        }
        if (!prefs.getBoolean(C.PLAYER_PAUSE, false)) {
            viewModel.showPauseButton.observe(viewLifecycleOwner) {
                playerView.findViewById<ImageButton>(R.id.exo_play_pause)?.apply {
                    if (it) {
                        gone()
                    } else {
                        visible()
                    }
                }
            }
        }
    }

    fun updateViewerCount(viewerCount: Int?) {
        if (viewerCount != null) {
            viewers.text = TwitchApiHelper.formatCount(requireContext(), viewerCount)
            if (prefs.getBoolean(C.PLAYER_VIEWERICON, true)) {
                viewerIcon.visible()
            }
        } else {
            viewers.text = null
            viewerIcon.gone()
        }
    }

    fun restartPlayer() {
        viewModel.restartPlayer()
    }

    fun openViewerList() {
        stream.channelLogin?.let { login -> FragmentUtils.showPlayerViewerListDialog(childFragmentManager, login, viewModel.repository) }
    }

    fun showPlaylistTags(mediaPlaylist: Boolean) {
        val tags = if (mediaPlaylist) {
            (viewModel.player?.currentManifest as? HlsManifest)?.mediaPlaylist?.tags?.dropLastWhile { it == "ads=true" }?.joinToString("\n")
        } else {
            (viewModel.player?.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.joinToString("\n")
        }
        if (!tags.isNullOrBlank()) {
            AlertDialog.Builder(requireContext()).apply {
                setView(NestedScrollView(context).apply {
                    addView(HorizontalScrollView(context).apply {
                        addView(TextView(context).apply {
                            text = tags
                            textSize = 12F
                            setTextIsSelectable(true)
                        })
                    })
                })
                setNegativeButton(R.string.copy_clip) { dialog, _ ->
                    val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("label", tags))
                    dialog.dismiss()
                }
                setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            }.show()
        }
    }

    fun hideEmotesMenu() = chatFragment.hideEmotesMenu()

    override fun onMinimize() {
        super.onMinimize()
        chatFragment.hideKeyboard()
    }

//    override fun play(obj: Parcelable) {
//        val stream = obj as Stream
//        if (viewModel.stream != stream) {
//            viewModel.player.playWhenReady = false
//            chatView.adapter.submitList(null)
//        }
//        viewModel.stream = stream
//        draggableView?.maximize()
//    }

    override fun onNetworkRestored() {
        if (isResumed) {
            viewModel.resumePlayer()
        }
    }

    fun startAudioOnly() {
        viewModel.startAudioOnly()
    }

    companion object {
        private const val KEY_STREAM = "stream"

        fun newInstance(stream: Stream): StreamPlayerFragment {
            return StreamPlayerFragment().apply { arguments = bundleOf(KEY_STREAM to stream) }
        }
    }
}
