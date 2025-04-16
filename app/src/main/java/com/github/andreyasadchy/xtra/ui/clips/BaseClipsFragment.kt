package com.github.andreyasadchy.xtra.ui.clips

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.HasDownloadDialog
import com.github.andreyasadchy.xtra.util.DownloadUtils

abstract class BaseClipsFragment : PagedListFragment(), HasDownloadDialog {

    var lastSelectedItem: Clip? = null

    override fun showDownloadDialog() {
        if (DownloadUtils.hasStoragePermission(requireActivity())) {
            lastSelectedItem?.let {
                DownloadDialog.newInstance(
                    clipId = it.id,
                    title = it.title,
                    uploadDate = it.uploadDate,
                    duration = it.duration,
                    videoId = it.videoId,
                    vodOffset = it.vodOffset,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    channelLogo = it.channelLogo,
                    thumbnailUrl = it.thumbnailUrl,
                    thumbnail = it.thumbnail,
                    gameId = it.gameId,
                    gameSlug = it.gameSlug,
                    gameName = it.gameName,
                ).show(childFragmentManager, null)
            }
        }
    }
}
