package com.github.andreyasadchy.xtra.ui.settings

import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

abstract class MaterialPreferenceFragment : PreferenceFragmentCompat() {

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference -> showPreferenceDialog(preference, MaterialListPreference())
            is EditTextPreference -> showPreferenceDialog(preference, MaterialEditTextPreference())
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showPreferenceDialog(preference: Preference, fragment: DialogFragment) {
        fragment.arguments = bundleOf("key" to preference.key)
        fragment.setTargetFragment(this, 0)
        fragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    }
}