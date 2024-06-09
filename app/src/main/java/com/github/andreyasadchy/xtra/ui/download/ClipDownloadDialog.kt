package com.github.andreyasadchy.xtra.ui.download

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogClipDownloadBinding
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint
import java.io.Serializable

@AndroidEntryPoint
class ClipDownloadDialog : BaseDownloadDialog() {

    companion object {
        private const val KEY_QUALITIES = "urls"
        private const val KEY_CLIP = "clip"

        fun newInstance(clip: Clip, qualities: Map<String, String>? = null): ClipDownloadDialog {
            return ClipDownloadDialog().apply {
                arguments = bundleOf(KEY_CLIP to clip, KEY_QUALITIES to qualities)
            }
        }
    }

    private var _binding: DialogClipDownloadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClipDownloadViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogClipDownloadBinding.inflate(layoutInflater)
        val builder = requireContext().getAlertDialogBuilder()
            .setView(binding.root)
        viewModel.integrity.observe(this) {
            if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                IntegrityDialog.show(childFragmentManager)
            }
        }
        with(requireArguments()) {
            viewModel.init(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                clip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getParcelable(KEY_CLIP, Clip::class.java)!!
                } else {
                    @Suppress("DEPRECATION")
                    getParcelable(KEY_CLIP)!!
                },
                qualities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getSerializable(KEY_QUALITIES, Serializable::class.java) as? Map<String, String>
                } else {
                    @Suppress("DEPRECATION")
                    getSerializable(KEY_QUALITIES) as? Map<String, String>
                },
                skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
            )
        }
        viewModel.qualities.observe(this) {
            binding.layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.sharedStorageLayout && v.id != R.id.appStorageLayout }
            init(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            with(binding.storageSelectionContainer) {
                val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.data?.let {
                            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            sharedPath = it.toString()
                            directory.visible()
                            directory.text = it.path?.substringAfter("/tree/")?.removeSuffix(":")
                        }
                    }
                }
                selectDirectory.setOnClickListener {
                    resultLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, sharedPath)
                        }
                    })
                }
            }
        } else {
            with(binding.storageSelectionContainer) {
                val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.data?.let {
                            sharedPath = it.path?.substringBeforeLast("/")
                            directory.visible()
                            directory.text = it.path?.substringBeforeLast("/")
                        }
                    }
                }
                selectDirectory.setOnClickListener {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    if (intent.resolveActivity(requireActivity().packageManager) != null) {
                        resultLauncher.launch(intent)
                    } else {
                        requireContext().toast(R.string.no_file_manager_found)
                    }
                }
            }
        }
        return builder.create()
    }

    private fun init(qualities: Map<String, String>) {
        with(binding) {
            val context = requireContext()
            init(context, binding.storageSelectionContainer, download)
            val previousPath = prefs.getString(C.DOWNLOAD_SHARED_PATH, null)
            if (!previousPath.isNullOrBlank()) {
                sharedPath = previousPath
                storageSelectionContainer.directory.apply {
                    visible()
                    text = Uri.decode(previousPath.substringAfter("/tree/"))
                }
            }
            downloadChat.apply {
                isChecked = prefs.getBoolean(C.DOWNLOAD_CHAT, false)
                setOnCheckedChangeListener { _, isChecked ->
                    downloadChatEmotes.isEnabled = isChecked
                }
            }
            downloadChatEmotes.apply {
                isChecked = prefs.getBoolean(C.DOWNLOAD_CHAT_EMOTES, false)
                isEnabled = downloadChat.isChecked
            }
            (spinner.editText as? MaterialAutoCompleteTextView)?.apply {
                setSimpleItems(qualities.keys.toTypedArray())
                setText(adapter.getItem(0).toString(), false)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    isFocusable = true
                    isEnabled = false
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                } else {
                    setRawInputType(InputType.TYPE_NULL)
                }
            }
            cancel.setOnClickListener { dismiss() }
            download.setOnClickListener {
                val quality = spinner.editText?.text.toString()
                val location = resources.getStringArray(R.array.spinnerStorage).indexOf(storageSelectionContainer.storageSpinner.editText?.text.toString())
                val path = if (location == 0) sharedPath else downloadPath
                val downloadChat = downloadChat.isChecked
                val downloadChatEmotes = downloadChatEmotes.isChecked
                if (!path.isNullOrBlank()) {
                    viewModel.download(qualities.getValue(quality), path, quality, downloadChat, downloadChatEmotes, requireContext().prefs().getBoolean(C.DOWNLOAD_WIFI_ONLY, false))
                    requireContext().prefs().edit {
                        putInt(C.DOWNLOAD_LOCATION, location)
                        if (location == 0) {
                            putString(C.DOWNLOAD_SHARED_PATH, sharedPath)
                        }
                        putBoolean(C.DOWNLOAD_CHAT, downloadChat)
                        putBoolean(C.DOWNLOAD_CHAT_EMOTES, downloadChatEmotes)
                    }
                    DownloadUtils.requestNotificationPermission(requireActivity())
                }
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
