package org.opengrind.google_oauth

import android.content.Intent
import org.mozilla.geckoview.GeckoResult

class RequestTokenActivity : MainActivity() {

    override fun isLaunchAllowed(): Boolean = isTrustedOpenGrind(callingPackage)

    override fun onToken(token: String): GeckoResult<Any> {
        if (!isTrustedOpenGrind(callingPackage)) {
            setResult(MainActivity.RESULT_REFUSED)
            finish()
            return GeckoResult.fromException(SecurityException("caller is not trusted"))
        }
        setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_TOKEN, token))
        finish()
        return GeckoResult.fromValue(true)
    }
}
