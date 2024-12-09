package com.github.andreyasadchy.xtra.util

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}