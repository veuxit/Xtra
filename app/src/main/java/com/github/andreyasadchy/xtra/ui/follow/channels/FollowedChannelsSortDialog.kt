package com.github.andreyasadchy.xtra.ui.follow.channels

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.os.bundleOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogFollowedChannelsSortBinding
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum.ASC
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum.DESC
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum.ALPHABETICALLY
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum.FOLLOWED_AT
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum.LAST_BROADCAST
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment

class FollowedChannelsSortDialog : ExpandingBottomSheetDialogFragment() {

    interface OnFilter {
        fun onChange(sort: FollowSortEnum, sortText: CharSequence, order: FollowOrderEnum, orderText: CharSequence, saveDefault: Boolean)
    }

    companion object {

        private const val SORT = "sort"
        private const val ORDER = "order"
        private const val SAVE_DEFAULT = "save_default"

        fun newInstance(sort: FollowSortEnum, order: FollowOrderEnum, saveDefault: Boolean = false): FollowedChannelsSortDialog {
            return FollowedChannelsSortDialog().apply {
                arguments = bundleOf(SORT to sort, ORDER to order, SAVE_DEFAULT to saveDefault)
            }
        }
    }

    private var _binding: DialogFollowedChannelsSortBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnFilter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFollowedChannelsSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val args = requireArguments()
            val originalSortId = when (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    args.getSerializable(SORT, FollowSortEnum::class.java)!!
                } else {
                    @Suppress("DEPRECATION")
                    args.getSerializable(SORT) as FollowSortEnum
                }) {
                FOLLOWED_AT -> R.id.time_followed
                ALPHABETICALLY -> R.id.alphabetically
                LAST_BROADCAST -> R.id.last_broadcast
            }
            val originalOrderId = if (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    args.getSerializable(ORDER, FollowOrderEnum::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    args.getSerializable(ORDER) as? FollowOrderEnum
                } == DESC) R.id.newest_first else R.id.oldest_first
            val originalSaveDefault = args.getBoolean(SAVE_DEFAULT)
            sort.check(originalSortId)
            order.check(originalOrderId)
            saveDefault.isChecked = originalSaveDefault
            apply.setOnClickListener {
                val checkedSortId = sort.checkedRadioButtonId
                val checkedOrderId = order.checkedRadioButtonId
                val checkedSaveDefault = saveDefault.isChecked
                if (checkedSortId != originalSortId || checkedOrderId != originalOrderId || checkedSaveDefault != originalSaveDefault) {
                    val sortBtn = view.findViewById<RadioButton>(checkedSortId)
                    val orderBtn = view.findViewById<RadioButton>(checkedOrderId)
                    listener.onChange(
                        when (checkedSortId) {
                            R.id.time_followed -> FOLLOWED_AT
                            R.id.alphabetically -> ALPHABETICALLY
                            else -> LAST_BROADCAST
                        },
                        sortBtn.text,
                        if (checkedOrderId == R.id.newest_first) DESC else ASC,
                        orderBtn.text,
                        checkedSaveDefault
                    )
                }
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}