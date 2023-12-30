package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.github.andreyasadchy.xtra.R
import com.google.android.material.color.DynamicColors

val Context.isNetworkAvailable get() = getConnectivityManager(this).let { connectivityManager ->
    val activeNetwork = connectivityManager.activeNetworkInfo ?: connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_VPN)
    activeNetwork?.isConnectedOrConnecting == true
}

private fun getConnectivityManager(context: Context) = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

fun Context.prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.convertDpToPixels(dp: Float) =  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this.resources.displayMetrics).toInt()

fun Context.convertPixelsToDp(pixels: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, pixels, this.resources.displayMetrics).toInt()

val Context.displayDensity
    get() = this.resources.displayMetrics.density

fun Activity.applyTheme(): String {
    val theme = if (prefs().getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
        when (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> prefs().getString(C.UI_THEME_DARK_ON, "0")!!
            else -> prefs().getString(C.UI_THEME_DARK_OFF, "2")!!
        }
    } else {
        prefs().getString(C.THEME, "0")!!
    }
    if (prefs().getBoolean(C.UI_THEME_ROUNDED_CORNERS, true)) {
        setTheme(when(theme) {
            "4" -> R.style.DarkTheme
            "5" -> R.style.LightTheme
            "1" -> R.style.AmoledTheme
            "2" -> R.style.LightTheme
            "3" -> R.style.BlueTheme
            else -> R.style.DarkTheme
        })
    } else {
        setTheme(when(theme) {
            "4" -> R.style.DarkThemeNoCorners
            "5" -> R.style.LightThemeNoCorners
            "1" -> R.style.AmoledThemeNoCorners
            "2" -> R.style.LightThemeNoCorners
            "3" -> R.style.BlueThemeNoCorners
            else -> R.style.DarkThemeNoCorners
        })
    }
    if (listOf("4", "5").contains(theme)) {
        DynamicColors.applyToActivityIfAvailable(this)
    }
    val isLightTheme = theme.isLightTheme
    WindowInsetsControllerCompat(window, window.decorView).run {
        isAppearanceLightStatusBars = isLightTheme
        isAppearanceLightNavigationBars = isLightTheme
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        if (prefs().getBoolean(C.UI_THEME_EDGE_TO_EDGE, true)) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            }
        } else {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> window.isNavigationBarContrastEnforced = false
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> window.navigationBarColor = Color.TRANSPARENT
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> {
                    if (!isLightTheme) {
                        window.navigationBarColor = Color.TRANSPARENT
                    }
                }
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = when (prefs().getString(C.UI_CUTOUTMODE, "0")) {
            "1" -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            "2" -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }
    return theme
}

fun Context.getActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> this.baseContext.getActivity()
        else -> null
    }
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