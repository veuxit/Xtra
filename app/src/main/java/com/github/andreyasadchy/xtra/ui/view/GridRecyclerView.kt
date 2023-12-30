package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getActivity
import com.github.andreyasadchy.xtra.util.prefs

class GridRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val prefs = context.prefs()
    private val portraitColumns = prefs.getString(C.PORTRAIT_COLUMN_COUNT, "1")!!.toInt()
    private val landscapeColumns = prefs.getString(C.LANDSCAPE_COLUMN_COUNT, "2")!!.toInt()

    val gridLayoutManager: GridLayoutManager

    init {
        val columns = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if ((context.getActivity() as? MainActivity)?.orientation == 1) portraitColumns else landscapeColumns
        } else {
            getColumnsForConfiguration(resources.configuration)
        }
        gridLayoutManager = GridLayoutManager(context, columns)
        layoutManager = gridLayoutManager
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val columns = getColumnsForConfiguration(newConfig)
        gridLayoutManager.spanCount = columns
    }

    fun getColumnsForConfiguration(configuration: Configuration): Int {
        return if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) portraitColumns else landscapeColumns
    }
}