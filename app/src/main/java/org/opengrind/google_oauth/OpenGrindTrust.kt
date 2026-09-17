package org.opengrind.google_oauth

object OpenGrindTrust {
	const val PACKAGE = "org.opengrind"

	val SIGNING_CERTS_SHA256 = setOf(
		"2805FDD8F0BADB9424D3244C5E5B3473CEF5B8798EC1117382E89EDA45C3658C",
		"90F4BB85C1F2FA2A7810A1076A60D20E791720C0EE88FDB88F9E4BA32CA8702E",
	)

	fun interface SigningCertificates {
		fun has(packageName: String, sha256: ByteArray): Boolean
	}

	fun trusts(
		packageName: String?,
		certificates: SigningCertificates,
	): Boolean = packageName == PACKAGE &&
		SIGNING_CERTS_SHA256.any { cert ->
			certificates.has(
				packageName = PACKAGE,
				sha256 = cert.hexToByteArray(),
			)
		}
}
