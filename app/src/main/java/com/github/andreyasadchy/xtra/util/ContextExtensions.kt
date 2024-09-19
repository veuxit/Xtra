package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.use
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.github.andreyasadchy.xtra.R
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder

val Context.isNetworkAvailable get() = getConnectivityManager(this).let { connectivityManager ->
    val activeNetwork = connectivityManager.activeNetworkInfo ?: connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_VPN)
    activeNetwork?.isConnectedOrConnecting == true
}

private fun getConnectivityManager(context: Context) = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

fun Context.prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.tokenPrefs(): SharedPreferences = getSharedPreferences("prefs2", Context.MODE_PRIVATE)

fun Context.convertDpToPixels(dp: Float) =  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this.resources.displayMetrics).toInt()

fun Context.convertPixelsToDp(pixels: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, pixels, this.resources.displayMetrics).toInt()

val Context.displayDensity
    get() = this.resources.displayMetrics.density

fun Activity.applyTheme() {
    val theme = if (prefs().getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
        when (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> prefs().getString(C.UI_THEME_DARK_ON, "0")!!
            else -> prefs().getString(C.UI_THEME_DARK_OFF, "2")!!
        }
    } else {
        prefs().getString(C.THEME, "0")!!
    }
    if (prefs().getBoolean(C.UI_THEME_MATERIAL3, true)) {
        if (prefs().getBoolean(C.UI_THEME_ROUNDED_CORNERS, true)) {
            setTheme(when(theme) {
                "4" -> R.style.DarkTheme
                "6" -> R.style.AmoledTheme
                "5" -> R.style.LightTheme
                "1" -> R.style.AmoledTheme
                "2" -> R.style.LightTheme
                "3" -> R.style.BlueTheme
                else -> R.style.DarkTheme
            })
        } else {
            setTheme(when(theme) {
                "4" -> R.style.DarkThemeNoCorners
                "6" -> R.style.AmoledThemeNoCorners
                "5" -> R.style.LightThemeNoCorners
                "1" -> R.style.AmoledThemeNoCorners
                "2" -> R.style.LightThemeNoCorners
                "3" -> R.style.BlueThemeNoCorners
                else -> R.style.DarkThemeNoCorners
            })
        }
        if (listOf("4", "6", "5").contains(theme)) {
            DynamicColors.applyToActivityIfAvailable(this,
                DynamicColorsOptions.Builder().apply {
                    setThemeOverlay(when(theme) {
                        "6" -> R.style.AmoledDynamicOverlay
                        "5" -> R.style.LightDynamicOverlay
                        else -> R.style.DarkDynamicOverlay
                    })
                }.build()
            )
        }
    } else {
        setTheme(when(theme) {
            "4" -> R.style.AppCompatDarkTheme
            "6" -> R.style.AppCompatAmoledTheme
            "5" -> R.style.AppCompatLightTheme
            "1" -> R.style.AppCompatAmoledTheme
            "2" -> R.style.AppCompatLightTheme
            "3" -> R.style.AppCompatBlueTheme
            else -> R.style.AppCompatDarkTheme
        })
    }
    val isLightTheme = this.isLightTheme
    WindowInsetsControllerCompat(window, window.decorView).run {
        isAppearanceLightStatusBars = isLightTheme
        isAppearanceLightNavigationBars = isLightTheme
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }
}

fun Context.getAlertDialogBuilder(): AlertDialog.Builder {
    return if (prefs().getBoolean(C.UI_THEME_MATERIAL3, true)) {
        MaterialAlertDialogBuilder(this)
    } else {
        AlertDialog.Builder(this)
    }
}

fun Context.getActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> this.baseContext.getActivity()
        else -> null
    }
}

val Context.isLightTheme
    get() = obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.isLightTheme)).use {
        it.getBoolean(0, false)
    }

val Context.isInPortraitOrientation
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

val Context.isInLandscapeOrientation
    get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

val Context.isActivityResumed
    get() = this !is Activity || !((Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN && isDestroyed) || isFinishing)

fun Context.toast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}

fun Context.shortToast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

fun Context.toast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun Context.shortToast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}