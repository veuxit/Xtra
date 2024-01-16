package com.github.andreyasadchy.xtra.ui.download

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogClipDownloadBinding
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint

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
                clip = getParcelable(KEY_CLIP)!!,
                qualities = getSerializable(KEY_QUALITIES) as Map<String, String>?,
                skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
            )
        }
        viewModel.qualities.observe(this) {
            binding.layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.storageSelectionContainer }
            init(it)
        }
        return builder.create()
    }

    private fun init(qualities: Map<String, String>) {
        with(binding) {
            val context = requireContext()
            init(context, binding.storageSelectionContainer, download)
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
                viewModel.download(qualities.getValue(quality), downloadPath, quality, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || requireContext().prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false))
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
