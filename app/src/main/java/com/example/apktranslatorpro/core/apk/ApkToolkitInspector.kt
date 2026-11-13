package com.example.apktranslatorpro.core.apk

import com.android.apksig.ApkVerifier
import com.example.apktranslatorpro.core.security.ApkSecurityUtils
import java.io.File
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ApkSignerCertDetails(
    val subjectDn: String,
    val issuerDn: String,
    val validFrom: String,
    val validUntil: String,
    val keyAlgorithm: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String
)

data class ApkFullInspectionReport(
    val totalZipEntries: Int,
    val compressedSizeBytes: Long,
    val uncompressedSizeBytes: Long,
    val dexFiles: List<String>,
    val nativeAbis: Map<String, List<String>>,
    val assetCount: Int,
    val resEntryCount: Int,
    val resourcesArscStored: Boolean,
    val usesPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val usesFeatures: List<String>,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val isSignatureVerified: Boolean,
    val v1Signed: Boolean,
    val v2Signed: Boolean,
    val v3Signed: Boolean,
    val v4Signed: Boolean,
    val signerCertificates: List<ApkSignerCertDetails>,
    val verificationIssues: List<String>,
    val md5Hex: String,
    val sha1Hex: String,
    val sha256Hex: String
)

object ApkToolkitInspector {

    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW"
    )

    fun inspectFullApk(
        apkFile: File,
        manifestInfo: ManifestInfo,
        sha256Hex: String
    ): ApkFullInspectionReport {
        val dexFiles = mutableListOf<String>()
        val nativeAbis = linkedMapOf<String, MutableList<String>>()
        var totalEntries = 0
        var totalCompressed = 0L
        var totalUncompressed = 0L
        var assetCount = 0
        var resCount = 0
        var arscStored = false

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                totalEntries++
                if (entry.compressedSize > 0) totalCompressed += entry.compressedSize
                if (entry.size > 0) totalUncompressed += entry.size

                val name = entry.name
                when {
                    name.matches(Regex("^classes\\d*\\.dex$", RegexOption.IGNORE_CASE)) -> dexFiles.add(name)
                    name.startsWith("lib/") && name.endsWith(".so", ignoreCase = true) -> {
                        val parts = name.split('/')
                        if (parts.size >= 3) {
                            nativeAbis.getOrPut(parts[1]) { mutableListOf() }.add(parts.drop(2).joinToString("/"))
                        }
                    }
                    name.startsWith("assets/") && !entry.isDirectory -> assetCount++
                    name.startsWith("res/") && !entry.isDirectory -> resCount++
                    name == "resources.arsc" -> arscStored = entry.method == ZipEntry.STORED
                }
            }
        }

        val minSdk = manifestInfo.minSdkVersion.coerceAtLeast(21)
        val verifierResult = runCatching {
            ApkVerifier.Builder(apkFile)
                .setMinCheckedPlatformVersion(minSdk)
                .build()
                .verify()
        }.getOrNull()

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val certs = verifierResult?.signerCertificates.orEmpty().map { cert ->
            val encoded = cert.encoded
            val keyInfo = when (val pub = cert.publicKey) {
                is RSAPublicKey -> "RSA-${pub.modulus.bitLength()}"
                is ECPublicKey -> "EC-${pub.params.order.bitLength()}"
                else -> pub.algorithm
            }
            ApkSignerCertDetails(
                subjectDn = cert.subjectX500Principal.name,
                issuerDn = cert.issuerX500Principal.name,
                validFrom = dateFmt.format(cert.notBefore),
                validUntil = dateFmt.format(cert.notAfter),
                keyAlgorithm = keyInfo,
                sha256Fingerprint = ApkSecurityUtils.calculateSha256(encoded),
                sha1Fingerprint = digestHex("SHA-1", encoded)
            )
        }

        val issues = mutableListOf<String>()
        if (verifierResult == null) {
            issues.add("APK imza doğrulayıcı çalıştırılamadı.")
        } else {
            issues.addAll(verifierResult.errors.map { "Hata: $it" })
            issues.addAll(verifierResult.warnings.map { "Uyarı: $it" })
        }

        val apkBytesHashes = computeMd5AndSha1(apkFile)
        val dangerous = manifestInfo.usesPermissions.filter { it in DANGEROUS_PERMISSIONS }

        return ApkFullInspectionReport(
            totalZipEntries = totalEntries,
            compressedSizeBytes = totalCompressed,
            uncompressedSizeBytes = totalUncompressed,
            dexFiles = dexFiles.sorted(),
            nativeAbis = nativeAbis.mapValues { it.value.sorted() },
            assetCount = assetCount,
            resEntryCount = resCount,
            resourcesArscStored = arscStored,
            usesPermissions = manifestInfo.usesPermissions,
            dangerousPermissions = dangerous,
            activities = manifestInfo.activities,
            services = manifestInfo.services,
            receivers = manifestInfo.receivers,
            providers = manifestInfo.providers,
            usesFeatures = manifestInfo.usesFeatures,
            isDebuggable = manifestInfo.isDebuggable,
            allowBackup = manifestInfo.allowBackup,
            isSignatureVerified = verifierResult?.isVerified == true,
            v1Signed = verifierResult?.isVerifiedUsingV1Scheme == true,
            v2Signed = verifierResult?.isVerifiedUsingV2Scheme == true,
            v3Signed = verifierResult?.isVerifiedUsingV3Scheme == true,
            v4Signed = verifierResult?.isVerifiedUsingV4Scheme == true,
            signerCertificates = certs,
            verificationIssues = issues.distinct(),
            md5Hex = apkBytesHashes.first,
            sha1Hex = apkBytesHashes.second,
            sha256Hex = sha256Hex
        )
    }

    private fun digestHex(algo: String, bytes: ByteArray): String {
        val d = MessageDigest.getInstance(algo).digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun computeMd5AndSha1(file: File): Pair<String, String> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                md5.update(buf, 0, read)
                sha1.update(buf, 0, read)
            }
        }
        return md5.digest().joinToString("") { "%02x".format(it) } to
            sha1.digest().joinToString("") { "%02x".format(it) }
    }
}
