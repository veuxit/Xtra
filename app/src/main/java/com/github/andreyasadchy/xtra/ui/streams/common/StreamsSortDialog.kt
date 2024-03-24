package com.github.andreyasadchy.xtra.ui.streams.common

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogStreamsSortBinding
import com.github.andreyasadchy.xtra.model.ui.StreamSortEnum
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment
import com.github.andreyasadchy.xtra.ui.search.tags.TagSearchFragmentDirections
import com.github.andreyasadchy.xtra.util.C

class StreamsSortDialog : ExpandingBottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: StreamSortEnum)
    }

    companion object {

        private const val SORT = "sort"

        fun newInstance(sort: StreamSortEnum? = StreamSortEnum.VIEWERS_HIGH): StreamsSortDialog {
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
        with(binding) {
            val args = requireArguments()
            val originalSortId = if (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    args.getSerializable(SORT, StreamSortEnum::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    args.getSerializable(SORT) as? StreamSortEnum
                } == StreamSortEnum.VIEWERS_HIGH) R.id.viewers_high else R.id.viewers_low
            sort.check(originalSortId)
            apply.setOnClickListener {
                val checkedSortId = sort.checkedRadioButtonId
                if (checkedSortId != originalSortId) {
                    listener.onChange(
                        if (checkedSortId == R.id.viewers_high) StreamSortEnum.VIEWERS_HIGH else StreamSortEnum.VIEWERS_LOW
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