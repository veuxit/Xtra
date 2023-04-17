package com.github.andreyasadchy.xtra.ui.settings

import androidx.lifecycle.ViewModel
import com.github.andreyasadchy.xtra.db.VideoPositionsDao
import com.github.andreyasadchy.xtra.db.VideosDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val videoPositions: VideoPositionsDao,
    private val videos: VideosDao) : ViewModel() {

    fun deletePositions() {
        GlobalScope.launch {
            videoPositions.deleteAll()
            videos.deletePositions()
        }
    }
}