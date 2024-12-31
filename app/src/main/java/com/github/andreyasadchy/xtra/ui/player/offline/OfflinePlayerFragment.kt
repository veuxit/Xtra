package com.github.andreyasadchy.xtra.ui.player.offline

import android.os.Build
import android.os.Bundle
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
import androidx.media3.session.SessionCommand
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerOfflineBinding
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlaybackService
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OfflinePlayerFragment : BasePlayerFragment() {

    private var _binding: FragmentPlayerOfflineBinding? = null
    private val binding get() = _binding!!
    override val viewModel: OfflinePlayerViewModel by viewModels()
    private lateinit var item: OfflineVideo

    override val controllerShowTimeoutMs: Int = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        enableNetworkCheck = false
        super.onCreate(savedInstanceState)
        item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_VIDEO, OfflineVideo::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_VIDEO)!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerOfflineBinding.inflate(inflater, container, false).also {
            (it.slidingLayout as LinearLayout).orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (prefs.getBoolean(C.PLAYER_MENU, true)) {
            requireView().findViewById<ImageButton>(R.id.playerMenu)?.apply {
                visible()
                setOnClickListener {
                    PlayerSettingsDialog.newInstance(
                        speedText = prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.find { it == player?.playbackParameters?.speed.toString() },
                    ).show(childFragmentManager, "closeOnPip")
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
                    findNavController().navigate(
                        ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                            channelId = item.channelId,
                            channelLogin = item.channelLogin,
                            channelName = item.channelName,
                            channelLogo = item.channelLogo
                        )
                    )
                    slidingLayout.minimize()
                }
            }
        }
        if (!item.name.isNullOrBlank() && prefs.getBoolean(C.PLAYER_TITLE, true)) {
            requireView().findViewById<TextView>(R.id.playerTitle)?.apply {
                visible()
                text = item.name
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
        chatFragment = childFragmentManager.findFragmentById(R.id.chatFragmentContainer).let {
            if (it != null) {
                it as ChatFragment
            } else {
                val fragment = ChatFragment.newLocalInstance(item.channelId, item.channelLogin, item.chatUrl)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun startPlayer() {
        super.startPlayer()
        if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.video.collectLatest {
                        player?.sendCustomCommand(
                            SessionCommand(
                                PlaybackService.START_OFFLINE_VIDEO, bundleOf(
                                    PlaybackService.ITEM to (it ?: item),
                                    PlaybackService.PLAYBACK_POSITION to (it?.lastWatchPosition ?: 0L),
                                )
                            ), Bundle.EMPTY
                        )
                    }
                }
            }
            viewModel.getVideo(item.id)
        } else {
            player?.sendCustomCommand(
                SessionCommand(
                    PlaybackService.START_OFFLINE_VIDEO, bundleOf(
                        PlaybackService.ITEM to item,
                        PlaybackService.PLAYBACK_POSITION to 0L,
                    )
                ), Bundle.EMPTY
            )
        }
    }

    override fun onNetworkRestored() {
        //do nothing
    }

    override fun onIntegrityDialogCallback(callback: String?) {
    }

    override fun onClose() {
        if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            player?.currentPosition?.let { position ->
                viewModel.savePosition(item.id, position)
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

        fun newInstance(video: OfflineVideo): OfflinePlayerFragment {
            return OfflinePlayerFragment().apply {
                arguments = bundleOf(
                    KEY_VIDEO to video
                )
            }
        }
    }
}