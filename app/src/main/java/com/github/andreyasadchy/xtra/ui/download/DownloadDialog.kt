package com.github.andreyasadchy.xtra.ui.download

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogVideoDownloadBinding
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.max

@AndroidEntryPoint
class DownloadDialog : DialogFragment() {

    companion object {
        private const val KEY_URLS_KEYS = "urls_keys"
        private const val KEY_URLS_VALUES = "urls_values"
        private const val KEY_VIDEO = "video"
        private const val KEY_VIDEO_INFO = "videoInfo"
        private const val KEY_CLIP = "clip"

        fun newInstance(video: Video, videoInfo: VideoDownloadInfo? = null): DownloadDialog {
            return DownloadDialog().apply { arguments = bundleOf(KEY_VIDEO to video, KEY_VIDEO_INFO to videoInfo) }
        }

        fun newInstance(clip: Clip, keys: Array<String>? = null, values: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply { arguments = bundleOf(KEY_CLIP to clip, KEY_URLS_KEYS to keys, KEY_URLS_VALUES to values) }
        }
    }

    private var _binding: DialogVideoDownloadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadViewModel by viewModels()
    private lateinit var storage: List<DownloadUtils.Storage>
    private val downloadPath: String
        get() {
            val index = if (storage.size == 1) {
                0
            } else {
                val checked = max(binding.storageSelectionContainer.radioGroup.checkedRadioButtonId, 0)
                requireContext().prefs().edit { putInt(C.DOWNLOAD_STORAGE, checked) }
                checked
            }
            return storage[index].path
        }
    private var sharedPath: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogVideoDownloadBinding.inflate(layoutInflater)
        val builder = requireContext().getAlertDialogBuilder()
            .setView(binding.root)
        viewModel.integrity.observe(this) {
            if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                IntegrityDialog.show(childFragmentManager)
            }
        }
        val args = requireArguments()
        val video = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_VIDEO, Video::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable(KEY_VIDEO)
        }
        if (video != null) {
            viewModel.videoInfo.observe(this) {
                if (it != null) {
                    val qualities = viewModel.qualities.value
                    if (!qualities.isNullOrEmpty()) {
                        initVideo(video, qualities, it)
                    }
                } else {
                    dismiss()
                }
            }
            viewModel.setVideo(
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                video = video,
                videoInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requireArguments().getParcelable(KEY_VIDEO_INFO, VideoDownloadInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    requireArguments().getParcelable(KEY_VIDEO_INFO)
                },
                playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2,
                enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false)
            )
        } else {
            val clip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                args.getParcelable(KEY_CLIP, Clip::class.java)
            } else {
                @Suppress("DEPRECATION")
                args.getParcelable(KEY_CLIP)
            }
            if (clip != null) {
                viewModel.qualities.observe(this) {
                    if (!it.isNullOrEmpty()) {
                        initClip(clip, it)
                    }
                }
                viewModel.setClip(
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                    clip = clip,
                    qualities = args.getStringArray(KEY_URLS_KEYS)?.let { keys ->
                        args.getStringArray(KEY_URLS_VALUES)?.let { values ->
                            keys.zip(values).toMap(mutableMapOf())
                        }
                    },
                    skipAccessToken = requireContext().prefs().getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                )
            }
        }
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
        return builder.create()
    }

    private fun init(qualities: Map<String, Pair<String, String>>) {
        with(binding.storageSelectionContainer) {
            storage = DownloadUtils.getAvailableStorage(requireContext())
            if (DownloadUtils.isExternalStorageAvailable) {
                val location = requireContext().prefs().getInt(C.DOWNLOAD_LOCATION, 0)
                when (location) {
                    0 -> sharedStorageLayout.visible()
                    1 -> appStorageLayout.visible()
                }
                (storageSpinner.editText as? MaterialAutoCompleteTextView)?.apply {
                    setSimpleItems(resources.getStringArray(R.array.spinnerStorage))
                    setOnItemClickListener { _, _, position, _ ->
                        when (position) {
                            0 -> {
                                sharedStorageLayout.visible()
                                appStorageLayout.gone()
                            }
                            1 -> {
                                appStorageLayout.visible()
                                sharedStorageLayout.gone()
                            }
                        }
                    }
                    setText(adapter.getItem(location).toString(), false)
                }
                if (storage.size > 1) {
                    for (s in storage) {
                        radioGroup.addView(RadioButton(requireContext()).apply {
                            id = s.id
                            text = s.name
                        })
                    }
                    radioGroup.check(requireContext().prefs().getInt(C.DOWNLOAD_STORAGE, 0))
                }
            } else {
                noStorageDetected.visible()
                storageSpinner.gone()
                binding.download.gone()
            }
        }
        with(binding) {
            val previousPath = requireContext().prefs().getString(C.DOWNLOAD_SHARED_PATH, null)
            if (!previousPath.isNullOrBlank()) {
                sharedPath = previousPath
                storageSelectionContainer.directory.apply {
                    visible()
                    text = Uri.decode(previousPath.substringAfter("/tree/"))
                }
            }
            downloadChat.apply {
                isChecked = requireContext().prefs().getBoolean(C.DOWNLOAD_CHAT, false)
                setOnCheckedChangeListener { _, isChecked ->
                    downloadChatEmotes.isEnabled = isChecked
                }
            }
            downloadChatEmotes.apply {
                isChecked = requireContext().prefs().getBoolean(C.DOWNLOAD_CHAT_EMOTES, false)
                isEnabled = downloadChat.isChecked
            }
            (spinner.editText as? MaterialAutoCompleteTextView)?.apply {
                setSimpleItems(qualities.keys.toTypedArray())
                setText(adapter.getItem(0).toString(), false)
            }
            cancel.setOnClickListener { dismiss() }
        }
    }

    private fun initVideo(video: Video, qualities: Map<String, Pair<String, String>>, videoInfo: VideoDownloadInfo) {
        with(binding) {
            with(videoInfo) {
                layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.sharedStorageLayout && v.id != R.id.appStorageLayout }
                init(qualities)
                val defaultFrom = DateUtils.formatElapsedTime(currentPosition / 1000L).let { if (it.length == 5) "00:$it" else it }
                val totalTime = DateUtils.formatElapsedTime(totalDuration / 1000L)
                val defaultTo = totalTime.let { if (it.length != 5) it else "00:$it" }
                duration.text = requireContext().getString(R.string.duration, totalTime)
                timeTo.editText?.hint = defaultTo
                timeFrom.editText?.hint = defaultFrom
                timeFrom.editText?.doOnTextChanged { text, _, _, _ -> if (text?.length == 8) timeTo.requestFocus() }
                addTextChangeListener(timeFrom.editText!!)
                addTextChangeListener(timeTo.editText!!)

                fun download() {
                    val from = parseTime(timeFrom.editText!!, defaultFrom) ?: return
                    val to = parseTime(timeTo.editText!!, defaultTo) ?: return
                    when {
                        to > totalDuration -> {
                            timeTo.requestFocus()
                            timeTo.editText?.error = getString(R.string.to_is_longer)
                        }
                        from < to -> {
                            val quality = qualities.getValue(spinner.editText?.text.toString())
                            val location = resources.getStringArray(R.array.spinnerStorage).indexOf(storageSelectionContainer.storageSpinner.editText?.text.toString())
                            val path = if (location == 0) sharedPath else downloadPath
                            val downloadChat = downloadChat.isChecked
                            val downloadChatEmotes = downloadChatEmotes.isChecked
                            if (!path.isNullOrBlank()) {
                                viewModel.downloadVideo(video, quality.second, path, quality.first, from, to, downloadChat, downloadChatEmotes, requireContext().prefs().getBoolean(C.DOWNLOAD_PLAYLIST_TO_FILE, false), requireContext().prefs().getBoolean(C.DOWNLOAD_WIFI_ONLY, false))
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

    private fun initClip(clip: Clip, qualities: Map<String, Pair<String, String>>) {
        with(binding) {
            layout.children.forEach { v -> v.isVisible = v.id != R.id.progressBar && v.id != R.id.timeLayout && v.id != R.id.sharedStorageLayout && v.id != R.id.appStorageLayout }
            init(qualities)
            download.setOnClickListener {
                val quality = qualities.getValue(spinner.editText?.text.toString())
                val location = resources.getStringArray(R.array.spinnerStorage).indexOf(storageSelectionContainer.storageSpinner.editText?.text.toString())
                val path = if (location == 0) sharedPath else downloadPath
                val downloadChat = downloadChat.isChecked
                val downloadChatEmotes = downloadChatEmotes.isChecked
                if (!path.isNullOrBlank()) {
                    viewModel.downloadClip(clip, quality.second, path, quality.first, downloadChat, downloadChatEmotes, requireContext().prefs().getBoolean(C.DOWNLOAD_WIFI_ONLY, false))
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
