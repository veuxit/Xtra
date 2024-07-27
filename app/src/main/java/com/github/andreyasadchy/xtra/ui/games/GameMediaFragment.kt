package com.github.andreyasadchy.xtra.ui.games

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentMediaBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.NotLoggedIn
import com.github.andreyasadchy.xtra.ui.clips.common.ClipsFragment
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.streams.common.StreamsFragment
import com.github.andreyasadchy.xtra.ui.videos.game.GameVideosFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FragmentUtils
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.nullIfEmpty
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameMediaFragment : BaseNetworkFragment(), Scrollable, FragmentHost {

    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GamePagerViewModel by viewModels()

    private var previousItem = -1

    override val currentFragment: Fragment?
        get() = childFragmentManager.findFragmentById(R.id.fragmentContainer)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousItem = savedInstanceState?.getInt("previousItem", -1) ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.integrity.observe(viewLifecycleOwner) {
            if (requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) && requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)) {
                IntegrityDialog.show(childFragmentManager)
            }
        }
        with(binding) {
            val activity = requireActivity() as MainActivity
            val account = Account.get(activity)
            val navController = findNavController()
            val appBarConfiguration = AppBarConfiguration(setOf(R.id.rootGamesFragment, R.id.rootTopFragment, R.id.followPagerFragment, R.id.followMediaFragment, R.id.savedPagerFragment, R.id.savedMediaFragment))
            toolbar.setupWithNavController(navController, appBarConfiguration)
            toolbar.title = args.gameName
            toolbar.menu.findItem(R.id.login).title = if (account !is NotLoggedIn) getString(R.string.log_out) else getString(R.string.log_in)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.followButton -> {
                        viewModel.follow.value?.let {
                            val following = it.first
                            val errorMessage = it.second
                            if (errorMessage.isNullOrBlank()) {
                                if (!following) {
                                    viewModel.saveFollowGame(args.gameId, args.gameSlug, args.gameName)
                                } else {
                                    FragmentUtils.showUnfollowDialog(requireContext(), args.gameName) {
                                        viewModel.deleteFollowGame(args.gameId)
                                    }
                                }
                            }
                        }
                        true
                    }
                    R.id.search -> {
                        findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
                        true
                    }
                    R.id.settings -> {
                        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
                        true
                    }
                    R.id.login -> {
                        if (account is NotLoggedIn) {
                            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                        } else {
                            activity.getAlertDialogBuilder().apply {
                                setTitle(getString(R.string.logout_title))
                                account.login?.nullIfEmpty()?.let { user -> setMessage(getString(R.string.logout_msg, user)) }
                                setNegativeButton(getString(R.string.no), null)
                                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
                            }.show()
                        }
                        true
                    }
                    else -> false
                }
            }
            if ((requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0) < 2) {
                val followButton = toolbar.menu.findItem(R.id.followButton)
                followButton.isVisible = true
                var initialized = false
                viewModel.follow.observe(viewLifecycleOwner) { pair ->
                    val following = pair.first
                    val errorMessage = pair.second
                    if (initialized) {
                        if (!errorMessage.isNullOrBlank()) {
                            requireContext().shortToast(errorMessage)
                        } else {
                            requireContext().shortToast(requireContext().getString(if (following) R.string.now_following else R.string.unfollowed, args.gameName))
                        }
                    } else {
                        initialized = true
                    }
                    if (errorMessage.isNullOrBlank()) {
                        followButton.icon = ContextCompat.getDrawable(requireContext(), if (following) R.drawable.baseline_favorite_black_24 else R.drawable.baseline_favorite_border_black_24)
                        followButton.title = requireContext().getString(if (following) R.string.unfollow else R.string.follow)
                    }
                }
            }
            if (!args.gameId.isNullOrBlank() || !args.gameName.isNullOrBlank()) {
                spinner.visible()
                (spinner.editText as? MaterialAutoCompleteTextView)?.apply {
                    setSimpleItems(resources.getStringArray(R.array.spinnerMedia))
                    setOnItemClickListener { _, _, position, _ ->
                        if (position != previousItem) {
                            childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(position)).commit()
                            previousItem = position
                        }
                    }
                    if (previousItem == -1) {
                        childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(0)).commit()
                        previousItem = 0
                    }
                    setText(adapter.getItem(previousItem).toString(), false)
                }
            } else {
                if (previousItem == -1) {
                    childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(0)).commit()
                    previousItem = 0
                }
            }
            childFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                    if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                        f.view?.findViewById<RecyclerView>(R.id.recyclerView)?.let {
                            appBar.setLiftOnScrollTargetView(it)
                            it.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                    super.onScrolled(recyclerView, dx, dy)
                                    appBar.isLifted = recyclerView.canScrollVertically(-1)
                                }
                            })
                            it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                appBar.isLifted = it.canScrollVertically(-1)
                            }
                        }
                    } else {
                        appBar.setLiftable(false)
                        appBar.background = null
                    }
                    (f as? Sortable)?.setupSortBar(sortBar) ?: sortBar.root.gone()
                }
            }, false)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun initialize() {
        if ((requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0) < 2) {
            viewModel.isFollowingGame(args.gameId, args.gameName)
        }
        if (args.updateLocal) {
            viewModel.updateLocalGame(args.gameId, args.gameName)
        }
    }

    private fun onSpinnerItemSelected(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            0 -> StreamsFragment()
            1 -> GameVideosFragment()
            else -> ClipsFragment()
        }
        return fragment.also { it.arguments = requireArguments() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("previousItem", previousItem)
        super.onSaveInstanceState(outState)
    }

    override fun scrollToTop() {
        binding.appBar.setExpanded(true, true)
        (currentFragment as? Scrollable)?.scrollToTop()
    }

    override fun onNetworkRestored() {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}