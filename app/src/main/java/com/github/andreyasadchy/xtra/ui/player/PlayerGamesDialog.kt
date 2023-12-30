package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment
import com.github.andreyasadchy.xtra.ui.view.GridRecyclerView
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.convertDpToPixels


class PlayerGamesDialog : ExpandingBottomSheetDialogFragment() {

    interface PlayerSeekListener {
        fun seek(position: Long)
    }

    companion object {

        fun newInstance(gamesList: List<Game>): PlayerGamesDialog {
            return PlayerGamesDialog().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(C.GAMES_LIST, ArrayList(gamesList))
                }
            }
        }
    }

    lateinit var listener: PlayerSeekListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as PlayerSeekListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val recycleView = GridRecyclerView(context).apply {
            id = R.id.recyclerView
            layoutParams = params
            setPadding(context.convertDpToPixels(8F))
            adapter = PlayerGamesDialogAdapter(this@PlayerGamesDialog).also {
                it.submitList(arguments?.getParcelableArrayList<Game>(C.GAMES_LIST)?.toList())
            }
        }
        return NestedScrollView(context).apply { addView(recycleView) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view?.findViewById<GridRecyclerView>(R.id.recyclerView)?.apply {
                gridLayoutManager.spanCount = getColumnsForConfiguration(newConfig)
            }
        }
    }
}
