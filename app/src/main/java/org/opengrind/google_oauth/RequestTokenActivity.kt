package org.opengrind.google_oauth

import android.content.Intent
import android.content.pm.PackageManager

class RequestTokenActivity : MainActivity() {

    override fun onToken(token: String) {
        if (!isTrustedCaller()) {
            setResult(RESULT_CANCELED)
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

    private fun isTrustedCaller(): Boolean {
        val caller = callingPackage ?: return false
        if (caller != TRUSTED_CALLER_PACKAGE) return false
        return packageManager.checkSignatures(caller, packageName) ==
            PackageManager.SIGNATURE_MATCH
    }

    private companion object {
        const val TRUSTED_CALLER_PACKAGE = "org.opengrind"
    }
}
