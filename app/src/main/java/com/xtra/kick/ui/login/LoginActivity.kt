package com.xtra.kick.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewClientCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xtra.kick.R
import com.xtra.kick.XtraApp
import com.xtra.kick.XtraModule
import com.xtra.kick.databinding.ActivityLoginBinding
import com.xtra.kick.util.C
import com.xtra.kick.util.KickOAuth
import com.xtra.kick.util.applyTheme
import com.xtra.kick.util.getAlertDialogBuilder
import com.xtra.kick.util.prefs
import com.xtra.kick.util.tokenPrefs
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var xtraModule: XtraModule
    private lateinit var binding: ActivityLoginBinding

    private var clientId: String? = null
    private var clientSecret: String? = null
    private var redirectUri: String? = null
    private var verifier: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        xtraModule = (application as XtraApp).xtraModule
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
        binding.progressBar.visibility = View.VISIBLE
        binding.havingTrouble.setOnClickListener {
            prefs().edit {
                putString(C.KICK_CLIENT_ID, null)
                putString(C.KICK_CLIENT_SECRET, null)
                putString(C.KICK_REDIRECT_URI, null)
                putString(C.KICK_CLIENT_SCOPES, null)
            }
            showClientSetupDialog()
        }
        val hasTwitchToken = !tokenPrefs().getString(C.TOKEN, null).isNullOrBlank()
        tokenPrefs().edit {
            putString(C.KICK_ACCESS_TOKEN, null)
            putString(C.KICK_REFRESH_TOKEN, null)
            putLong(C.KICK_TOKEN_EXPIRES_AT, 0)
            putLong(C.KICK_REFRESH_EXPIRES_AT, 0)
            if (!hasTwitchToken) {
                putString(C.USERNAME, null)
                putString(C.USER_ID, null)
            }
        }
        setupWebView()
        checkClientConfigured()
    }

    private fun checkClientConfigured() {
        val preference = prefs()
        clientId = preference.getString(C.KICK_CLIENT_ID, null)
        clientSecret = preference.getString(C.KICK_CLIENT_SECRET, null)
        redirectUri = preference.getString(C.KICK_REDIRECT_URI, null)
        if (clientId.isNullOrBlank() || redirectUri.isNullOrBlank()) {
            showClientSetupDialog()
        } else {
            startKickLogin()
        }
    }

    private fun showClientSetupDialog() {
        binding.havingTrouble.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.GONE
        val clientIdLayout = TextInputLayout(this).apply {
            hint = getString(R.string.kick_client_id)
        }
        clientIdLayout.addView(TextInputEditText(this).apply {
            setText(prefs().getString(C.KICK_CLIENT_ID, null))
            id = View.generateViewId()
        })
        val clientSecretLayout = TextInputLayout(this).apply {
            hint = getString(R.string.kick_client_secret)
        }
        clientSecretLayout.addView(TextInputEditText(this).apply {
            setText(prefs().getString(C.KICK_CLIENT_SECRET, null))
            id = View.generateViewId()
        })
        val redirectLayout = TextInputLayout(this).apply {
            hint = getString(R.string.kick_redirect_uri)
        }
        redirectLayout.addView(TextInputEditText(this).apply {
            setText(prefs().getString(C.KICK_REDIRECT_URI, null) ?: "https://xtra.kick.local/auth")
            id = View.generateViewId()
        })
        getAlertDialogBuilder()
            .setTitle(getString(R.string.log_in))
            .setMessage(getString(R.string.kick_login_description))
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(clientIdLayout)
                addView(clientSecretLayout)
                addView(redirectLayout)
            })
            .setPositiveButton(getString(R.string.next)) { _, _ ->
                val clientId = (clientIdLayout.getChildAt(0) as TextInputEditText).text?.toString()
                val clientSecret = (clientSecretLayout.getChildAt(0) as TextInputEditText).text?.toString()
                val redirectUri = (redirectLayout.getChildAt(0) as TextInputEditText).text?.toString()
                if (!clientId.isNullOrBlank() && !redirectUri.isNullOrBlank()) {
                    prefs().edit {
                        putString(C.KICK_CLIENT_ID, clientId)
                        putString(C.KICK_CLIENT_SECRET, clientSecret)
                        putString(C.KICK_REDIRECT_URI, redirectUri)
                    }
                    this@LoginActivity.clientId = clientId
                    this@LoginActivity.clientSecret = clientSecret
                    this@LoginActivity.redirectUri = redirectUri
                    startKickLogin()
                } else {
                    Toast.makeText(this@LoginActivity, R.string.invalid_url, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startKickLogin() {
        binding.progressBar.visibility = View.VISIBLE
        verifier = KickOAuth.generateVerifier()
        val challenge = verifier?.let { KickOAuth.generateCodeChallenge(it) }
        val state = KickOAuth.generateState()
        tokenPrefs().edit {
            putString(C.KICK_OAUTH_STATE, state)
            putString(C.KICK_OAUTH_VERIFIER, verifier)
        }
        val scopes = prefs().getString(
            C.KICK_CLIENT_SCOPES,
            "user:read channel:read channel:write chat:write moderation:ban moderation:chat_message:manage kicks:read streamkey:read"
        ) ?: ""
        val url = KickOAuth.buildAuthorizeUrl(clientId ?: "", redirectUri ?: "", scopes, state, challenge)
        binding.webView.loadUrl(url)
        binding.webView.visibility = View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView) {
            visibility = View.INVISIBLE
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.textZoom = 100
            webViewClient = object : WebViewClientCompat() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val redirect = redirectUri
                    if (!redirect.isNullOrBlank() && url.startsWith(redirect, ignoreCase = true)) {
                        handleOAuthCallback(url)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    binding.progressBar.visibility = View.GONE
                    if (url?.startsWith(redirectUri.orEmpty(), ignoreCase = true) == true) {
                        handleOAuthCallback(url)
                    }
                }
            }
        }
    }

    private fun handleOAuthCallback(url: String) {
        val uri = url.toUri()
        if (uri.getQueryParameter("error") != null) {
            Toast.makeText(this, R.string.kick_login_error, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val state = uri.getQueryParameter("state")
        if (state.isNullOrBlank() || state != tokenPrefs().getString(C.KICK_OAUTH_STATE, null)) {
            Toast.makeText(this, R.string.kick_login_state_mismatch, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val code = uri.getQueryParameter("code")
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val token = xtraModule.kickRepository.getOAuthToken(clientId, clientSecret, redirectUri, code.orEmpty(), verifier.orEmpty())
                tokenPrefs().edit {
                    putString(C.KICK_ACCESS_TOKEN, token.accessToken)
                    putString(C.KICK_REFRESH_TOKEN, token.refreshToken)
                    putLong(C.KICK_TOKEN_EXPIRES_AT, System.currentTimeMillis() + token.expiresIn * 1000)
                    if (token.refreshExpiresIn > 0) {
                        putLong(C.KICK_REFRESH_EXPIRES_AT, System.currentTimeMillis() + token.refreshExpiresIn * 1000)
                    }
                }
                val user = runCatching {
                    xtraModule.kickRepository.getCurrentUser(token.accessToken).data
                }.getOrNull()
                tokenPrefs().edit {
                    putString(C.USERNAME, user?.slug?.takeIf { it.isNotBlank() } ?: user?.username)
                    if (user?.id != null) {
                        putString(C.USER_ID, "user_${user.id}")
                    }
                }
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@LoginActivity, R.string.kick_login_error, Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}