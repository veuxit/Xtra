package com.github.andreyasadchy.xtra.ui.view.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogChatMessageClickBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.loadImage
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageClickedDialog : ExpandingBottomSheetDialogFragment(), IntegrityDialog.CallbackListener {

    interface OnButtonClickListener {
        fun onReplyClicked(userName: String)
        fun onCopyMessageClicked(message: String)
        fun onViewProfileClicked(id: String?, login: String?, name: String?, channelLogo: String?)
    }

    companion object {
        private const val KEY_MESSAGING = "messaging"
        private const val KEY_FORMATTED = "formatted"
        private const val KEY_MESSAGE = "message"
        private const val KEY_USERID = "userId"
        private const val KEY_USERNAME = "userName"
        private const val KEY_CHANNEL_ID = "channelId"
        private const val KEY_FULL_MSG = "full"
        private val savedUsers = mutableListOf<Pair<User, String?>>()

        fun newInstance(messagingEnabled: Boolean, formattedMessage: CharSequence, message: String?, userId: String?, userName: String?, channelId: String?, fullMsg: String?) = MessageClickedDialog().apply {
            arguments = bundleOf(KEY_MESSAGING to messagingEnabled, KEY_FORMATTED to formattedMessage, KEY_MESSAGE to message, KEY_USERID to userId, KEY_USERNAME to userName, KEY_CHANNEL_ID to channelId, KEY_FULL_MSG to fullMsg)
        }
    }

    private var _binding: DialogChatMessageClickBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MessageClickedViewModel by viewModels()

    private lateinit var listener: OnButtonClickListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnButtonClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogChatMessageClickBinding.inflate(inflater, container, false)
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
            message.text = args.getCharSequence(KEY_FORMATTED)
            val msg = args.getString(KEY_MESSAGE)
            val userId = args.getString(KEY_USERID)
            val userName = args.getString(KEY_USERNAME)
            val targetId = args.getString(KEY_CHANNEL_ID)
            val fullMsg = args.getString(KEY_FULL_MSG)
            val clipboard = getSystemService(requireContext(), ClipboardManager::class.java)
            if (userId != null) {
                val item = savedUsers.find { it.first.channelId == userId && it.second == targetId }
                if (item != null) {
                    updateUserLayout(item.first)
                } else {
                    viewModel.loadUser(
                        channelId = userId,
                        targetId = if (userId != targetId) targetId else null,
                        helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                        helixToken = Account.get(requireContext()).helixToken,
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                        checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.user.collectLatest { pair ->
                                if (pair != null) {
                                    val user = pair.first
                                    val error = pair.second
                                    if (user != null) {
                                        savedUsers.add(Pair(user, targetId))
                                        updateUserLayout(user)
                                        viewModel.user.value = Pair(null, false)
                                    } else {
                                        if (error == true) {
                                            viewProfile.visible()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (args.getBoolean(KEY_MESSAGING)) {
                    if (!userName.isNullOrBlank()) {
                        reply.visible()
                        reply.setOnClickListener {
                            listener.onReplyClicked(userName)
                            dismiss()
                        }
                    }
                    if (!msg.isNullOrBlank()) {
                        copyMessage.visible()
                        copyMessage.setOnClickListener {
                            listener.onCopyMessageClicked(msg)
                            dismiss()
                        }
                    }
                }
            }
            copyClip.setOnClickListener {
                clipboard?.setPrimaryClip(ClipData.newPlainText("label", msg))
                dismiss()
            }
            if (requireContext().prefs().getBoolean(C.DEBUG_CHAT_FULLMSG, false) && fullMsg != null) {
                copyFullMsg.visible()
                copyFullMsg.setOnClickListener {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("label", fullMsg))
                    dismiss()
                }
            }
        }
    }

    private fun updateUserLayout(user: User) {
        with(binding) {
            if (user.bannerImageURL != null) {
                userLayout.visible()
                bannerImage.visible()
                bannerImage.loadImage(requireParentFragment(), user.bannerImageURL)
            } else {
                bannerImage.gone()
            }
            if (user.channelLogo != null) {
                userLayout.visible()
                userImage.visible()
                userImage.loadImage(requireParentFragment(), user.channelLogo, circle = true)
                userImage.setOnClickListener {
                    listener.onViewProfileClicked(user.channelId, user.channelLogin, user.channelName, user.channelLogo)
                    dismiss()
                }
            } else {
                userImage.gone()
            }
            if (user.channelName != null) {
                userLayout.visible()
                userName.visible()
                userName.text = user.channelName
                userName.setOnClickListener {
                    listener.onViewProfileClicked(user.channelId, user.channelLogin, user.channelName, user.channelLogo)
                    dismiss()
                }
                if (user.bannerImageURL != null) {
                    userName.setTextColor(Color.WHITE)
                    userName.setShadowLayer(4f, 0f, 0f, Color.BLACK)
                }
            } else {
                userName.gone()
            }
            if (user.createdAt != null) {
                userLayout.visible()
                userCreated.visible()
                userCreated.text = requireContext().getString(R.string.created_at, TwitchApiHelper.formatTimeString(requireContext(), user.createdAt))
                if (user.bannerImageURL != null) {
                    userCreated.setTextColor(Color.LTGRAY)
                    userCreated.setShadowLayer(4f, 0f, 0f, Color.BLACK)
                }
            } else {
                userCreated.gone()
            }
            if (user.followedAt != null) {
                userLayout.visible()
                userFollowed.visible()
                userFollowed.text = requireContext().getString(R.string.followed_at, TwitchApiHelper.formatTimeString(requireContext(), user.followedAt!!))
                if (user.bannerImageURL != null) {
                    userFollowed.setTextColor(Color.LTGRAY)
                    userFollowed.setShadowLayer(4f, 0f, 0f, Color.BLACK)
                }
            } else {
                userFollowed.gone()
            }
            if (!userImage.isVisible && !userName.isVisible) {
                viewProfile.visible()
            }
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback == "refresh") {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val userId = requireArguments().getString(KEY_USERID)
                    if (userId != null) {
                        val targetId = requireArguments().getString(KEY_CHANNEL_ID)
                        viewModel.loadUser(
                            channelId = userId,
                            targetId = if (userId != targetId) targetId else null,
                            helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                            helixToken = Account.get(requireContext()).helixToken,
                            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                            checkIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
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
}