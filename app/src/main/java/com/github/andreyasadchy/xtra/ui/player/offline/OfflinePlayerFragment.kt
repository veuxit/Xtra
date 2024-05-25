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
import androidx.media3.session.SessionCommand
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerOfflineBinding
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.player.BasePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.PlaybackService
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FragmentUtils
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OfflinePlayerFragment : BasePlayerFragment() {
//    override fun play(obj: Parcelable) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }

    private var _binding: FragmentPlayerOfflineBinding? = null
    private val binding get() = _binding!!
    override val viewModel: OfflinePlayerViewModel by viewModels()
    private lateinit var video: OfflineVideo

    override val controllerShowTimeoutMs: Int = 5000

    override fun onCreate(savedInstanceState: Bundle?) {
        enableNetworkCheck = false
        super.onCreate(savedInstanceState)
        video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                    FragmentUtils.showPlayerSettingsDialog(
                        fragmentManager = childFragmentManager,
                        speedText = prefs.getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.find { it == player?.playbackParameters?.speed.toString() },
                    )
                }
            }
        }
        if (prefs.getBoolean(C.PLAYER_CHANNEL, true)) {
            requireView().findViewById<TextView>(R.id.playerChannel)?.apply {
                visible()
                text = video.channelName
                setOnClickListener {
                    findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelId = video.channelId,
                        channelLogin = video.channelLogin,
                        channelName = video.channelName,
                        channelLogo = video.channelLogo
                    ))
                    slidingLayout.minimize()
                }
            }
        }
        if (!video.name.isNullOrBlank() && prefs.getBoolean(C.PLAYER_TITLE, true)) {
            requireView().findViewById<TextView>(R.id.playerTitle)?.apply {
                visible()
                text = video.name
            }
        }
        if (!video.gameName.isNullOrBlank() && prefs.getBoolean(C.PLAYER_CATEGORY, true)) {
            requireView().findViewById<TextView>(R.id.playerCategory)?.apply {
                visible()
                text = video.gameName
                setOnClickListener {
                    findNavController().navigate(
                        if (prefs.getBoolean(C.UI_GAMEPAGER, true)) {
                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                gameId = video.gameId,
                                gameSlug = video.gameSlug,
                                gameName = video.gameName
                            )
                        } else {
                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                gameId = video.gameId,
                                gameSlug = video.gameSlug,
                                gameName = video.gameName
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
                val fragment = ChatFragment.newLocalInstance(video.channelId, video.channelLogin, video.chatUrl)
                childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                fragment
            }
        }
    }

    override fun startPlayer() {
        super.startPlayer()
        player?.sendCustomCommand(SessionCommand(PlaybackService.START_OFFLINE_VIDEO, bundleOf(PlaybackService.ITEM to video)), Bundle.EMPTY)
    }

    override fun onNetworkRestored() {
        //do nothing
    }

    override fun onClose() {
        if (prefs.getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            player?.currentPosition?.let { position ->
                viewModel.savePosition(video.id, position)
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
            return OfflinePlayerFragment().apply { arguments = bundleOf(KEY_VIDEO to video) }
        }
    }
}