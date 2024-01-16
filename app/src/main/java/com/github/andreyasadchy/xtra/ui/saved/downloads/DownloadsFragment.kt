package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.download.DownloadWorker
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadsFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadsViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<OfflineVideo, out RecyclerView.ViewHolder>
    override var enableNetworkCheck = false

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
            val delete = getString(R.string.delete)
            requireActivity().getAlertDialogBuilder()
                .setTitle(delete)
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(delete) { _, _ -> viewModel.delete(requireContext(), it) }
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
