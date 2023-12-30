package com.github.andreyasadchy.xtra.ui.saved

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentMediaPagerBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.NotLoggedIn
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.nullIfEmpty
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.reduceDragSensitivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SavedPagerFragment : Fragment(), Scrollable, FragmentHost {

    private var _binding: FragmentMediaPagerBinding? = null
    private val binding get() = _binding!!
    private var firstLaunch = true

    override val currentFragment: Fragment?
        get() = childFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstLaunch = savedInstanceState == null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val activity = requireActivity() as MainActivity
            val account = Account.get(activity)
            val navController = findNavController()
            val appBarConfiguration = AppBarConfiguration(setOf(R.id.rootGamesFragment, R.id.rootTopFragment, R.id.followPagerFragment, R.id.followMediaFragment, R.id.savedPagerFragment, R.id.savedMediaFragment))
            toolbar.setupWithNavController(navController, appBarConfiguration)
            toolbar.menu.findItem(R.id.login).title = if (account !is NotLoggedIn) getString(R.string.log_out) else getString(R.string.log_in)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search -> {
                        findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
                        true
                    }
                    R.id.settings -> {
                        activity.startActivityFromFragment(this@SavedPagerFragment, Intent(activity, SettingsActivity::class.java), 3)
                        true
                    }
                    R.id.login -> {
                        if (account is NotLoggedIn) {
                            activity.startActivityForResult(Intent(activity, LoginActivity::class.java), 1)
                        } else {
                            MaterialAlertDialogBuilder(activity).apply {
                                setTitle(getString(R.string.logout_title))
                                account.login?.nullIfEmpty()?.let { user -> setMessage(getString(R.string.logout_msg, user)) }
                                setNegativeButton(getString(R.string.no), null)
                                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.startActivityForResult(Intent(activity, LoginActivity::class.java), 2) }
                            }.show()
                        }
                        true
                    }
                    else -> false
                }
            }
            val adapter = SavedPagerAdapter(this@SavedPagerFragment)
            viewPager.adapter = adapter
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewPager.doOnLayout {
                        childFragmentManager.findFragmentByTag("f${position}")?.let { fragment ->
                            if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                                fragment.view?.findViewById<RecyclerView>(R.id.recyclerView)?.let {
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
                            (fragment as? Sortable)?.setupSortBar(sortBar) ?: sortBar.root.gone()
                        }
                    }
                }
            })
            if (firstLaunch) {
                viewPager.setCurrentItem(requireContext().prefs().getString(C.UI_SAVED_DEFAULT_PAGE, "0")?.toIntOrNull() ?: 0, false)
                firstLaunch = false
            }
            viewPager.offscreenPageLimit = adapter.itemCount
            viewPager.reduceDragSensitivity()
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.bookmarks)
                    else -> getString(R.string.downloads)
                }
            }.attach()
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3 && resultCode == Activity.RESULT_OK) {
            requireActivity().recreate()
        }
    }

    override fun scrollToTop() {
        binding.appBar.setExpanded(true, true)
        (currentFragment as? Scrollable)?.scrollToTop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
