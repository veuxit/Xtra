package com.github.andreyasadchy.xtra.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import java.io.File

object DownloadUtils {

    val isExternalStorageAvailable: Boolean
        get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !activity.prefs().getBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, false)) {
            activity.prefs().edit { putBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, true) }
            activity.getAlertDialogBuilder()
                .setMessage(R.string.notification_permission_message)
                .setTitle(R.string.notification_permission_title)
                .setPositiveButton(android.R.string.ok) { _, _ -> ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    fun getAvailableStorage(context: Context): List<Storage> {
        val storage = context.getExternalFilesDirs(".downloads")
        val list = mutableListOf<Storage>()
        for (i in storage.indices) {
            val storagePath = storage[i]?.absolutePath ?: continue
            val name = if (i == 0) {
                context.getString(R.string.internal_storage)
            } else {
                val endRootIndex = storagePath.indexOf("/Android/data")
                if (endRootIndex < 0) continue
                val startRootIndex = storagePath.lastIndexOf(File.separatorChar, endRootIndex - 1)
                storagePath.substring(startRootIndex + 1, endRootIndex)
            }
            list.add(Storage(i, name, storagePath))
        }
        return list
    }

    data class Storage(
            val id: Int,
            val name: String,
            val path: String)
}
