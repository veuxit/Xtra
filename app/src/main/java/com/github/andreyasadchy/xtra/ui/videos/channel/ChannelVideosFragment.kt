package com.github.andreyasadchy.xtra.ui.videos.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.databinding.SortBarBinding
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosAdapter
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosFragment
import com.github.andreyasadchy.xtra.ui.videos.VideosSortDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChannelVideosFragment : BaseVideosFragment(), Scrollable, Sortable, VideosSortDialog.OnFilter {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChannelVideosViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<Video, out RecyclerView.ViewHolder>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = ChannelVideosAdapter(this, {
            lastSelectedItem = it
            showDownloadDialog()
        }, {
            lastSelectedItem = it
            viewModel.saveBookmark(requireContext(), it)
        })
        setAdapter(binding.recyclerView, pagingAdapter)
    }

    override fun initialize() {
        initializeAdapter(binding, pagingAdapter, viewModel.flow)
        initializeVideoAdapter(viewModel, pagingAdapter as BaseVideosAdapter)
    }

    override fun setupSortBar(sortBar: SortBarBinding) {
        sortBar.root.visible()
        sortBar.root.setOnClickListener {
            VideosSortDialog.newInstance(
                sort = viewModel.sort,
                period = viewModel.period,
                type = viewModel.type,
                saveSort = viewModel.saveSort,
                saveDefault = requireContext().prefs().getBoolean(C.SORT_DEFAULT_CHANNEL_VIDEOS, false)
            ).show(childFragmentManager, null)
        }
        viewModel.sortText.observe(viewLifecycleOwner) {
            sortBar.sortText.text = it
        }
    }

    override fun onChange(sort: VideoSortEnum, sortText: CharSequence, period: VideoPeriodEnum, periodText: CharSequence, type: BroadcastTypeEnum, languageIndex: Int, saveSort: Boolean, saveDefault: Boolean) {
        if ((parentFragment as? FragmentHost)?.currentFragment == this) {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.scrollTop.gone()
                pagingAdapter.submitData(PagingData.empty())
                viewModel.filter(
                    sort = sort,
                    type = type,
                    text = getString(R.string.sort_and_period, sortText, periodText),
                    saveSort = saveSort,
                    saveDefault = saveDefault
                )
            }
        }
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
        pagingAdapter.retry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}