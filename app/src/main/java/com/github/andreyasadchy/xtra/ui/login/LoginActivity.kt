package com.github.andreyasadchy.xtra.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivityLoginBinding
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.isLightTheme
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AuthRepository

    private val tokenPattern = Pattern.compile("token=(.+?)(?=&)")
    private var tokens = mutableListOf<String>()
    private var userId: String? = null
    private var userLogin: String? = null
    private var helixToken: String? = null
    private var gqlToken: String? = null
    private var deviceCode: String? = null
    private var readHeaders = false

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                rightMargin = insets.right
                bottomMargin = insets.bottom
            }
            windowInsets
        }
        val helixHeaders = TwitchApiHelper.getHelixHeaders(this)
        val helixClientId = helixHeaders[C.HEADER_CLIENT_ID]
        val helixToken = helixHeaders[C.HEADER_TOKEN]?.removePrefix("Bearer ")
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(this, true)
        val gqlClientId = gqlHeaders[C.HEADER_CLIENT_ID]
        val gqlToken = gqlHeaders[C.HEADER_TOKEN]?.removePrefix("OAuth ")
        if (!gqlToken.isNullOrBlank() || !helixToken.isNullOrBlank()) {
            TwitchApiHelper.checkedValidation = false
            tokenPrefs().edit {
                putString(C.TOKEN, null)
                putString(C.GQL_HEADERS, null)
                putLong(C.INTEGRITY_EXPIRATION, 0)
                putString(C.GQL_TOKEN2, null)
                putString(C.USER_ID, null)
                putString(C.USERNAME, null)
            }
            lifecycleScope.launch {
                if (!helixClientId.isNullOrBlank() && !helixToken.isNullOrBlank()) {
                    try {
                        repository.revoke(helixClientId, helixToken)
                    } catch (e: Exception) {

                    }
                }
                if (!gqlClientId.isNullOrBlank() && !gqlToken.isNullOrBlank()) {
                    try {
                        repository.revoke(gqlClientId, gqlToken)
                    } catch (e: Exception) {

                    }
                }
            }
        }
        if (prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp") == "kd1unb4b3q4t58fwlpcbzcbnm76a8fp") {
            prefs().edit {
                putString(C.GQL_CLIENT_ID2, "ue6666qo983tsx6so1t0vnawi233wa")
                putString(C.GQL_REDIRECT2, "https://www.twitch.tv/settings/connections")
            }
        }
        initWebView(helixClientId, prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(helixClientId: String?, gqlClientId: String?) {
        val apiSetting = prefs().getString(C.API_LOGIN, "0")?.toInt() ?: 0
        val helixRedirect = prefs().getString(C.HELIX_REDIRECT, "https://localhost")
        val helixScopes = listOf(
            "channel:edit:commercial", // channels/commercial
            "channel:manage:broadcast", // streams/markers
            "channel:manage:moderators", // moderation/moderators
            "channel:manage:raids", // raids
            "channel:manage:vips", // channels/vips
            "channel:moderate",
            "chat:edit", // irc
            "chat:read", // irc
            "moderator:manage:announcements", // chat/announcements
            "moderator:manage:banned_users", // moderation/bans
            "moderator:manage:chat_messages", // moderation/chat
            "moderator:manage:chat_settings", // chat/settings
            "moderator:read:chatters", // chat/chatters
            "moderator:read:followers", // channels/followers
            "user:manage:chat_color", // chat/color
            "user:manage:whispers", // whispers
            "user:read:chat",
            "user:read:emotes", // chat/emotes/user
            "user:read:follows", // streams/followed, channels/followed
            "user:write:chat", // chat/messages
        )
        val helixAuthUrl = "https://id.twitch.tv/oauth2/authorize?response_type=token&client_id=${helixClientId}&redirect_uri=${helixRedirect}&scope=${URLEncoder.encode(helixScopes.joinToString(" "), Charsets.UTF_8.name())}"
        val gqlRedirect = prefs().getString(C.GQL_REDIRECT2, "https://www.twitch.tv/")
        with(binding) {
            webViewContainer.visible()
            textZoom.visible()
            havingTrouble.setOnClickListener {
                this@LoginActivity.getAlertDialogBuilder()
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
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                        this@LoginActivity.getAlertDialogBuilder()
                            .setTitle(R.string.enter_url)
                            .setView(LinearLayout(this@LoginActivity).apply {
                                addView(editText)
                                val padding = convertDpToPixels(20f)
                                setPadding(padding, 0, padding, 0)
                            })
                            .setPositiveButton(R.string.log_in) { _, _ ->
                                val text = editText.text
                                if (text.isNotEmpty()) {
                                    if (!loginIfValidUrl(text.toString(), helixAuthUrl, helixClientId, gqlRedirect, gqlClientId, 2)) {
                                        shortToast(R.string.invalid_url)
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            textZoom.setOnClickListener {
                val slider = Slider(this@LoginActivity).apply {
                    value = (webView.settings.textZoom.toFloat() / 100)
                }
                this@LoginActivity.getAlertDialogBuilder()
                    .setTitle(getString(R.string.text_size))
                    .setView(LinearLayout(this@LoginActivity).apply {
                        addView(slider)
                        val padding = convertDpToPixels(10f)
                        setPadding(padding, 0, padding, 0)
                    })
                    .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                        webView.settings.textZoom = (slider.value * 100).roundToInt()
                    }
                    .setNegativeButton(getString(android.R.string.cancel), null)
                    .show()
            }
            clearCookies()
        }
        with(binding.webView) {
            @Suppress("DEPRECATION")
            if (!isLightTheme) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    WebSettingsCompat.setForceDark(this.settings, WebSettingsCompat.FORCE_DARK_ON)
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(this.settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClientCompat() {

                override fun shouldInterceptRequest(view: WebView, webViewRequest: WebResourceRequest): WebResourceResponse? {
                    if (readHeaders) {
                        val token = webViewRequest.requestHeaders.entries.firstOrNull {
                            it.key.equals(C.HEADER_TOKEN, true) && !it.value.equals("undefined", true)
                        }?.value?.removePrefix("OAuth ")
                        if (!token.isNullOrBlank()) {
                            val clientId = webViewRequest.requestHeaders.entries.firstOrNull { it.key.equals(C.HEADER_CLIENT_ID, true) }?.value
                            loginIfValidUrl("token=${token}&", "", null, null, clientId, 1)
                        }
                    }
                    return super.shouldInterceptRequest(view, webViewRequest)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!readHeaders) {
                        loginIfValidUrl(request.url.toString(), helixAuthUrl, helixClientId, gqlRedirect, gqlClientId, apiSetting)
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.SHOULD_OVERRIDE_WITH_REDIRECTS)) {
                        if (!readHeaders && url != null) {
                            loginIfValidUrl(url, helixAuthUrl, helixClientId, gqlRedirect, gqlClientId, apiSetting)
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, url)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        val errorCode = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) {
                            error.errorCode
                        } else null
                        val errorMessage = if (errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                            getString(R.string.browser_workaround)
                        } else {
                            getString(R.string.error, "${errorCode ?: ""} ${
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
                                    error.description
                                } else ""
                            }")
                        }
                        val html = "<html><body><div align=\"center\">$errorMessage</div></body>"
                        loadUrl("about:blank")
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR)) {
                        val errorMessage = if (errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                            getString(R.string.browser_workaround)
                        } else {
                            getString(R.string.error, "$errorCode $description")
                        }
                        val html = "<html><body><div align=\"center\">$errorMessage</div></body>"
                        loadUrl("about:blank")
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }
            }
            if (apiSetting == 1) {
                if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                    readHeaders = true
                    loadUrl("https://www.twitch.tv/login")
                } else getGqlAuthUrl(gqlClientId, gqlRedirect)
            } else loadUrl(helixAuthUrl)
        }
    }

    private fun getGqlAuthUrl(gqlClientId: String?, gqlRedirect: String?) {
        lifecycleScope.launch {
            try {
                val response = repository.getDeviceCode("client_id=${gqlClientId}&scopes=channel_read+chat%3Aread+user_blocks_edit+user_blocks_read+user_follows_edit+user_read".toRequestBody())
                deviceCode = response.deviceCode
                val gqlAuthUrl = "https://id.twitch.tv/oauth2/authorize?client_id=${gqlClientId}&device_code=${deviceCode}&force_verify=true&redirect_uri=${gqlRedirect}&response_type=device_grant_trigger&scope=channel_read chat:read user_blocks_edit user_blocks_read user_follows_edit user_read"
                binding.webView.loadUrl(gqlAuthUrl)
                binding.webViewContainer.visible()
                binding.textZoom.visible()
                binding.progressBar.gone()
            } catch (e: Exception) {
                if (!helixToken.isNullOrBlank()) {
                    TwitchApiHelper.checkedValidation = true
                    tokenPrefs().edit {
                        putString(C.TOKEN, helixToken)
                        putString(C.USER_ID, userId)
                        putString(C.USERNAME, userLogin)
                    }
                }
                setResult(RESULT_OK)
                finish()
            }
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

    private fun loginIfValidUrl(url: String, helixAuthUrl: String, helixClientId: String?, gqlRedirect: String?, gqlClientId: String?, apiSetting: Int): Boolean {
        with(binding) {
            return if (((apiSetting == 0 && tokens.count() == 1) || apiSetting == 1) && url == gqlRedirect && !prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                lifecycleScope.launch {
                    try {
                        val response = repository.getToken("client_id=${gqlClientId}&device_code=${deviceCode}&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code".toRequestBody())
                        loginIfValidUrl("token=${response.token}&", helixAuthUrl, helixClientId, gqlRedirect, gqlClientId, apiSetting)
                    } catch (e: Exception) {
                        if (!helixToken.isNullOrBlank()) {
                            TwitchApiHelper.checkedValidation = true
                            tokenPrefs().edit {
                                putString(C.TOKEN, helixToken)
                                putString(C.USER_ID, userId)
                                putString(C.USERNAME, userLogin)
                            }
                        }
                        setResult(RESULT_OK)
                        finish()
                    }
                }
                true
            } else {
                val matcher = tokenPattern.matcher(url)
                if (matcher.find()) {
                    webViewContainer.gone()
                    textZoom.gone()
                    progressBar.visible()
                    val token = matcher.group(1)
                    if (!token.isNullOrBlank() && !tokens.contains(token)) {
                        tokens.add(token)
                        lifecycleScope.launch {
                            try {
                                val api = when {
                                    (apiSetting == 0 && tokens.count() == 1) || (apiSetting == 2) -> C.HELIX
                                    else -> C.GQL
                                }
                                val response = repository.validate(
                                    when (api) {
                                        C.HELIX -> TwitchApiHelper.addTokenPrefixHelix(token)
                                        else -> TwitchApiHelper.addTokenPrefixGQL(token)
                                    }
                                )
                                if (response.clientId.isNotBlank() &&
                                    response.clientId == when (api) {
                                        C.HELIX -> helixClientId
                                        else -> gqlClientId
                                    }
                                ) {
                                    response.userId?.let { userId = it }
                                    response.login?.let { userLogin = it }
                                    when (api) {
                                        C.HELIX -> helixToken = token
                                        C.GQL -> gqlToken = token
                                    }
                                    if ((apiSetting == 0 && tokens.count() == 2) || (apiSetting == 1) || (apiSetting == 2)) {
                                        TwitchApiHelper.checkedValidation = true
                                        tokenPrefs().edit {
                                            if (!gqlToken.isNullOrBlank()) {
                                                if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                                                    putLong(C.INTEGRITY_EXPIRATION, 0)
                                                    putString(C.GQL_HEADERS, JSONObject(mapOf(
                                                        C.HEADER_CLIENT_ID to gqlClientId,
                                                        C.HEADER_TOKEN to "OAuth $gqlToken"
                                                    )).toString())
                                                } else {
                                                    putString(C.GQL_TOKEN2, gqlToken)
                                                }
                                            }
                                            if (!helixToken.isNullOrBlank()) {
                                                putString(C.TOKEN, helixToken)
                                            }
                                            putString(C.USER_ID, userId)
                                            putString(C.USERNAME, userLogin)
                                        }
                                        setResult(RESULT_OK)
                                        finish()
                                    }
                                } else {
                                    throw IOException()
                                }
                            } catch (e: Exception) {
                                toast(R.string.connection_error)
                                if ((apiSetting == 0 && tokens.count() == 2) || (apiSetting == 1) || (apiSetting == 2)) {
                                    clearCookies()
                                    tokens = mutableListOf()
                                    userId = null
                                    userLogin = null
                                    helixToken = null
                                    gqlToken = null
                                    webViewContainer.visible()
                                    textZoom.visible()
                                    progressBar.gone()
                                    if ((prefs().getString(C.API_LOGIN, "0")?.toInt() ?: 0) == 1) {
                                        if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                                            readHeaders = true
                                            webView.loadUrl("https://www.twitch.tv/login")
                                        } else getGqlAuthUrl(gqlClientId, gqlRedirect)
                                    } else webView.loadUrl(helixAuthUrl)
                                }
                            }
                            if (apiSetting == 0 && tokens.count() == 1) {
                                if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                                    readHeaders = true
                                    webView.loadUrl("https://www.twitch.tv/login")
                                } else getGqlAuthUrl(gqlClientId, gqlRedirect)
                            }
                        }
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onDestroy() {
        binding.webView.loadUrl("about:blank")
        super.onDestroy()
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
    }
}