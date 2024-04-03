package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.databinding.StorageSelectionBinding
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.download.DownloadWorker
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadsFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadsViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<OfflineVideo, out RecyclerView.ViewHolder>
    override var enableNetworkCheck = false
    private var fileResultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            fileResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let {
                        viewModel.selectedVideo?.let { video ->
                            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            viewModel.moveToSharedStorage(it, video)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = DownloadsAdapter(this, {
            WorkManager.getInstance(requireContext()).cancelUniqueWork(it.toString())
        }, {
            WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                it.toString(),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to it))
                    .build()
            )
        }, {
            if (it.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                val storage = DownloadUtils.getAvailableStorage(requireContext())
                val binding = StorageSelectionBinding.inflate(layoutInflater).apply {
                    storageSpinner.gone()
                    if (DownloadUtils.isExternalStorageAvailable) {
                        appStorageLayout.visible()
                        for (s in storage) {
                            radioGroup.addView(RadioButton(context).apply {
                                id = s.id
                                text = s.name
                            })
                        }
                        radioGroup.check(if (storage.size == 1) 0 else requireContext().prefs().getInt(C.DOWNLOAD_STORAGE, 0))
                    } else {
                        noStorageDetected.apply {
                            visible()
                            layoutParams = layoutParams.apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                            }
                        }
                    }
                }
                requireActivity().getAlertDialogBuilder()
                    .setView(binding.root)
                    .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                        val checked = binding.radioGroup.checkedRadioButtonId
                        storage.getOrNull(checked)?.let { storage ->
                            requireContext().prefs().edit { putInt(C.DOWNLOAD_STORAGE, checked) }
                            viewModel.moveToAppStorage(storage.path, it)
                        }
                    }
                    .setNegativeButton(getString(android.R.string.cancel), null)
                    .show()
            } else {
                viewModel.selectedVideo = it
                if (it.url.endsWith(".m3u8")) {
                    fileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                } else {
                    fileResultLauncher?.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_TITLE, it.url.substringAfterLast("/", ""))
                    })
                }
            }
        }, {
            val delete = getString(R.string.delete)
            val checkBox = CheckBox(requireContext()).apply {
                text = getString(R.string.keep_files)
                isChecked = true
            }
            val checkBoxView = LinearLayout(requireContext()).apply {
                addView(checkBox)
                val padding = requireContext().convertDpToPixels(20f)
                setPadding(padding, 0, padding, 0)
            }
            requireActivity().getAlertDialogBuilder()
                .setTitle(delete)
                .setMessage(getString(R.string.are_you_sure))
                .setView(checkBoxView)
                .setPositiveButton(delete) { _, _ -> viewModel.delete(it, checkBox.isChecked) }
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show()
        })
        with(binding) {
            pagingAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    pagingAdapter.unregisterAdapterDataObserver(this)
                    pagingAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                            if (positionStart == 0) {
                                recyclerView.smoothScrollToPosition(0)
                            }
                        }
                    })
                }
            })
            recyclerView.adapter = pagingAdapter
            (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    override fun initialize() {
        initializeAdapter(binding, pagingAdapter, viewModel.flow, enableSwipeRefresh = false, enableScrollTopButton = false)
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
