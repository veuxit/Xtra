package com.github.andreyasadchy.xtra.ui.download

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogVideoDownloadBinding
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoDownloadDialog : BaseDownloadDialog() {

    companion object {
        private const val KEY_VIDEO_INFO = "videoInfo"
        private const val KEY_VIDEO = "video"

        fun newInstance(videoInfo: VideoDownloadInfo): VideoDownloadDialog {
            return VideoDownloadDialog().apply { arguments = bundleOf(KEY_VIDEO_INFO to videoInfo) }
        }

        fun newInstance(video: Video): VideoDownloadDialog {
            return VideoDownloadDialog().apply { arguments = bundleOf(KEY_VIDEO to video) }
        }
    }

    private var _binding: DialogVideoDownloadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VideoDownloadViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogVideoDownloadBinding.inflate(layoutInflater)
        val builder = requireContext().getAlertDialogBuilder()
            .setView(binding.root)
        viewModel.integrity.observe(this) {
            if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                IntegrityDialog.show(childFragmentManager)
            }
        }
        viewModel.videoInfo.observe(this) {
            if (it != null) {
                binding.layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.storageSelectionContainer }
                init(it)
            } else {
                dismiss()
            }
        }
        requireArguments().getParcelable<VideoDownloadInfo?>(KEY_VIDEO_INFO).let {
            if (it == null) {
                viewModel.setVideo(
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                    video = requireArguments().getParcelable(KEY_VIDEO)!!,
                    playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                    skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                )
            } else {
                viewModel.setVideoInfo(it)
            }
        }
        return builder.create()
    }

    private fun init(videoInfo: VideoDownloadInfo) {
        with(binding) {
            val context = requireContext()
            init(context, storageSelectionContainer, download)
            with(videoInfo) {
                (spinner.editText as? MaterialAutoCompleteTextView)?.apply {
                    setSimpleItems(qualities.keys.toTypedArray())
                    setText(adapter.getItem(0).toString(), false)
                }
                val defaultFrom = DateUtils.formatElapsedTime(currentPosition / 1000L).let { if (it.length == 5) "00:$it" else it }
                val totalTime = DateUtils.formatElapsedTime(totalDuration / 1000L)
                val defaultTo = totalTime.let { if (it.length != 5) it else "00:$it" }
                duration.text = context.getString(R.string.duration, totalTime)
                timeTo.editText?.hint = defaultTo
                timeFrom.editText?.hint = defaultFrom
                timeFrom.editText?.doOnTextChanged { text, _, _, _ -> if (text?.length == 8) timeTo.requestFocus() }
                addTextChangeListener(timeFrom.editText!!)
                addTextChangeListener(timeTo.editText!!)
                cancel.setOnClickListener { dismiss() }

                fun download() {
                    val from = parseTime(timeFrom.editText!!, defaultFrom) ?: return
                    val to = parseTime(timeTo.editText!!, defaultTo) ?: return
                    when {
                        to > totalDuration -> {
                            timeTo.requestFocus()
                            timeTo.editText?.error = getString(R.string.to_is_longer)
                        }
                        from < to -> {
                            val quality = spinner.editText?.text.toString()
                            val url = videoInfo.qualities.getValue(quality)
                            viewModel.download(url, downloadPath, quality, from, to, Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || requireContext().prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false))
                            DownloadUtils.requestNotificationPermission(requireActivity())
                            dismiss()
                        }
                        from >= to -> {
                            timeFrom.requestFocus()
                            timeFrom.editText?.error = getString(R.string.from_is_greater)
                        }
                        else -> {
                            timeTo.requestFocus()
                            timeTo.editText?.error = getString(R.string.to_is_lesser)
                        }
                    }
                }
                timeTo.editText?.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        download()
                        true
                    } else {
                        false
                    }
                }
                download.setOnClickListener { download() }
            }
        }
    }

    private fun parseTime(textView: TextView, default: String): Long? {
        with(textView) {
            val value = if (text.isEmpty()) default else text
            val time = value.split(':')
            try {
                if (time.size != 3) throw IllegalArgumentException()
                val hours = time[0].toLong()
                val minutes = time[1].toLong().also { if (it > 59) throw IllegalArgumentException()}
                val seconds = time[2].toLong().also { if (it > 59) throw IllegalArgumentException()}
                return ((hours * 3600) + (minutes * 60) + seconds) * 1000
            } catch (ex: Exception) {
                requestFocus()
                error = getString(R.string.invalid_time)
            }
        }
        return null
    }

    private fun addTextChangeListener(textView: TextView) {
        textView.addTextChangedListener(object : TextWatcher {
            private var lengthBeforeEdit = 0

            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                textView.error = null
                val length = s.length
                if (length == 2 || length == 5) {
                    if (lengthBeforeEdit < length) {
                        textView.append(":")
                    } else {
                        textView.editableText.delete(length - 1, length)
                    }
                }
                lengthBeforeEdit = length
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
