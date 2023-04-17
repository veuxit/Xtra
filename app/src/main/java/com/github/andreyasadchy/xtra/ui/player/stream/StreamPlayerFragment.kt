package com.github.andreyasadchy.xtra.ui.player.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerStreamBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlayerMode
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FragmentUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.disable
import com.github.andreyasadchy.xtra.util.enable
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.exoplayer2.source.hls.HlsManifest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StreamPlayerFragment : BasePlayerFragment() {

    private var _binding: FragmentPlayerStreamBinding? = null
    private val binding get() = _binding!!
    override val viewModel: StreamPlayerViewModel by viewModels()
    lateinit var chatFragment: ChatFragment
    private lateinit var stream: Stream

    override val shouldEnterPictureInPicture: Boolean
        get() = viewModel.playerMode.value == PlayerMode.NORMAL

    override val controllerAutoShow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stream = requireArguments().getParcelable(KEY_STREAM)!!
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
        val mode = requireView().findViewById<ImageButton>(R.id.playerMode)
        viewModel.loaded.observe(viewLifecycleOwner) {
            if (it) {
                settings?.enable()
                mode?.enable()
                (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setQuality(viewModel.qualities?.getOrNull(viewModel.qualityIndex))
            } else {
                settings?.disable()
                mode?.disable()
            }
        }
        viewModel.stream.observe(viewLifecycleOwner) {
            chatFragment.updateStreamId(it?.id)
            if (prefs.getBoolean(C.CHAT_DISABLE, false) || !prefs.getBoolean(C.CHAT_PUBSUB_ENABLED, true) || requireView().findViewById<TextView>(R.id.viewers)?.text.isNullOrBlank()) {
                updateViewerCount(it?.viewerCount)
            }
        }
        if (prefs.getBoolean(C.PLAYER_SETTINGS, true)) {
            settings?.apply {
                visible()
                setOnClickListener { showQualityDialog() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_MENU, true)) {
            requireView().findViewById<ImageButton>(R.id.playerMenu)?.apply {
                visible()
                setOnClickListener {
                    FragmentUtils.showPlayerSettingsDialog(
                        fragmentManager = childFragmentManager,
                        quality = if (viewModel.loaded.value == true) viewModel.qualities?.getOrNull(viewModel.qualityIndex) else null
                    )
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
                setOnClickListener { viewModel.player?.seekToDefaultPosition() }
            }
        }
        if (prefs.getBoolean(C.PLAYER_MODE, false)) {
            mode?.apply {
                visible()
                setOnClickListener {
                    if (viewModel.playerMode.value != PlayerMode.AUDIO_ONLY) {
                        viewModel.qualities?.lastIndex?.minus(1)?.let { viewModel.changeQuality(it) }
                    } else {
                        viewModel.changeQuality(viewModel.previousQuality)
                    }
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_VIEWERLIST, false)) {
            requireView().findViewById<LinearLayout>(R.id.viewersLayout)?.apply {
                setOnClickListener { openViewerList() }
            }
        }
        if (!prefs.getBoolean(C.PLAYER_PAUSE, false)) {
            viewModel.showPauseButton.observe(viewLifecycleOwner) {
                binding.playerView.findViewById<ImageButton>(R.id.exo_play_pause)?.apply {
                    if (it) {
                        gone()
                    } else {
                        visible()
                    }
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_CHANNEL, true)) {
            requireView().findViewById<TextView>(R.id.playerChannel)?.apply {
                visible()
                text = stream.channelName
                setOnClickListener {
                    findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelId = stream.channelId,
                        channelLogin = stream.channelLogin,
                        channelName = stream.channelName,
                        channelLogo = stream.channelLogo
                    ))
                    slidingLayout.minimize()
                }
            }
        }
        val activity = requireActivity() as MainActivity
        val account = Account.get(activity)
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
        if (prefs.getBoolean(C.PLAYER_FOLLOW, true) && ((setting == 0 && account.id != stream.channelId || account.login != stream.channelLogin) || setting == 1)) {
            val followButton = requireView().findViewById<ImageButton>(R.id.playerFollow)
            followButton?.visible()
            var initialized = false
            viewModel.follow.observe(viewLifecycleOwner) { pair ->
                val following = pair.first
                val errorMessage = pair.second
                if (initialized) {
                    if (!errorMessage.isNullOrBlank()) {
                        requireContext().shortToast(errorMessage)
                    } else {
                        requireContext().shortToast(requireContext().getString(if (following) R.string.now_following else R.string.unfollowed, stream.channelName))
                    }
                } else {
                    initialized = true
                }
                if (errorMessage.isNullOrBlank()) {
                    followButton?.setOnClickListener {
                        if (!following) {
                            viewModel.saveFollowChannel(requireContext(), stream.channelId, stream.channelLogin, stream.channelName, stream.channelLogo)
                        } else {
                            FragmentUtils.showUnfollowDialog(requireContext(), stream.channelName) {
                                viewModel.deleteFollowChannel(requireContext(), stream.channelId)
                            }
                        }
                    }
                    followButton?.setImageResource(if (following) R.drawable.baseline_favorite_black_24 else R.drawable.baseline_favorite_border_black_24)
                }
            }
        }
        viewModel.initializePlayer()
        chatFragment = childFragmentManager.findFragmentById(R.id.chatFragmentContainer).let {
            if (it != null) {
                it as ChatFragment
            } else {
                val fragment = ChatFragment.newInstance(stream.channelId, stream.channelLogin, stream.channelName, stream.id)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun initialize() {
        viewModel.startStream(stream)
        val activity = requireActivity() as MainActivity
        val account = Account.get(activity)
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
        if (prefs.getBoolean(C.PLAYER_FOLLOW, true) && ((setting == 0 && account.id != stream.channelId || account.login != stream.channelLogin) || setting == 1)) {
            viewModel.isFollowingChannel(requireContext(), stream.channelId, stream.channelLogin)
        }
    }

    fun updateViewerCount(viewerCount: Int?) {
        val viewers = requireView().findViewById<TextView>(R.id.viewers)
        val viewerIcon = requireView().findViewById<ImageView>(R.id.viewerIcon)
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

    fun emoteMenuIsVisible() = chatFragment.emoteMenuIsVisible()

    fun toggleEmoteMenu(enable: Boolean) = chatFragment.toggleEmoteMenu(enable)

    fun toggleBackPressedCallback(enable: Boolean) = chatFragment.toggleBackPressedCallback(enable)

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
