package org.opengrind.google_oauth

import android.content.Intent

class RequestTokenActivity : MainActivity() {

    override fun isLaunchAllowed(): Boolean = isTrustedOpenGrind(callingPackage)

    override fun onToken(token: String) {
        if (!isTrustedOpenGrind(callingPackage)) {
            setResult(MainActivity.RESULT_REFUSED)
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_TOKEN, token))
        finish()
    }

    override fun onError(error: String) {
        super.onError(error)
        setResult(RESULT_CANCELED)
        finish()
    }
}
