package com.github.andreyasadchy.xtra.ui.streams.followed

import android.os.Bundle
import androidx.fragment.app.viewModels
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.streams.BaseStreamsFragment
import com.github.andreyasadchy.xtra.ui.streams.StreamsCompactAdapter
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FollowedStreamsFragment : BaseStreamsFragment<FollowedStreamsViewModel>() {

    override val viewModel: FollowedStreamsViewModel by viewModels()

    override val adapter: BasePagedListAdapter<Stream> by lazy {
        if (!compactStreams) {
            super.adapter
        } else {
            val activity = requireActivity() as MainActivity
            StreamsCompactAdapter(this, activity, activity, activity)
        }
    }

    private var compactStreams = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        compactStreams = requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") != "disabled"
    }

    override fun initialize() {
        super.initialize()
        viewModel.loadStreams(
            user = User.get(requireContext()),
            helixClientId = requireContext().prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
            gqlClientId = requireContext().prefs().getString(C.GQL_CLIENT_ID, "kimne78kx3ncx6brgo4mv6wki5h1ko"),
            apiPref = TwitchApiHelper.listFromPrefs(requireContext().prefs().getString(C.API_PREF_FOLLOWED_STREAMS, ""), TwitchApiHelper.followedStreamsApiDefaults),
            thumbnailsEnabled = !compactStreams
        )
    }
}
