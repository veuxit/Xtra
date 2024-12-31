package com.github.andreyasadchy.xtra.model.ui

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sort_game")
class SortGame(
    @PrimaryKey
    val id: String,
    var saveSort: Boolean? = null,
    var videoSort: String? = null,
    var videoPeriod: String? = null,
    var videoType: String? = null,
    var videoLanguageIndex: Int? = null,
    var clipPeriod: String? = null,
    var clipLanguageIndex: Int? = null,
)
