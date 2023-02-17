package com.github.andreyasadchy.xtra.ui.search.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.AlertDialogFragment
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchFragment
import com.github.andreyasadchy.xtra.ui.search.Searchable
import com.github.andreyasadchy.xtra.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.common_recycler_view_layout.*
import kotlinx.android.synthetic.main.fragment_search.*

@AndroidEntryPoint
class ChannelSearchFragment : PagedListFragment<User, ChannelSearchViewModel, BasePagedListAdapter<User>>(), Searchable, UserResultDialog.OnUserResultListener, AlertDialogFragment.OnDialogResultListener {

    override val viewModel: ChannelSearchViewModel by viewModels()
    override val adapter: BasePagedListAdapter<User> by lazy { ChannelSearchAdapter(this, requireActivity() as MainActivity) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.common_recycler_view_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh.isEnabled = false
        (parentFragment as? SearchFragment)?.menu?.apply {
            visible()
            setOnClickListener {
                UserResultDialog.show(childFragmentManager)
            }
        }
    }

    override fun search(query: String) {
        if (query.isNotEmpty()) { //TODO same query doesn't fire
            viewModel.setQuery(
                query = query,
                helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                helixToken = Account.get(requireContext()).helixToken,
                gqlClientId = requireContext().prefs().getString(C.GQL_CLIENT_ID, "kimne78kx3ncx6brgo4mv6wki5h1ko"),
                apiPref = TwitchApiHelper.listFromPrefs(requireContext().prefs().getString(C.API_PREF_SEARCH_CHANNEL, ""), TwitchApiHelper.searchChannelsApiDefaults)
            )
        } else {
            adapter.submitList(null)
            nothingHere?.gone()
        }
    }

    private var userResult: Pair<Int?, String?>? = null

    private fun viewUserResult() {
        userResult?.let {
            when (it.first) {
                0 -> (requireActivity() as? MainActivity)?.viewChannel(it.second, null, null, null)
                1 -> (requireActivity() as? MainActivity)?.viewChannel(null, it.second, null, null)
                else -> {}
            }
        }
    }

    override fun onUserResult(resultCode: Int, checkedId: Int, result: String) {
        if (result.isNotBlank()) {
            userResult = Pair(checkedId, result)
            when (resultCode) {
                UserResultDialog.RESULT_POSITIVE -> {
                    viewModel.loadUserResult(requireContext().prefs().getString(C.GQL_CLIENT_ID, "kimne78kx3ncx6brgo4mv6wki5h1ko"), checkedId, result)
                    viewModel.userResult.observe(viewLifecycleOwner) {
                        if (it != null) {
                            if (!it.first.isNullOrBlank()) {
                                AlertDialogFragment.show(
                                    fragmentManager = childFragmentManager,
                                    requestCode = 0,
                                    title = it.first,
                                    message = it.second,
                                    positiveButton = getString(R.string.view_profile),
                                    negativeButton = getString(android.R.string.cancel),
                                )
                            } else {
                                viewUserResult()
                            }
                        }
                    }
                }
                UserResultDialog.RESULT_NEUTRAL -> viewUserResult()
            }
        }
    }

    override fun onDialogResult(requestCode: Int, resultCode: Int) {
        if (requestCode == 0 && resultCode == AlertDialogFragment.RESULT_POSITIVE) {
            viewUserResult()
        }
    }
}