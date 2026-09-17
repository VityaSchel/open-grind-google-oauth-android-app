package org.opengrind.google_oauth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGrindTrustTest {
	private val openGrind = OpenGrindTrust.PACKAGE
	private val pinnedCert = OpenGrindTrust.SIGNING_CERTS_SHA256.first()
	private val foreignCert = "01".repeat(32)

	private class InstalledPackages(
		private val signerByPackage: Map<String, String>,
	) : OpenGrindTrust.SigningCertificates {
		val lookups = mutableListOf<String>()

		override fun has(packageName: String, sha256: ByteArray): Boolean {
			lookups += packageName
			val signer = signerByPackage[packageName] ?: return false
			return signer.hexToByteArray().contentEquals(sha256)
		}

		fun trust(packageName: String?): Boolean = OpenGrindTrust.trusts(
			packageName = packageName,
			certificates = this,
		)
	}

	private fun releaseCertCheckedBySignRelease(): String {
		val script = checkNotNull(
			generateSequence(File("").absoluteFile) { it.parentFile }
				.map { dir -> File(dir, "contrib/sign-release.sh") }
				.firstOrNull(File::isFile)
		) { "contrib/sign-release.sh not found" }
		val assignment = checkNotNull(
			Regex("^RELEASE_CERT=(\\p{XDigit}{64})$", RegexOption.MULTILINE)
				.find(script.readText())
		) { "$script sets no RELEASE_CERT" }
		return assignment.groupValues[1]
	}

	@Test
	fun `Open Grind signed by any pinned signer is trusted`() {
		for (cert in OpenGrindTrust.SIGNING_CERTS_SHA256) {
			val packages = InstalledPackages(mapOf(openGrind to cert))

			assertTrue(cert, packages.trust(openGrind))
		}
	}

	@Test
	fun `the release cert that sign-release checks is pinned`() {
		val releaseCert = releaseCertCheckedBySignRelease()

		assertTrue(
			releaseCert,
			OpenGrindTrust.SIGNING_CERTS_SHA256.any { pin ->
				pin.equals(releaseCert, ignoreCase = true)
			},
		)
	}

	@Test
	fun `Open Grind signed by any other key is refused`() {
		val packages = InstalledPackages(mapOf(openGrind to foreignCert))

		assertFalse(packages.trust(openGrind))
	}

	@Test
	fun `Open Grind is refused when it is not installed`() {
		val packages = InstalledPackages(emptyMap())

		assertFalse(packages.trust(openGrind))
	}

	@Test
	fun `another package signed by a pinned key is refused unchecked`() {
		val packages = InstalledPackages(mapOf("org.example" to pinnedCert))

		assertFalse(packages.trust("org.example"))
		assertTrue(packages.lookups.isEmpty())
	}

	@Test
	fun `a launch without a calling package is refused unchecked`() {
		val packages = InstalledPackages(mapOf(openGrind to pinnedCert))

		assertFalse(packages.trust(null))
		assertTrue(packages.lookups.isEmpty())
	}

	@Test
	fun `every pinned signer is a SHA-256 digest`() {
		for (cert in OpenGrindTrust.SIGNING_CERTS_SHA256) {
			assertEquals(cert, 32, cert.hexToByteArray().size)
		}
	}
}
