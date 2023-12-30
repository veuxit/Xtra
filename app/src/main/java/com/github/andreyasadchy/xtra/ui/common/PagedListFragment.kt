package com.github.andreyasadchy.xtra.ui.common

import android.content.res.Configuration
import android.os.Build
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.view.GridRecyclerView
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class PagedListFragment : BaseNetworkFragment() {

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view?.findViewById<GridRecyclerView>(R.id.recyclerView)?.apply {
                gridLayoutManager.spanCount = getColumnsForConfiguration(newConfig)
            }
        }
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> setAdapter(recyclerView: RecyclerView, adapter: PagingDataAdapter<T, VH>) {
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                adapter.unregisterAdapterDataObserver(this)
                adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        try {
                            if (positionStart == 0) {
                                recyclerView.scrollToPosition(0)
                            }
                        } catch (e: Exception) {

                        }
                    }
                })
            }
        })
        recyclerView.adapter = adapter
    }

    private fun shouldShowButton(recyclerView: RecyclerView): Boolean {
        val offset = recyclerView.computeVerticalScrollOffset()
        if (offset < 0) {
            return false
        }
        val extent = recyclerView.computeVerticalScrollExtent()
        val range = recyclerView.computeVerticalScrollRange()
        val percentage = (100f * offset / (range - extent).toFloat())
        return percentage > 3f
    }

    fun <T : Any, VH : RecyclerView.ViewHolder> initializeAdapter(binding: CommonRecyclerViewLayoutBinding, pagingAdapter: PagingDataAdapter<T, VH>, flow: Flow<PagingData<T>>, enableSwipeRefresh: Boolean = true, enableScrollTopButton: Boolean = true) {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                flow.collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                pagingAdapter.loadStateFlow.collectLatest { loadState ->
                    progressBar.isVisible = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount == 0
                    if (enableSwipeRefresh) {
                        swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading && pagingAdapter.itemCount != 0
                    }
                    nothingHere.isVisible = loadState.refresh !is LoadState.Loading && pagingAdapter.itemCount == 0
                    if ((loadState.refresh as? LoadState.Error ?: loadState.append as? LoadState.Error ?: loadState.prepend as? LoadState.Error)?.error?.message == "failed integrity check" &&
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                        IntegrityDialog.show(childFragmentManager)
                    }
                }
            }
            if (enableSwipeRefresh) {
                swipeRefresh.isEnabled = true
                swipeRefresh.setOnRefreshListener { pagingAdapter.refresh() }
            }
            if (enableScrollTopButton && requireContext().prefs().getBoolean(C.UI_SCROLLTOP, true)) {
                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        scrollTop.isVisible = shouldShowButton(recyclerView)
                    }
                })
                scrollTop.setOnClickListener {
                    (parentFragment as? Scrollable)?.scrollToTop()
                    it.gone()
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
}