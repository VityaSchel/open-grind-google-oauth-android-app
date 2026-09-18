package org.opengrind.google_oauth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

open class MainActivity : ComponentActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var popupSession: GeckoSession? = null
    private var extension: WebExtension? = null
    private var pendingToken: String? = null
    private var openGrindHasTheToken = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (!isLaunchAllowed()) {
            setResult(RESULT_REFUSED)
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        geckoView = findViewById(R.id.geckoview)
        geckoView.coverUntilFirstPaint(Color.BLACK)

        runtime = GeckoRuntime.getDefault(this)
        runtime.settings.preferredColorScheme = GeckoRuntimeSettings.COLOR_SCHEME_DARK

        session = GeckoSession(privateSessionSettings()).apply {
            navigationDelegate = newSessionDelegate
            promptDelegate = popupPromptDelegate
        }
        session.open(runtime)
        geckoView.setSession(session)

        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URL, EXTENSION_ID)
            .then<WebExtension> { installed ->
                extension = installed
                bindMessageDelegate()
                runtime.webExtensionController
                    .setAllowedInPrivateBrowsing(installed!!, true)
            }
            .accept({
                session.loadUri(HELPER_URL)
            }, { e ->
                Log.e(TAG, "extension install failed", e)
            })
    }

    override fun onResume() {
        super.onResume()
        bindMessageDelegate()
    }

    private fun bindMessageDelegate() {
        extension?.setMessageDelegate(messageDelegate, NATIVE_APP)
    }

    protected open fun isLaunchAllowed(): Boolean = true

    protected fun isTrustedOpenGrind(packageName: String?): Boolean =
        OpenGrindTrust.trusts(
            packageName = packageName,
            certificates = { openGrind, sha256 ->
                packageManager.hasSigningCertificate(
                    openGrind,
                    sha256,
                    PackageManager.CERT_INPUT_SHA256,
                )
            },
        )

    override fun onDestroy() {
        if (::session.isInitialized) {
            popupSession?.close()
            session.close()
            runtime.storageController
                .clearData(StorageController.ClearFlags.ALL)
        }
        super.onDestroy()
    }

    private fun privateSessionSettings() =
        GeckoSessionSettings.Builder().usePrivateMode(true).build()

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any> {
            val msg = message as? JSONObject ?: return refuse("message is not an object")
            return when (msg.optString("type")) {
                "token" -> takeToken(msg.optString("token"))
                "error" -> {
                    onError(msg.optString("error"))
                    GeckoResult.fromValue(true)
                }
                else -> refuse("unknown message type")
            }
        }
    }

    private fun takeToken(token: String): GeckoResult<Any> =
        if (token.isEmpty()) refuse("empty token") else onToken(token)

    private fun refuse(reason: String): GeckoResult<Any> =
        GeckoResult.fromException(IllegalArgumentException(reason))

    protected open fun onToken(token: String): GeckoResult<Any> {
        pendingToken = token
        if (!handBackToOpenGrind(token)) showTokenPage(token)
        return GeckoResult.fromValue(true)
    }

    private fun showTokenPage(token: String) {
        geckoView.setSession(session)
        session.loadUri("$TOKEN_PAGE_URL#${Uri.encode(token)}")
    }

    private fun handBackToOpenGrind(token: String): Boolean {
        if (!isTrustedOpenGrind(OpenGrindTrust.PACKAGE)) return false
        val intent = Intent()
            .setClassName(OpenGrindTrust.PACKAGE, OPEN_GRIND_HANDOFF_ACTIVITY)
            .putExtra(EXTRA_TOKEN, token)
        return try {
            handBackLauncher.launch(intent)
            openGrindHasTheToken = true
            true
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "handoff unavailable", e)
            false
        } catch (e: SecurityException) {
            Log.i(TAG, "handoff refused", e)
            false
        }
    }

    private val handBackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            openGrindHasTheToken = false
            pendingToken?.let { showTokenPage(it) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (openGrindHasTheToken) finishAndRemoveTask()
    }

    protected open fun onError(error: String) {
        Log.e(TAG, "extension error: $error")
    }

    private val popupPromptDelegate = object : GeckoSession.PromptDelegate {
        override fun onPopupPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.PopupPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    private val newSessionDelegate = object : GeckoSession.NavigationDelegate {
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
            val popup = GeckoSession(privateSessionSettings())
            popupSession = popup
            popup.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onCloseRequest(session: GeckoSession) = closePopup()
            }
            geckoView.setSession(popup)
            return GeckoResult.fromValue(popup)
        }
    }

    private fun closePopup() {
        geckoView.setSession(session)
        popupSession?.close()
        popupSession = null
    }

    companion object {
        const val EXTRA_TOKEN = "org.opengrind.google_oauth.extra.TOKEN"
        const val RESULT_REFUSED = Activity.RESULT_FIRST_USER

        private const val TAG = "grindr-oauth"
        private const val HELPER_URL = "https://web.grindr.com/"
        private const val NATIVE_APP = "grindr_google_oauth"
        private const val EXTENSION_ID = "grindr-google-oauth-webextension@opengrind.org"
        private const val EXTENSION_URL = "resource://android/assets/grindr-google-oauth/"
        private const val TOKEN_PAGE_URL = EXTENSION_URL + "shared/token.html"
        private const val OPEN_GRIND_HANDOFF_ACTIVITY = "org.opengrind.TokenHandoffActivity"
    }
}
