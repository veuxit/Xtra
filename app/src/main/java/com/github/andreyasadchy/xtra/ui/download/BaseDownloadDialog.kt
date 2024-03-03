package com.github.andreyasadchy.xtra.ui.download

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.widget.RadioButton
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.StorageSelectionBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlin.math.max

abstract class BaseDownloadDialog : DialogFragment() {

    protected lateinit var prefs: SharedPreferences
    private lateinit var storageSelectionContainer: StorageSelectionBinding
    private lateinit var storage: List<Storage>
    protected val downloadPath: String
        get() {
            val index = if (storage.size == 1) {
                0
            } else {
                val checked = max(storageSelectionContainer.radioGroup.checkedRadioButtonId, 0)
                prefs.edit { putInt(C.DOWNLOAD_STORAGE, checked) }
                checked
            }
            return storage[index].path
        }
    protected var sharedPath: String? = null

    protected fun init(context: Context, binding: StorageSelectionBinding, downloadButton: View) {
        with(binding) {
            storageSelectionContainer = binding
            prefs = context.prefs()
            storage = DownloadUtils.getAvailableStorage(context)
            if (DownloadUtils.isExternalStorageAvailable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && this@BaseDownloadDialog is ClipDownloadDialog) {
                    val location = prefs.getInt(C.DOWNLOAD_LOCATION, 0)
                    when (location) {
                        0 -> sharedStorageLayout.visible()
                        1 -> appStorageLayout.visible()
                    }
                    (storageSpinner.editText as? MaterialAutoCompleteTextView)?.apply {
                        setSimpleItems(resources.getStringArray(R.array.spinnerStorage))
                        setOnItemClickListener { _, _, position, _ ->
                            when (position) {
                                0 -> {
                                    sharedStorageLayout.visible()
                                    appStorageLayout.gone()
                                }
                                1 -> {
                                    appStorageLayout.visible()
                                    sharedStorageLayout.gone()
                                }
                            }
                        }
                        setText(adapter.getItem(location).toString(), false)
                    }
                } else {
                    (storageSpinner.editText as? MaterialAutoCompleteTextView)?.apply {
                        setSimpleItems(resources.getStringArray(R.array.spinnerStorage))
                        setText(adapter.getItem(1).toString(), false)
                    }
                    saveTo.gone()
                    storageSpinner.gone()
                }
                if (storage.size > 1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && this@BaseDownloadDialog is ClipDownloadDialog) {
                        saveTo.visible()
                        appStorageLayout.visible()
                    }
                    for (s in storage) {
                        radioGroup.addView(RadioButton(context).apply {
                            id = s.id
                            text = s.name
                        })
                    }
                    radioGroup.check(prefs.getInt(C.DOWNLOAD_STORAGE, 0))
                }
            } else {
                noStorageDetected.visible()
                storageSpinner.gone()
                downloadButton.gone()
            }
        }
    }

    data class Storage(
            val id: Int,
            val name: String,
            val path: String)
}