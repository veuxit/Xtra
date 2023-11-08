package com.github.andreyasadchy.xtra.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.util.Pair
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivitySettingsBinding
import com.github.andreyasadchy.xtra.ui.Utils
import com.github.andreyasadchy.xtra.ui.main.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DisplayUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    var recreate = false

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.apply {
            navigationIcon = Utils.getNavigationIcon(this@SettingsActivity)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        recreate = savedInstanceState?.getBoolean(SettingsFragment.KEY_CHANGED) == true
        if (savedInstanceState == null || recreate) {
            recreate = false
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SettingsFragment.KEY_CHANGED, recreate)
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat() {

        private val viewModel: SettingsViewModel by viewModels()

        private var changed = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            changed = savedInstanceState?.getBoolean(KEY_CHANGED) == true
            if (changed) {
                requireActivity().setResult(Activity.RESULT_OK)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val activity = requireActivity()
            val changeListener = Preference.OnPreferenceChangeListener { _, _ ->
                setResult()
                true
            }

            findPreference<ListPreference>(C.UI_LANGUAGE)?.setOnPreferenceChangeListener { _, _ ->
                (activity as? SettingsActivity)?.recreate = true
                changed = true
                activity.recreate()
                true
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
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ThemeSettingsFragment())
                    .addToBackStack(null)
                    .commit()
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
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, PlayerButtonSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            findPreference<Preference>("player_menu_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, PlayerMenuSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                findPreference<ListPreference>(C.PLAYER_BACKGROUND_PLAYBACK)?.apply {
                    setEntries(R.array.backgroundPlaybackNoPipEntries)
                    setEntryValues(R.array.backgroundPlaybackNoPipValues)
                }
            }

            findPreference<Preference>("buffer_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, BufferSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            findPreference<Preference>("clear_video_positions")?.setOnPreferenceClickListener {
                viewModel.deletePositions()
                requireContext().shortToast(R.string.cleared)
                true
            }

            findPreference<Preference>("proxy_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ProxySettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            findPreference<Preference>("token_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, TokenSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }

            findPreference<Preference>("api_settings")?.setOnPreferenceClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, DragListFragment())
                    .addToBackStack(null)
                    .commit()
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

    class ThemeSettingsFragment : PreferenceFragmentCompat() {

        private var changed = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            changed = savedInstanceState?.getBoolean(SettingsFragment.KEY_CHANGED) == true
            if (changed) {
                requireActivity().setResult(Activity.RESULT_OK)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.theme_preferences, rootKey)
            val activity = requireActivity()

            findPreference<ListPreference>(C.THEME)?.setOnPreferenceChangeListener { _, _ ->
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                findPreference<SwitchPreferenceCompat>(C.UI_STATUSBAR)!!.isVisible = false
                findPreference<SwitchPreferenceCompat>(C.UI_NAVBAR)!!.isVisible = false
            } else {
                findPreference<SwitchPreferenceCompat>(C.UI_STATUSBAR)?.setOnPreferenceChangeListener { _, _ ->
                    changed = true
                    activity.recreate()
                    true
                }
                findPreference<SwitchPreferenceCompat>(C.UI_NAVBAR)?.setOnPreferenceChangeListener { _, _ ->
                    changed = true
                    activity.recreate()
                    true
                }
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            outState.putBoolean(SettingsFragment.KEY_CHANGED, changed)
            super.onSaveInstanceState(outState)
        }
    }

    class PlayerButtonSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.player_button_preferences, rootKey)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                findPreference<SwitchPreferenceCompat>(C.PLAYER_AUDIO_COMPRESSOR_BUTTON)?.isVisible = false
            }
        }
    }

    class PlayerMenuSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.player_menu_preferences, rootKey)
        }
    }

    class BufferSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.buffer_preferences, rootKey)
        }
    }

    class ProxySettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.proxy_preferences, rootKey)
        }
    }

    class TokenSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.token_preferences, rootKey)
        }
    }

    class DragListFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            var newId = 1000
            val view = LinearLayout(requireContext()).apply {
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
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
            return NestedScrollView(requireContext()).apply { addView(view) }
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
    }

    class ApiSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.api_preferences, rootKey)

            findPreference<Preference>("api_token_settings")?.setOnPreferenceClickListener {
                requireParentFragment().parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ApiTokenSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }
        }
    }

    class ApiTokenSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.api_token_preferences, rootKey)
        }
    }
}