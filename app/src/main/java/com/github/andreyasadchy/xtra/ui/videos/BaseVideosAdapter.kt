package com.github.andreyasadchy.xtra.ui.videos

import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.offline.Bookmark

abstract class BaseVideosAdapter<T: Any, VH : RecyclerView.ViewHolder>(diffCallback: DiffUtil.ItemCallback<T>) : PagingDataAdapter<T, VH>(diffCallback) {

    protected var positions: Map<Long, Long>? = null

    fun setVideoPositions(positions: Map<Long, Long>) {
        this.positions = positions
        if (itemCount != 0) {
            notifyDataSetChanged()
        }
    }

    protected var bookmarks: List<Bookmark>? = null

    fun setBookmarksList(list: List<Bookmark>) {
        this.bookmarks = list
    }
}