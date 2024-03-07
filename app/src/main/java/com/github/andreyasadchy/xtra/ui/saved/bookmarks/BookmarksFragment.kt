package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.download.VideoDownloadDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BookmarksFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BookmarksViewModel by viewModels()
    private lateinit var pagingAdapter: PagingDataAdapter<Bookmark, out RecyclerView.ViewHolder>
    override var enableNetworkCheck = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagingAdapter = BookmarksAdapter(this, {
            viewModel.updateVideo(
                context = requireContext(),
                helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                helixToken = Account.get(requireContext()).helixToken,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                videoId = it
            )
        }, {
            if (DownloadUtils.hasStoragePermission(requireActivity())) {
                VideoDownloadDialog.newInstance(it).show(childFragmentManager, null)
            }
        }, {
            viewModel.vodIgnoreUser(it)
        }, {
            val delete = getString(R.string.delete)
            requireActivity().getAlertDialogBuilder()
                .setTitle(delete)
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(delete) { _, _ -> viewModel.delete(requireContext(), it) }
                .setNegativeButton(getString(android.R.string.cancel), null)
                .show()
        })
        with(binding) {
            pagingAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    pagingAdapter.unregisterAdapterDataObserver(this)
                    pagingAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                            if (positionStart == 0) {
                                recyclerView.smoothScrollToPosition(0)
                            }
                        }
                    })
                }
            })
            recyclerView.adapter = pagingAdapter
            (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    override fun initialize() {
        initializeAdapter(binding, pagingAdapter, viewModel.flow, enableSwipeRefresh = false, enableScrollTopButton = false)
        if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
            viewModel.positions.observe(viewLifecycleOwner) {
                (pagingAdapter as BookmarksAdapter).setVideoPositions(it)
            }
        }
        if (requireContext().prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true)) {
            viewModel.ignoredUsers.observe(viewLifecycleOwner) {
                (pagingAdapter as BookmarksAdapter).setIgnoredUsers(it)
            }
            viewModel.updateUsers(
                helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                helixToken = Account.get(requireContext()).helixToken,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            )
        }
        if (!Account.get(requireContext()).helixToken.isNullOrBlank()) {
            viewModel.updateVideos(
                context = requireContext(),
                helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                helixToken = Account.get(requireContext()).helixToken,
            )
        }
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
