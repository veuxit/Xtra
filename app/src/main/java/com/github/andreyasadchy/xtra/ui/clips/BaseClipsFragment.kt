package com.github.andreyasadchy.xtra.ui.clips

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog

abstract class BaseClipsFragment : PagedListFragment(), HasDownloadDialog {

    var lastSelectedItem: Clip? = null

    override fun showDownloadDialog() {
        lastSelectedItem?.let {
            DownloadDialog.newInstance(it).show(childFragmentManager, null)
        }
    }
}
