package com.github.andreyasadchy.xtra.ui.download

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
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
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.color.MaterialColors
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
                binding.layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.sharedStorageLayout && v.id != R.id.appStorageLayout }
                init(it)
            } else {
                dismiss()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_VIDEO_INFO, VideoDownloadInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_VIDEO_INFO)
        }.let {
            if (it == null) {
                viewModel.setVideo(
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                    video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requireArguments().getParcelable(KEY_VIDEO, Video::class.java)!!
                    } else {
                        @Suppress("DEPRECATION")
                        requireArguments().getParcelable(KEY_VIDEO)!!
                    },
                    playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                    skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2,
                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                )
            } else {
                viewModel.setVideoInfo(it)
            }
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

    private fun init(videoInfo: VideoDownloadInfo) {
        with(binding) {
            val context = requireContext()
            init(context, storageSelectionContainer, download)
            val previousPath = prefs.getString(C.DOWNLOAD_SHARED_PATH, null)
            if (!previousPath.isNullOrBlank()) {
                sharedPath = previousPath
                storageSelectionContainer.directory.apply {
                    visible()
                    text = Uri.decode(previousPath.substringAfter("/tree/"))
                }
            }
            with(videoInfo) {
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
                            val location = resources.getStringArray(R.array.spinnerStorage).indexOf(storageSelectionContainer.storageSpinner.editText?.text.toString())
                            val path = if (location == 0) sharedPath else downloadPath
                            if (!path.isNullOrBlank()) {
                                viewModel.download(url, path, quality, from, to, requireContext().prefs().getBoolean(C.DOWNLOAD_PLAYLIST_TO_FILE, false), Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || requireContext().prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false))
                                requireContext().prefs().edit {
                                    putInt(C.DOWNLOAD_LOCATION, location)
                                    if (location == 0) {
                                        putString(C.DOWNLOAD_SHARED_PATH, sharedPath)
                                    }
                                }
                                DownloadUtils.requestNotificationPermission(requireActivity())
                            }
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
            val value = text.ifEmpty { default }
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
