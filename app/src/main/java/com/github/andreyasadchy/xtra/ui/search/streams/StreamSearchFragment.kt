package com.github.andreyasadchy.xtra.ui.search.streams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.ui.streams.StreamsAdapter
import com.github.andreyasadchy.xtra.ui.streams.StreamsCompactAdapter
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StreamSearchFragment : PagedListFragment(), Searchable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StreamSearchViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<Stream, out RecyclerView.ViewHolder>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = if (requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") == "all") {
            StreamsCompactAdapter(this)
        } else {
            StreamsAdapter(this)
        }
        setAdapter(binding.recyclerView, pagingAdapter)
    }

    override fun initialize() {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.flow.collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                pagingAdapter.loadStateFlow.collectLatest { loadState ->
                    progressBar.isVisible = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount == 0
                    nothingHere.isVisible = loadState.refresh !is LoadState.Loading && pagingAdapter.itemCount == 0 && viewModel.query.value.isNotBlank()
                    if ((loadState.refresh as? LoadState.Error ?: loadState.append as? LoadState.Error ?: loadState.prepend as? LoadState.Error)?.error?.message == "failed integrity check" &&
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                        IntegrityDialog.show(childFragmentManager)
                    }
                }
            }
        }
        childFragmentManager.setFragmentResultListener("integrity", this) { _, bundle ->
            if (bundle.getBoolean("refresh")) {
                pagingAdapter.refresh()
            }
        }
        if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true) && TwitchApiHelper.isIntegrityTokenExpired(requireContext())) {
            IntegrityDialog.show(childFragmentManager)
        }
    }

    override fun search(query: String) {
        viewModel.setQuery(query)
    }

    override fun onNetworkRestored() {
        pagingAdapter.retry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}