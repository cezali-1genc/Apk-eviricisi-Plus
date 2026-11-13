package com.example.apktranslatorpro.core.signing

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.example.apktranslatorpro.core.security.SecurityGuards
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

data class SigningVerificationReport(
    val isVerified: Boolean,
    val v1Verified: Boolean,
    val v2Verified: Boolean,
    val v3Verified: Boolean,
    val signerCertSha256: String,
    val errors: List<String>,
    val warnings: List<String>
)

class ApkSignerEngine(private val keystoreFile: File) {

    constructor(context: Context) : this(
        File(File(context.filesDir, "keystore").apply { mkdirs() }, "apk_translator_signer.p12")
    )

    companion object {
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val KEY_ALIAS = "apk_translator_key"
        private val KEYSTORE_PASSWORD = "ApkTranslatorPro#Cezali1Genc".toCharArray()
    }

    data class SignerIdentity(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val certSha256: String
    )

    fun getOrCreateSignerIdentity(): SignerIdentity {
        keystoreFile.parentFile?.mkdirs()
        if (keystoreFile.exists()) {
            try {
                val ks = KeyStore.getInstance(KEYSTORE_TYPE)
                FileInputStream(keystoreFile).use { ks.load(it, KEYSTORE_PASSWORD) }
                val key = ks.getKey(KEY_ALIAS, KEYSTORE_PASSWORD) as? PrivateKey
                val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate
                if (key != null && cert != null) {
                    return SignerIdentity(key, cert, SecurityGuards.computeSha256(cert.encoded))
                }
            } catch (_: Throwable) {
                // Regenerate corrupted keystore below
            }
        }
        return regenerateSignerIdentity()
    }

    fun regenerateSignerIdentity(): SignerIdentity {
        keystoreFile.parentFile?.mkdirs()
        val generated = X509CertificateGenerator.generateSelfSignedRsaIdentity()
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        ks.load(null, null)
        ks.setKeyEntry(
            KEY_ALIAS,
            generated.keyPair.private,
            KEYSTORE_PASSWORD,
            arrayOf(generated.certificate)
        )
        FileOutputStream(keystoreFile).use { ks.store(it, KEYSTORE_PASSWORD) }
        return SignerIdentity(
            privateKey = generated.keyPair.private,
            certificate = generated.certificate,
            certSha256 = SecurityGuards.computeSha256(generated.certificate.encoded)
        )
    }

    fun signApk(
        unsignedApk: File,
        signedApk: File,
        minSdkVersion: Int = 26
    ): SignerIdentity {
        val identity = getOrCreateSignerIdentity()
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "CEZALI_1GENC",
            identity.privateKey,
            listOf(identity.certificate)
        ).build()

        signedApk.parentFile?.mkdirs()
        if (signedApk.exists()) signedApk.delete()

        val signer = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(minSdkVersion.coerceAtLeast(24))
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setCreatedBy("APK Translator Pro (Cezali 1Genc)")
            .build()

        signer.sign()
        return identity
    }

    fun verifyApk(apkFile: File, minSdkVersion: Int = 26): SigningVerificationReport {
        val verifier = ApkVerifier.Builder(apkFile)
            .setMinCheckedPlatformVersion(minSdkVersion.coerceAtLeast(24))
            .build()
        val result = verifier.verify()
        val certSha = result.signerCertificates.firstOrNull()?.let {
            SecurityGuards.computeSha256(it.encoded)
        } ?: ""

        return SigningVerificationReport(
            isVerified = result.isVerified,
            v1Verified = result.isVerifiedUsingV1Scheme,
            v2Verified = result.isVerifiedUsingV2Scheme,
            v3Verified = result.isVerifiedUsingV3Scheme,
            signerCertSha256 = certSha,
            errors = result.errors.map { it.toString() },
            warnings = result.warnings.map { it.toString() }
        )
    }
}
