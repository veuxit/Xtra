package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment
import com.github.andreyasadchy.xtra.ui.view.GridRecyclerView
import com.github.andreyasadchy.xtra.util.C


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
            context.obtainStyledAttributes(intArrayOf(R.attr.dialogLayoutPadding)).use {
                setPadding(it.getDimensionPixelSize(0, 0))
            }
            adapter = PlayerGamesDialogAdapter(this@PlayerGamesDialog).also {
                it.submitList(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arguments?.getParcelableArrayList(C.GAMES_LIST, Game::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        arguments?.getParcelableArrayList(C.GAMES_LIST)
                    }?.toList()
                )
            }
        }
        return NestedScrollView(context).apply { addView(recycleView) }
    }
}
