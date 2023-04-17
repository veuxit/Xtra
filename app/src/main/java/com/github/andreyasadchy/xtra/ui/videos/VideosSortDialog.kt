package com.github.andreyasadchy.xtra.ui.videos

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogVideosSortBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum.ALL
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum.DAY
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum.MONTH
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum.WEEK
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum.TIME
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum.VIEWS
import com.github.andreyasadchy.xtra.ui.clips.common.ClipsFragment
import com.github.andreyasadchy.xtra.ui.common.ExpandingBottomSheetDialogFragment
import com.github.andreyasadchy.xtra.ui.common.RadioButtonDialogFragment
import com.github.andreyasadchy.xtra.ui.videos.channel.ChannelVideosFragment
import com.github.andreyasadchy.xtra.ui.videos.followed.FollowedVideosFragment
import com.github.andreyasadchy.xtra.ui.videos.game.GameVideosFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FragmentUtils
import com.github.andreyasadchy.xtra.util.gone

class VideosSortDialog : ExpandingBottomSheetDialogFragment(), RadioButtonDialogFragment.OnSortOptionChanged {

    interface OnFilter {
        fun onChange(sort: VideoSortEnum, sortText: CharSequence, period: VideoPeriodEnum, periodText: CharSequence, type: BroadcastTypeEnum, languageIndex: Int, saveSort: Boolean, saveDefault: Boolean)
    }

    companion object {

        private const val SORT = "sort"
        private const val PERIOD = "period"
        private const val TYPE = "type"
        private const val LANGUAGE = "language"
        private const val SAVE_SORT = "save_sort"
        private const val SAVE_DEFAULT = "save_default"
        private const val CLIP_CHANNEL = "clip_channel"

        private const val REQUEST_CODE_LANGUAGE = 0

        fun newInstance(sort: VideoSortEnum? = VIEWS, period: VideoPeriodEnum? = ALL, type: BroadcastTypeEnum? = BroadcastTypeEnum.ALL, languageIndex: Int? = 0, saveSort: Boolean = false, saveDefault: Boolean = false, clipChannel: Boolean = false): VideosSortDialog {
            return VideosSortDialog().apply {
                arguments = bundleOf(SORT to sort, PERIOD to period, TYPE to type, LANGUAGE to languageIndex, SAVE_SORT to saveSort, SAVE_DEFAULT to saveDefault, CLIP_CHANNEL to clipChannel)
            }
        }
    }

    private var _binding: DialogVideosSortBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnFilter

    private var langIndex = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogVideosSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val args = requireArguments()
            when (parentFragment) {
                is ClipsFragment -> {
                    if (args.getBoolean(CLIP_CHANNEL)) {
                        sort.gone()
                        sortType.gone()
                        selectLang.gone()
                        saveSort.text = requireContext().getString(R.string.save_sort_channel)
                        saveSort.isVisible = parentFragment?.arguments?.getString(C.CHANNEL_ID).isNullOrBlank() == false
                    } else {
                        sort.gone()
                        sortType.gone()
                        saveSort.text = requireContext().getString(R.string.save_sort_game)
                        saveSort.isVisible = parentFragment?.arguments?.getString(C.GAME_ID).isNullOrBlank() == false
                    }
                }
                is ChannelVideosFragment -> {
                    period.gone()
                    selectLang.gone()
                    saveSort.text = requireContext().getString(R.string.save_sort_channel)
                    saveSort.isVisible = parentFragment?.arguments?.getString(C.CHANNEL_ID).isNullOrBlank() == false
                }
                is FollowedVideosFragment -> {
                    period.gone()
                    selectLang.gone()
                    saveSort.gone()
                }
                is GameVideosFragment -> {
                    if (Account.get(requireContext()).helixToken.isNullOrBlank()) {
                        period.gone()
                    }
                    saveSort.text = requireContext().getString(R.string.save_sort_game)
                    saveSort.isVisible = parentFragment?.arguments?.getString(C.GAME_ID).isNullOrBlank() == false
                }
            }
            val originalSortId = if (args.getSerializable(SORT) as VideoSortEnum == TIME) R.id.time else R.id.views
            val originalPeriodId = when (args.getSerializable(PERIOD) as VideoPeriodEnum) {
                DAY -> R.id.today
                WEEK -> R.id.week
                MONTH -> R.id.month
                ALL -> R.id.all
            }
            val originalTypeId = when (args.getSerializable(TYPE) as BroadcastTypeEnum) {
                BroadcastTypeEnum.ARCHIVE -> R.id.typeArchive
                BroadcastTypeEnum.HIGHLIGHT -> R.id.typeHighlight
                BroadcastTypeEnum.UPLOAD -> R.id.typeUpload
                BroadcastTypeEnum.ALL -> R.id.typeAll
            }
            val originalLanguageIndex = args.getSerializable(LANGUAGE)
            val originalSaveSort = args.getBoolean(SAVE_SORT)
            val originalSaveDefault = args.getBoolean(SAVE_DEFAULT)
            sort.check(originalSortId)
            period.check(originalPeriodId)
            sortType.check(originalTypeId)
            langIndex = args.getInt(LANGUAGE)
            saveSort.isChecked = originalSaveSort
            saveDefault.isChecked = originalSaveDefault
            apply.setOnClickListener {
                val checkedPeriodId = period.checkedRadioButtonId
                val checkedSortId = sort.checkedRadioButtonId
                val checkedTypeId = sortType.checkedRadioButtonId
                val checkedSaveSort = saveSort.isChecked
                val checkedSaveDefault = saveDefault.isChecked
                if (checkedPeriodId != originalPeriodId || checkedSortId != originalSortId || checkedTypeId != originalTypeId || langIndex != originalLanguageIndex || checkedSaveSort != originalSaveSort || checkedSaveDefault != originalSaveDefault) {
                    val sortBtn = view.findViewById<RadioButton>(checkedSortId)
                    val periodBtn = view.findViewById<RadioButton>(checkedPeriodId)
                    listener.onChange(
                        if (checkedSortId == R.id.time) TIME else VIEWS,
                        sortBtn.text,
                        when (checkedPeriodId) {
                            R.id.today -> DAY
                            R.id.week -> WEEK
                            R.id.month -> MONTH
                            else -> ALL
                        },
                        periodBtn.text,
                        when (checkedTypeId) {
                            R.id.typeArchive -> BroadcastTypeEnum.ARCHIVE
                            R.id.typeHighlight -> BroadcastTypeEnum.HIGHLIGHT
                            R.id.typeUpload -> BroadcastTypeEnum.UPLOAD
                            else -> BroadcastTypeEnum.ALL
                        },
                        langIndex,
                        checkedSaveSort,
                        checkedSaveDefault
                    )
                }
                dismiss()
            }
            val langArray = resources.getStringArray(R.array.gqlUserLanguageEntries).toList()
            selectLang.setOnClickListener {
                FragmentUtils.showRadioButtonDialogFragment(childFragmentManager, langArray, langIndex, REQUEST_CODE_LANGUAGE)
            }
        }
    }

    override fun onChange(requestCode: Int, index: Int, text: CharSequence, tag: Int?) {
        when (requestCode) {
            REQUEST_CODE_LANGUAGE -> {
                langIndex = index
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
