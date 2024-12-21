package com.github.andreyasadchy.xtra.ui.videos

import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark

abstract class BaseVideosAdapter<T: Any, VH : RecyclerView.ViewHolder>(diffCallback: DiffUtil.ItemCallback<T>) : PagingDataAdapter<T, VH>(diffCallback) {

    protected var positions: List<VideoPosition>? = null

    fun setVideoPositions(positions: List<VideoPosition>) {
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