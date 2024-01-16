package com.github.andreyasadchy.xtra.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.content.res.use
import androidx.core.os.LocaleListCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivitySettingsBinding
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DisplayUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.google.android.material.appbar.AppBarLayout
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            binding.appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            binding.navHostFragment.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }
        val navController = (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment).navController
        val appBarConfiguration = AppBarConfiguration(setOf(), fallbackOnNavigateUpListener = {
            onBackPressedDispatcher.onBackPressed()
            true
        })
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
    }

    @AndroidEntryPoint
    class SettingsFragment : MaterialPreferenceFragment() {

        private val viewModel: SettingsViewModel by viewModels()

        private var changed = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            changed = savedInstanceState?.getBoolean(KEY_CHANGED) == true
            if (changed) {
                requireActivity().setResult(Activity.RESULT_OK)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val activity = requireActivity()
            val changeListener = Preference.OnPreferenceChangeListener { _, _ ->
                setResult()
                true
            }

            findPreference<ListPreference>(C.UI_LANGUAGE)?.apply {
                val lang = AppCompatDelegate.getApplicationLocales()
                if (lang.isEmpty) {
                    setValueIndex(findIndexOfValue("auto"))
                } else {
                    try {
                        setValueIndex(findIndexOfValue(lang.toLanguageTags()))
                    } catch (e: Exception) {
                        try {
                            setValueIndex(findIndexOfValue(
                                lang.toLanguageTags().substringBefore("-").let {
                                    when (it) {
                                        "id" -> "in"
                                        "pt" -> "pt-BR"
                                        "zh" -> "zh-TW"
                                        else -> it
                                    }
                                }
                            ))
                        } catch (e: Exception) {
                            setValueIndex(findIndexOfValue("en"))
                        }
                    }
                }
                setOnPreferenceChangeListener { _, value ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(
                        if (value.toString() == "auto") {
                            null
                        } else {
                            value.toString()
                        }
                    ))
                    true
                }
            }

            findPreference<ListPreference>(C.UI_CUTOUTMODE)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setOnPreferenceChangeListener { _, _ ->
                        changed = true
                        activity.recreate()
                        true
                    }
                } else {
                    isVisible = false
                }
            }

            findPreference<Preference>("theme_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToThemeSettingsFragment())
                true
            }

            findPreference<SwitchPreferenceCompat>(C.UI_ROUNDUSERIMAGE)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_TRUNCATEVIEWCOUNT)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_UPTIME)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_TAGS)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_BROADCASTERSCOUNT)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_BOOKMARK_TIME_LEFT)?.onPreferenceChangeListener = changeListener
            findPreference<SwitchPreferenceCompat>(C.UI_SCROLLTOP)?.onPreferenceChangeListener = changeListener
            findPreference<ListPreference>(C.PORTRAIT_COLUMN_COUNT)?.onPreferenceChangeListener = changeListener
            findPreference<ListPreference>(C.LANDSCAPE_COLUMN_COUNT)?.onPreferenceChangeListener = changeListener
            findPreference<ListPreference>(C.COMPACT_STREAMS)?.onPreferenceChangeListener = changeListener

            findPreference<SeekBarPreference>("chatWidth")?.apply {
                summary = context.getString(R.string.pixels, activity.prefs().getInt(C.LANDSCAPE_CHAT_WIDTH, 30))
                setOnPreferenceChangeListener { _, newValue ->
                    setResult()
                    val chatWidth = DisplayUtils.calculateLandscapeWidthByPercent(activity, newValue as Int)
                    summary = context.getString(R.string.pixels, chatWidth)
                    activity.prefs().edit { putInt(C.LANDSCAPE_CHAT_WIDTH, chatWidth) }
                    true
                }
            }

            findPreference<Preference>("player_button_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToPlayerButtonSettingsFragment())
                true
            }

            findPreference<Preference>("player_menu_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToPlayerMenuSettingsFragment())
                true
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                findPreference<ListPreference>(C.PLAYER_BACKGROUND_PLAYBACK)?.apply {
                    setEntries(R.array.backgroundPlaybackNoPipEntries)
                    setEntryValues(R.array.backgroundPlaybackNoPipValues)
                }
            }

            findPreference<Preference>("buffer_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToBufferSettingsFragment())
                true
            }

            findPreference<Preference>("clear_video_positions")?.setOnPreferenceClickListener {
                viewModel.deletePositions()
                requireContext().shortToast(R.string.cleared)
                true
            }

            findPreference<Preference>("proxy_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToProxySettingsFragment())
                true
            }

            findPreference<Preference>("token_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToTokenSettingsFragment())
                true
            }

            findPreference<Preference>("api_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$SettingsFragmentDirections`.actionSettingsFragmentToDragListFragment())
                true
            }

            findPreference<Preference>("admin_settings")?.setOnPreferenceClickListener {
                startActivity(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")))
                true
            }

            findPreference<Preference>("get_integrity_token")?.setOnPreferenceClickListener {
                IntegrityDialog.show(childFragmentManager)
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                findPreference<SwitchPreferenceCompat>(C.DEBUG_WORKMANAGER_DOWNLOADS)?.isVisible = false
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            outState.putBoolean(KEY_CHANGED, changed)
            super.onSaveInstanceState(outState)
        }

        private fun setResult() {
            if (!changed) {
                changed = true
                requireActivity().setResult(Activity.RESULT_OK)
            }
        }

        companion object {
            const val KEY_CHANGED = "changed"
        }
    }

    class ThemeSettingsFragment : MaterialPreferenceFragment() {

        private var changed = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            changed = savedInstanceState?.getBoolean(SettingsFragment.KEY_CHANGED) == true
            if (changed) {
                requireActivity().setResult(Activity.RESULT_OK)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.theme_preferences, rootKey)
            val activity = requireActivity()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                findPreference<ListPreference>(C.THEME)?.apply {
                    setEntries(R.array.themeNoDynamicEntries)
                    setEntryValues(R.array.themeNoDynamicValues)
                }
                findPreference<ListPreference>(C.UI_THEME_DARK_ON)?.apply {
                    setEntries(R.array.themeNoDynamicEntries)
                    setEntryValues(R.array.themeNoDynamicValues)
                }
                findPreference<ListPreference>(C.UI_THEME_DARK_OFF)?.apply {
                    setEntries(R.array.themeNoDynamicEntries)
                    setEntryValues(R.array.themeNoDynamicValues)
                }
            }

            findPreference<ListPreference>(C.THEME)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_ROUNDED_CORNERS)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_FOLLOW_SYSTEM)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<ListPreference>(C.UI_THEME_DARK_ON)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<ListPreference>(C.UI_THEME_DARK_OFF)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_EDGE_TO_EDGE)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_APPBAR_LIFT)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_BOTTOM_NAV_COLOR)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
            findPreference<SwitchPreferenceCompat>(C.UI_THEME_MATERIAL3)?.setOnPreferenceChangeListener { _, _ ->
                changed = true
                activity.recreate()
                true
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            outState.putBoolean(SettingsFragment.KEY_CHANGED, changed)
            super.onSaveInstanceState(outState)
        }
    }

    class PlayerButtonSettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.player_button_preferences, rootKey)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                findPreference<SwitchPreferenceCompat>(C.PLAYER_AUDIO_COMPRESSOR_BUTTON)?.isVisible = false
            }
        }
    }

    class PlayerMenuSettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.player_menu_preferences, rootKey)
        }
    }

    class BufferSettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.buffer_preferences, rootKey)
        }
    }

    class ProxySettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.proxy_preferences, rootKey)
        }
    }

    class TokenSettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.token_preferences, rootKey)
        }
    }

    class DragListFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            var newId = 1000
            val view = LinearLayout(requireContext()).apply {
                id = R.id.layout
                orientation = LinearLayout.VERTICAL
                addView(FrameLayout(requireContext()).apply {
                    id = newId
                })
            }
            childFragmentManager.beginTransaction().replace(newId, ApiSettingsFragment()).commit()
            mapOf(
                getString(R.string.games) to Pair(C.API_PREF_GAMES, TwitchApiHelper.gamesApiDefaults),
                getString(R.string.streams) to Pair(C.API_PREF_STREAMS, TwitchApiHelper.streamsApiDefaults),
                getString(R.string.game_streams) to Pair(C.API_PREF_GAME_STREAMS, TwitchApiHelper.gameStreamsApiDefaults),
                getString(R.string.game_videos) to Pair(C.API_PREF_GAME_VIDEOS, TwitchApiHelper.gameVideosApiDefaults),
                getString(R.string.game_clips) to Pair(C.API_PREF_GAME_CLIPS, TwitchApiHelper.gameClipsApiDefaults),
                getString(R.string.channel_videos) to Pair(C.API_PREF_CHANNEL_VIDEOS, TwitchApiHelper.channelVideosApiDefaults),
                getString(R.string.channel_clips) to Pair(C.API_PREF_CHANNEL_CLIPS, TwitchApiHelper.channelClipsApiDefaults),
                getString(R.string.search_videos) to Pair(C.API_PREF_SEARCH_VIDEOS, TwitchApiHelper.searchVideosApiDefaults),
                getString(R.string.search_streams) to Pair(C.API_PREF_SEARCH_STREAMS, TwitchApiHelper.searchStreamsApiDefaults),
                getString(R.string.search_channels) to Pair(C.API_PREF_SEARCH_CHANNEL, TwitchApiHelper.searchChannelsApiDefaults),
                getString(R.string.search_games) to Pair(C.API_PREF_SEARCH_GAMES, TwitchApiHelper.searchGamesApiDefaults),
                getString(R.string.followed_streams) to Pair(C.API_PREF_FOLLOWED_STREAMS, TwitchApiHelper.followedStreamsApiDefaults),
                getString(R.string.followed_videos) to Pair(C.API_PREF_FOLLOWED_VIDEOS, TwitchApiHelper.followedVideosApiDefaults),
                getString(R.string.followed_channels) to Pair(C.API_PREF_FOLLOWED_CHANNELS, TwitchApiHelper.followedChannelsApiDefaults),
                getString(R.string.followed_games) to Pair(C.API_PREF_FOLLOWED_GAMES, TwitchApiHelper.followedGamesApiDefaults),
            ).forEach { entry ->
                newId++
                view.addView(TextView(requireContext()).apply {
                    text = entry.key
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(context.convertDpToPixels(10F), context.convertDpToPixels(3F), 0, context.convertDpToPixels(3F))
                    }
                    context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.textAppearanceTitleMedium)).use {
                        TextViewCompat.setTextAppearance(this, it.getResourceId(0, 0))
                    }
                })
                val list = TwitchApiHelper.listFromPrefs(requireActivity().prefs().getString(entry.value.first, ""), entry.value.second)
                view.addView((inflater.inflate(R.layout.drag_list_layout, container, false) as DragListView).apply {
                    id = newId
                    setLayoutManager(LinearLayoutManager(context))
                    setAdapter(DragListAdapter(list), true)
                    setCanDragHorizontally(false)
                    setCanDragVertically(true)
                    setDragListListener(object : DragListView.DragListListenerAdapter() {
                        override fun onItemDragStarted(position: Int) {}

                        override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                            if (fromPosition != toPosition) {
                                var str = ""
                                adapter.itemList.forEachIndexed { index, item ->
                                    str = "$str${index.toLong()}:${(item as Pair<Long, String>).second},"
                                }
                                requireActivity().prefs().edit { putString(entry.value.first, str) }
                            }
                        }
                    })
                })
            }
            return NestedScrollView(requireContext()).apply {
                id = R.id.scrollView
                addView(view)
            }
        }

        class DragListAdapter(list: ArrayList<Pair<Long?, String?>?>?) : DragItemAdapter<Pair<Long?, String?>?, DragListAdapter.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.drag_list_item, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                holder.mText.text = mItemList[position]!!.second!!
            }

            override fun getUniqueItemId(position: Int): Long = mItemList[position]!!.first!!

            class ViewHolder(itemView: View) : DragItemAdapter.ViewHolder(itemView, R.id.image, false) {
                val mText: TextView = itemView.findViewById(R.id.text)
            }

            init {
                itemList = list
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.findViewById<LinearLayout>(R.id.layout)?.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    view.findViewById<NestedScrollView>(R.id.scrollView)?.let {
                        appBar.setLiftOnScrollTargetView(it)
                        it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            appBar.isLifted = it.canScrollVertically(-1)
                        }
                    }
                } else {
                    appBar.setLiftable(false)
                    appBar.background = null
                }
            }
        }
    }

    class ApiSettingsFragment : MaterialPreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.api_preferences, rootKey)

            findPreference<Preference>("api_token_settings")?.setOnPreferenceClickListener {
                requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(true)
                findNavController().navigate(`SettingsActivity$DragListFragmentDirections`.actionDragListFragmentToApiTokenSettingsFragment())
                true
            }
        }
    }

    class ApiTokenSettingsFragment : MaterialPreferenceFragment() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                listView.updatePadding(bottom = insets.bottom)
                WindowInsetsCompat.CONSUMED
            }
            requireActivity().findViewById<AppBarLayout>(R.id.appBar)?.let { appBar ->
                if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                    listView.let {
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
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.api_token_preferences, rootKey)
        }
    }
}