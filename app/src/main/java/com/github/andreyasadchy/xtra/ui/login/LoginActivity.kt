package com.github.andreyasadchy.xtra.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivityLoginBinding
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.LoggedIn
import com.github.andreyasadchy.xtra.model.NotLoggedIn
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AuthRepository
    private val tokenPattern = Pattern.compile("token=(.+?)(?=&)")
    private val tokens = mutableListOf<String>()
    private var userId: String? = null
    private var userLogin: String? = null
    private var helixToken: String? = null
    private var gqlToken: String? = null
    private var gqlToken2: String? = null

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val helixClientId = prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi")
        val gqlClientId = prefs().getString(C.GQL_CLIENT_ID, "kimne78kx3ncx6brgo4mv6wki5h1ko")
        val gqlClientId2 = prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
        val account = Account.get(this)
        if (account !is NotLoggedIn) {
            TwitchApiHelper.checkedValidation = false
            Account.set(this, null)
            GlobalScope.launch {
                try {
                    if (!helixClientId.isNullOrBlank() && !account.helixToken.isNullOrBlank()) {
                        repository.revoke(helixClientId, account.helixToken)
                    }
                    if (!gqlClientId.isNullOrBlank() && !account.gqlToken.isNullOrBlank()) {
                        repository.revoke(gqlClientId, account.gqlToken)
                    }
                    if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank() && account.gqlToken2 != account.gqlToken) {
                        repository.revoke(gqlClientId2, account.gqlToken2)
                    }
                } catch (e: Exception) {

                }
            }
        }
        initWebView(helixClientId, gqlClientId, gqlClientId2)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(helixClientId: String?, gqlClientId: String?, gqlClientId2: String?) {
        val apiSetting = prefs().getString(C.API_LOGIN, "0")?.toInt() ?: 0
        val helixRedirect = prefs().getString(C.HELIX_REDIRECT, "https://localhost")
        val helixScopes = listOf(
            "channel:edit:commercial", // channels/commercial
            "channel:manage:broadcast", // streams/markers
            "channel:manage:moderators", // moderation/moderators
            "channel:manage:raids", // raids
            "channel:manage:vips", // channels/vips
            "channel:moderate",
            "chat:edit",
            "chat:read",
            "moderator:manage:announcements", // chat/announcements
            "moderator:manage:banned_users", // moderation/bans
            "moderator:manage:chat_messages", // moderation/chat
            "moderator:manage:chat_settings", // chat/settings
            "moderator:read:chatters", // chat/chatters
            "moderator:read:followers", // channels/followers
            "user:manage:chat_color", // chat/color
            "user:manage:whispers", // whispers
            "user:read:follows", // streams/followed, channels/followed
        )
        val helixAuthUrl = "https://id.twitch.tv/oauth2/authorize?response_type=token&client_id=${helixClientId}&redirect_uri=${helixRedirect}&scope=${URLEncoder.encode(helixScopes.joinToString(" "), Charsets.UTF_8.name())}"
        val gqlRedirect = prefs().getString(C.GQL_REDIRECT, "https://www.twitch.tv/")
        val gqlAuthUrl = "https://id.twitch.tv/oauth2/authorize?response_type=token&client_id=${gqlClientId}&redirect_uri=${gqlRedirect}&scope="
        val gqlAuthUrl2 = "https://id.twitch.tv/oauth2/authorize?response_type=token&client_id=${gqlClientId2}&redirect_uri=${gqlRedirect}&scope="
        with(binding) {
            webViewContainer.visible()
            havingTrouble.setOnClickListener {
                AlertDialog.Builder(this@LoginActivity)
                    .setMessage(getString(R.string.login_problem_solution))
                    .setPositiveButton(R.string.log_in) { _, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, helixAuthUrl.toUri())
                        if (intent.resolveActivity(packageManager) != null) {
                            webView.reload()
                            startActivity(intent)
                        } else {
                            toast(R.string.no_browser_found)
                        }
                    }
                    .setNeutralButton(R.string.to_enter_url) { _, _ ->
                        val editText = EditText(this@LoginActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                val margin = convertDpToPixels(10f)
                                setMargins(margin, 0, margin, 0)
                            }
                        }
                        val dialog = AlertDialog.Builder(this@LoginActivity)
                            .setTitle(R.string.enter_url)
                            .setView(editText)
                            .setPositiveButton(R.string.log_in) { _, _ ->
                                val text = editText.text
                                if (text.isNotEmpty()) {
                                    if (!loginIfValidUrl(text.toString(), gqlAuthUrl, gqlAuthUrl2, helixClientId, gqlClientId, gqlClientId2, 2)) {
                                        shortToast(R.string.invalid_url)
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            clearCookies()
        }
        with(binding.webView) {
            val theme = if (prefs().getBoolean(C.UI_THEME_FOLLOW_SYSTEM, false)) {
                when (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
                    Configuration.UI_MODE_NIGHT_YES -> prefs().getString(C.UI_THEME_DARK_ON, "0")!!
                    else -> prefs().getString(C.UI_THEME_DARK_OFF, "2")!!
                }
            } else {
                prefs().getString(C.THEME, "0")!!
            }
            if (theme != "2") {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    WebSettingsCompat.setForceDark(this.settings, WebSettingsCompat.FORCE_DARK_ON)
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(this.settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                }
            }
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    loginIfValidUrl(url, gqlAuthUrl, gqlAuthUrl2, helixClientId, gqlClientId, gqlClientId2, apiSetting)
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                    val errorMessage = if (errorCode == -11) {
                        getString(R.string.browser_workaround)
                    } else {
                        getString(R.string.error, "$errorCode $description")
                    }
                    val html = "<html><body><div align=\"center\">$errorMessage</div></body>"
                    loadUrl("about:blank")
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }
            loadUrl(if (apiSetting == 1) gqlAuthUrl else helixAuthUrl)
        }
    }

/*    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }*/

    private fun loginIfValidUrl(url: String, gqlAuthUrl: String, gqlAuthUrl2: String, helixClientId: String?, gqlClientId: String?, gqlClientId2: String?, apiSetting: Int): Boolean {
        with(binding) {
            val matcher = tokenPattern.matcher(url)
            return if (matcher.find()) {
                webViewContainer.gone()
                progressBar.visible()
                val token = matcher.group(1)!!
                if (token.isNotBlank() && !tokens.contains(token)) {
                    tokens.add(token)
                    lifecycleScope.launch {
                        try {
                            val response = repository.validate(if ((apiSetting == 0 && tokens.count() == 1) || (apiSetting == 2)) {
                                TwitchApiHelper.addTokenPrefixHelix(token)
                            } else {
                                TwitchApiHelper.addTokenPrefixGQL(token)
                            })
                            if (!response?.clientId.isNullOrBlank() && response?.clientId ==
                                if ((apiSetting == 0 && tokens.count() == 1) || (apiSetting == 2)) {
                                    helixClientId
                                } else {
                                    if ((apiSetting == 0 && tokens.count() == 2) || (apiSetting == 1 && tokens.count() == 1)) {
                                        gqlClientId
                                    } else {
                                        if ((apiSetting == 0 && tokens.count() == 3) || (apiSetting == 1 && tokens.count() == 2)) {
                                            gqlClientId2
                                        } else null
                                    }
                                }) {
                                userId = response?.userId
                                userLogin = response?.login
                                if ((apiSetting == 0 && tokens.count() == 1) || (apiSetting == 2)) {
                                    helixToken = token
                                } else {
                                    if ((apiSetting == 0 && tokens.count() == 2) || (apiSetting == 1 && tokens.count() == 1)) {
                                        gqlToken = token
                                    } else {
                                        if ((apiSetting == 0 && tokens.count() == 3) || (apiSetting == 1 && tokens.count() == 2)) {
                                            gqlToken2 = token
                                        }
                                    }
                                }
                                if ((apiSetting == 0 && tokens.count() == 3) || (apiSetting == 1 && tokens.count() == 2) || (apiSetting == 2)) {
                                    TwitchApiHelper.checkedValidation = true
                                    Account.set(this@LoginActivity, LoggedIn(userId, userLogin, helixToken, gqlToken, gqlToken2))
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            } else {
                                throw IOException()
                            }
                        } catch (e: Exception) {
                            toast(R.string.connection_error)
                            if ((apiSetting == 0 && tokens.count() == 3) || (apiSetting == 1 && tokens.count() == 2) || (apiSetting == 2)) {
                                finish()
                            }
                        }
                        if (apiSetting == 0 && tokens.count() == 1) {
                            webView.loadUrl(gqlAuthUrl)
                        } else {
                            if ((apiSetting == 0 && tokens.count() == 2) || (apiSetting == 1 && tokens.count() == 1)) {
                                webView.loadUrl(gqlAuthUrl2)
                            }
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }
}