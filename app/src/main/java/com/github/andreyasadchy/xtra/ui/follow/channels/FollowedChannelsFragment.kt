package com.github.andreyasadchy.xtra.ui.follow.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentFollowedChannelsBinding
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowedChannelsFragment : PagedListFragment(), Scrollable, FollowedChannelsSortDialog.OnFilter {

    private var _binding: FragmentFollowedChannelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FollowedChannelsViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<User, out RecyclerView.ViewHolder>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFollowedChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = FollowedChannelsAdapter(this)
        setAdapter(binding.recyclerViewLayout.recyclerView, pagingAdapter)
    }

    override fun initialize() {
        initializeAdapter(binding.recyclerViewLayout, pagingAdapter, viewModel.flow, enableScrollTopButton = false)
        with(binding) {
            sortBar.root.visible()
            sortBar.root.setOnClickListener {
                FollowedChannelsSortDialog.newInstance(
                    sort = viewModel.sort,
                    order = viewModel.order,
                    saveDefault = requireContext().prefs().getBoolean(C.SORT_DEFAULT_FOLLOWED_CHANNELS, false)
                ).show(childFragmentManager, null)
            }
            viewModel.sortText.observe(viewLifecycleOwner) {
                sortBar.sortText.text = it
            }
        }
    }

    override fun onChange(sort: FollowSortEnum, sortText: CharSequence, order: FollowOrderEnum, orderText: CharSequence, saveDefault: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            pagingAdapter.submitData(PagingData.empty())
            viewModel.filter(
                sort = sort,
                order = order,
                text = getString(R.string.sort_and_order, sortText, orderText),
                saveDefault = saveDefault
            )
        }
    }

    override fun scrollToTop() {
        binding.recyclerViewLayout.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
        pagingAdapter.retry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}