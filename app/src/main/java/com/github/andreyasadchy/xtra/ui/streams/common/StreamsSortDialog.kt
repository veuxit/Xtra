package com.github.andreyasadchy.xtra.ui.streams.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogStreamsSortBinding
import com.github.andreyasadchy.xtra.ui.search.tags.TagSearchFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class StreamsSortDialog : BottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: String)
    }

    companion object {
        const val SORT_VIEWERS = "VIEWER_COUNT"
        const val SORT_VIEWERS_ASC = "VIEWER_COUNT_ASC"

        private const val SORT = "sort"

        fun newInstance(sort: String?): StreamsSortDialog {
            return StreamsSortDialog().apply {
                arguments = bundleOf(SORT to sort)
            }
        }
    }

    private var _binding: DialogStreamsSortBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnFilter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStreamsSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        with(binding) {
            val args = requireArguments()
            val originalSortId = when (args.getString(SORT)) {
                SORT_VIEWERS -> R.id.viewers_high
                SORT_VIEWERS_ASC -> R.id.viewers_low
                else -> R.id.viewers_high
            }
            sort.check(originalSortId)
            apply.setOnClickListener {
                val checkedSortId = sort.checkedRadioButtonId
                if (checkedSortId != originalSortId) {
                    listener.onChange(
                        when (checkedSortId) {
                            R.id.viewers_high -> SORT_VIEWERS
                            R.id.viewers_low -> SORT_VIEWERS_ASC
                            else -> SORT_VIEWERS
                        }
                    )
                }
                dismiss()
            }
            selectTags.setOnClickListener {
                findNavController().navigate(TagSearchFragmentDirections.actionGlobalTagSearchFragment(
                    gameId = parentFragment?.arguments?.getString(C.GAME_ID),
                    gameSlug = parentFragment?.arguments?.getString(C.GAME_SLUG),
                    gameName = parentFragment?.arguments?.getString(C.GAME_NAME)
                ))
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}