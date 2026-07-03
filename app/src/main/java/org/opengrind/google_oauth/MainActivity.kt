package org.opengrind.google_oauth

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

class MainActivity : ComponentActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var popupSession: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
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

        session = GeckoSession().apply {
            navigationDelegate = newSessionDelegate
            promptDelegate = popupPromptDelegate
        }
        session.open(runtime)
        geckoView.setSession(session)

        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URL, EXTENSION_ID)
            .accept({ extension ->
                extension?.setMessageDelegate(messageDelegate, NATIVE_APP)
                session.loadUri(HELPER_URL)
            }, { e ->
                Log.e(TAG, "extension install failed", e)
            })
    }

    override fun onDestroy() {
        popupSession?.close()
        session.close()
        super.onDestroy()
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            (message as? JSONObject)?.let { msg ->
                when (msg.optString("type")) {
                    "token" -> onToken(msg.optString("token"))
                    "error" -> onError(msg.optString("error"))
                }
            }
            return null
        }
    }

    private fun onToken(token: String) {
        if (callingActivity != null) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_TOKEN, token))
            finish()
        } else {
            geckoView.setSession(session)
            session.loadUri("$TOKEN_PAGE_URL#${Uri.encode(token)}")
        }
    }

    private fun onError(error: String) {
        Log.e(TAG, "extension error: $error")
        if (callingActivity != null) {
            setResult(RESULT_CANCELED)
            finish()
        }
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
            val popup = GeckoSession()
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

        private const val TAG = "grindr-oauth"
        private const val HELPER_URL = "https://web.grindr.com/"
        private const val NATIVE_APP = "grindr_google_oauth"
        private const val EXTENSION_ID = "grindr-google-oauth-webextension@opengrind.org"
        private const val EXTENSION_URL = "resource://android/assets/grindr-google-oauth/"
        private const val TOKEN_PAGE_URL = EXTENSION_URL + "shared/token.html"
    }
}
