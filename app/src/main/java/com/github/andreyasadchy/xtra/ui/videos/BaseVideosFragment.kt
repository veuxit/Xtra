package com.github.andreyasadchy.xtra.ui.videos

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseVideosFragment : PagedListFragment(), HasDownloadDialog {

    var lastSelectedItem: Video? = null

    fun <T : Any, VH : RecyclerView.ViewHolder> initializeVideoAdapter(viewModel: BaseVideosViewModel, adapter: BaseVideosAdapter<T, VH>) {
        if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.positions.collectLatest {
                        adapter.setVideoPositions(it)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bookmarks.collectLatest {
                    adapter.setBookmarksList(it)
                }
            }
        }
    }

    override fun showDownloadDialog() {
        lastSelectedItem?.let {
            DownloadDialog.newInstance(it).show(childFragmentManager, null)
        }
    }
}