package com.github.andreyasadchy.xtra.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.offline.Downloadable
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.offline.Request
import com.github.andreyasadchy.xtra.ui.download.BaseDownloadDialog.Storage
import com.github.andreyasadchy.xtra.ui.download.DownloadService
import com.github.andreyasadchy.xtra.ui.download.DownloadService.Companion.KEY_REQUEST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DownloadUtils {

    val isExternalStorageAvailable: Boolean
        get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    fun download(context: Context, request: Request) {
        val intent = Intent(context, DownloadService::class.java)
                .putExtra(KEY_REQUEST, request)
        context.startService(intent)
        DownloadService.activeRequests.add(request.offlineVideoId)
    }

    suspend fun prepareDownload(context: Context, downloadable: Downloadable, url: String, path: String, duration: Long? = null, startPosition: Long? = null, segmentFrom: Int? = null, segmentTo: Int? = null, downloadPath: String? = null, fromTime: Long? = null, toTime: Long? = null, quality: String? = null): OfflineVideo {
        return with(downloadable) {
            val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let {
                savePng(context, thumbnail, "thumbnails", it)
            }
            val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let {
                savePng(context, channelLogo, "profile_pics", it)
            }
            OfflineVideo(
                url = path,
                sourceUrl = url,
                sourceStartPosition = startPosition,
                name = title,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = channelName,
                channelLogo = downloadedLogo,
                thumbnail = downloadedThumbnail,
                gameId = gameId,
                gameSlug = gameSlug,
                gameName = gameName,
                duration = duration,
                uploadDate = uploadDate?.let { TwitchApiHelper.parseIso8601Date(it) },
                downloadDate = System.currentTimeMillis(),
                progress = 0,
                maxProgress = if (segmentTo != null && segmentFrom != null) segmentTo - segmentFrom + 1 else 100,
                downloadPath = downloadPath,
                fromTime = fromTime,
                toTime = toTime,
                type = type,
                videoId = id,
                quality = if (quality?.contains("Audio", true) != true) quality else "audio"
            )
        }
    }

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

    fun hasStoragePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return true
        } else {
            fun requestPermissions() {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            }

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                activity.getAlertDialogBuilder()
                    .setMessage(R.string.storage_permission_message)
                    .setTitle(R.string.storage_permission_title)
                    .setPositiveButton(android.R.string.ok) { _, _ -> requestPermissions() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> activity.toast(R.string.permission_denied) }
                    .show()
            } else {
                requestPermissions()
            }
            return false
        }
    }

    fun getAvailableStorage(context: Context): List<Storage> {
        val storage = ContextCompat.getExternalFilesDirs(context, ".downloads")
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

    suspend fun savePng(context: Context, url: String?, folder: String, fileName: String): String {
        withContext(Dispatchers.IO) {
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .into(object: CustomTarget<Bitmap>() {
                        override fun onLoadCleared(placeholder: Drawable?) {}

                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            File(context.filesDir, folder).mkdir()
                            FileOutputStream(context.filesDir.path + File.separator + folder + File.separator + "$fileName.png").use {
                                resource.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                    })
            } catch (e: Exception) {

            }
        }
        return File(context.filesDir.path + File.separator + folder + File.separator + "$fileName.png").absolutePath
    }
}
