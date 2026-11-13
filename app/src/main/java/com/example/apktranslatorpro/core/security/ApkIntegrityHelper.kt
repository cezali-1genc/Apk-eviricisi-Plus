package com.example.apktranslatorpro.core.security

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

data class ApkIntegrityReport(
    val initialSha256: String,
    val finalSha256: String,
    val isUnchanged: Boolean
)

/**
 * Utility class dedicated to computing cryptographic SHA-256 hashes of APK files
 * and verifying that the user's original APK remains byte-for-byte unchanged
 * throughout extraction, translation, repackaging, and signing.
 */
object ApkIntegrityHelper {

    private const val STREAM_BUFFER_SIZE = 16 * 1024

    fun calculateSha256(file: File): String {
        require(file.exists() && file.isFile) { "SHA-256 hesaplanacak dosya bulunamadı: ${file.name}" }
        FileInputStream(file).use { stream ->
            return calculateSha256(stream)
        }
    }

    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun calculateApkUriSha256(context: Context, apkUri: Uri): String {
        val stream = context.contentResolver.openInputStream(apkUri)
            ?: throw SecurityException("Seçilen APK dosyası okunamıyor (SAF akışı açılamadı).")
        stream.use { return calculateSha256(it) }
    }

    fun verifyOriginalApkUnchanged(
        context: Context,
        originalApkUri: Uri,
        expectedSha256: String
    ): ApkIntegrityReport {
        val finalHash = calculateApkUriSha256(context, originalApkUri)
        return ApkIntegrityReport(
            initialSha256 = expectedSha256,
            finalSha256 = finalHash,
            isUnchanged = finalHash.equals(expectedSha256, ignoreCase = true)
        )
    }

    fun verifyFileUnchanged(file: File, expectedSha256: String): ApkIntegrityReport {
        val finalHash = calculateSha256(file)
        return ApkIntegrityReport(
            initialSha256 = expectedSha256,
            finalSha256 = finalHash,
            isUnchanged = finalHash.equals(expectedSha256, ignoreCase = true)
        )
    }
}
