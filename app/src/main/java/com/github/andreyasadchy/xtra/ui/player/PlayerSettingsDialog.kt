package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.PlayerSettingsBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerSettingsDialog : BottomSheetDialogFragment() {

    companion object {

        private const val SPEED = "speed"
        private const val VOD_GAMES = "vod_games"

        fun newInstance(speedText: String? = null, vodGames: Boolean? = false): PlayerSettingsDialog {
            return PlayerSettingsDialog().apply {
                arguments = bundleOf(SPEED to speedText, VOD_GAMES to vodGames)
            }
        }
    }

    private var _binding: PlayerSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PlayerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val arguments = requireArguments()
        with(binding) {
            if ((parentFragment as? PlayerFragment)?.stream == null && requireContext().prefs().getBoolean(C.PLAYER_MENU_SPEED, false)) {
                menuSpeed.visible()
                menuSpeed.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showSpeedDialog()
                    dismiss()
                }
                setSpeed(arguments.getString(SPEED))
            }
            if (requireContext().prefs().getBoolean(C.PLAYER_MENU_QUALITY, false)) {
                menuQuality.visible()
                menuQuality.setOnClickListener { dismiss() }
                (parentFragment as? PlayerFragment)?.setQualityText()
            }
            if ((parentFragment as? PlayerFragment)?.stream != null) {
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU_VIEWER_LIST, true)) {
                    menuViewerList.visible()
                    menuViewerList.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.openViewerList()
                        dismiss()
                    }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU_RESTART, false)) {
                    menuRestart.visible()
                    menuRestart.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.restartPlayer()
                        dismiss()
                    }
                }
                if (!requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
                    val isLoggedIn = !requireContext().tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                            (!TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                                    !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank())
                    if (isLoggedIn && requireContext().prefs().getBoolean(C.PLAYER_MENU_CHAT_BAR, true)) {
                        menuChatBar.visible()
                        if (requireContext().prefs().getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)) {
                            menuChatBar.text = requireContext().getString(R.string.hide_chat_bar)
                        } else {
                            menuChatBar.text = requireContext().getString(R.string.show_chat_bar)
                        }
                        menuChatBar.setOnClickListener {
                            (parentFragment as? PlayerFragment)?.toggleChatBar()
                            dismiss()
                        }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_MENU_CHAT_DISCONNECT, true)) {
                        menuChatDisconnect.visible()
                        if ((parentFragment as? PlayerFragment)?.isActive() == false) {
                            menuChatDisconnect.text = requireContext().getString(R.string.connect_chat)
                            menuChatDisconnect.setOnClickListener {
                                (parentFragment as? PlayerFragment)?.reconnect()
                                dismiss()
                            }
                        } else {
                            menuChatDisconnect.text = requireContext().getString(R.string.disconnect_chat)
                            menuChatDisconnect.setOnClickListener {
                                (parentFragment as? PlayerFragment)?.disconnect()
                                dismiss()
                            }
                        }
                    }
                }
                if (requireContext().prefs().getBoolean(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false)) {
                    menuMediaPlaylistTags.visible()
                    menuMediaPlaylistTags.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.showPlaylistTags(true)
                        dismiss()
                    }
                    menuMultivariantPlaylistTags.visible()
                    menuMultivariantPlaylistTags.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.showPlaylistTags(false)
                        dismiss()
                    }
                }
            }
            if ((parentFragment as? PlayerFragment)?.video != null) {
                if (arguments.getBoolean(VOD_GAMES)) {
                    setVodGames()
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU_BOOKMARK, true)) {
                    (parentFragment as? PlayerFragment)?.checkBookmark()
                }
            }
            if ((parentFragment as? PlayerFragment)?.offlineVideo == null && requireContext().prefs().getBoolean(C.PLAYER_MENU_DOWNLOAD, true)) {
                menuDownload.visible()
                menuDownload.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showDownloadDialog()
                    dismiss()
                }
            }
            if ((parentFragment as? PlayerFragment)?.clip == null && requireContext().prefs().getBoolean(C.PLAYER_MENU_SLEEP, true)) {
                menuTimer.visible()
                menuTimer.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showSleepTimerDialog()
                    dismiss()
                }
            }
            if ((parentFragment as? PlayerFragment)?.getIsPortrait() == false) {
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU_ASPECT, false)) {
                    menuRatio.visible()
                    menuRatio.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.setResizeMode()
                        dismiss()
                    }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU_CHAT_TOGGLE, false)) {
                    menuChatToggle.visible()
                    if (requireContext().prefs().getBoolean(C.KEY_CHAT_OPENED, true)) {
                        menuChatToggle.text = requireContext().getString(R.string.hide_chat)
                        menuChatToggle.setOnClickListener {
                            (parentFragment as? PlayerFragment)?.hideChat()
                            dismiss()
                        }
                    } else {
                        menuChatToggle.text = requireContext().getString(R.string.show_chat)
                        menuChatToggle.setOnClickListener {
                            (parentFragment as? PlayerFragment)?.showChat()
                            dismiss()
                        }
                    }
                }
            }
            if (requireContext().prefs().getBoolean(C.PLAYER_MENU_VOLUME, false)) {
                menuVolume.visible()
                menuVolume.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showVolumeDialog()
                    dismiss()
                }
            }
            (parentFragment as? PlayerFragment)?.setSubtitles()
            if (((parentFragment as? PlayerFragment)?.stream != null || (parentFragment as? PlayerFragment)?.video != null) &&
                !requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) &&
                requireContext().prefs().getBoolean(C.PLAYER_MENU_RELOAD_EMOTES, true)
            ) {
                menuReloadEmotes.visible()
                menuReloadEmotes.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.reloadEmotes()
                    dismiss()
                }
            }
        }
    }

    fun setQuality(text: String?) {
        with(binding) {
            if (!text.isNullOrBlank() && menuQuality.isVisible) {
                qualityValue.visible()
                qualityValue.text = text
                menuQuality.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showQualityDialog()
                    dismiss()
                }
            }
        }
    }

    fun setSpeed(text: String?) {
        with(binding) {
            if (!text.isNullOrBlank() && menuSpeed.isVisible) {
                speedValue.visible()
                speedValue.text = text
            }
        }
    }

    fun setVodGames() {
        with(binding) {
            if (requireContext().prefs().getBoolean(C.PLAYER_MENU_GAMES, false)) {
                menuVodGames.visible()
                menuVodGames.setOnClickListener {
                    (parentFragment as? PlayerFragment)?.showVodGames()
                    dismiss()
                }
            }
        }
    }

    fun setBookmarkText(isBookmarked: Boolean) {
        with(binding) {
            menuBookmark.visible()
            menuBookmark.text = requireContext().getString(if (isBookmarked) R.string.remove_bookmark else R.string.add_bookmark)
            menuBookmark.setOnClickListener {
                (parentFragment as? PlayerFragment)?.saveBookmark()
                dismiss()
            }
        }
    }

    fun setSubtitles(available: Boolean, enabled: Boolean) {
        with(binding) {
            if (available && requireContext().prefs().getBoolean(C.PLAYER_MENU_SUBTITLES, false)) {
                menuSubtitles.visible()
                if (enabled) {
                    menuSubtitles.text = requireContext().getString(R.string.hide_subtitles)
                    menuSubtitles.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.toggleSubtitles(false)
                        dismiss()
                    }
                } else {
                    menuSubtitles.text = requireContext().getString(R.string.show_subtitles)
                    menuSubtitles.setOnClickListener {
                        (parentFragment as? PlayerFragment)?.toggleSubtitles(true)
                        dismiss()
                    }
                }
            } else {
                menuSubtitles.gone()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
