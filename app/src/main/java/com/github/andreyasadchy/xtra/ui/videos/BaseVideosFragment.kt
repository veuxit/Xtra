package com.github.andreyasadchy.xtra.ui.videos

import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

abstract class BaseVideosFragment : PagedListFragment(), HasDownloadDialog {

    var lastSelectedItem: Video? = null

    fun <T : Any, VH : RecyclerView.ViewHolder> initializeVideoAdapter(viewModel: BaseVideosViewModel, adapter: BaseVideosAdapter<T, VH>) {
        if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            viewModel.positions.observe(viewLifecycleOwner) {
                adapter.setVideoPositions(it)
            }
        }
        viewModel.bookmarks.observe(viewLifecycleOwner) {
            adapter.setBookmarksList(it)
        }
    }

    override fun showDownloadDialog() {
        lastSelectedItem?.let {
            DownloadDialog.newInstance(it).show(childFragmentManager, null)
        }
    }
}