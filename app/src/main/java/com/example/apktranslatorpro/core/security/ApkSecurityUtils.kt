package com.example.apktranslatorpro.core.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

data class SafApkVerificationResult(
    val uri: Uri,
    val exists: Boolean,
    val isReadable: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val sha256Hex: String
)

/**
 * Utility class that calculates and returns SHA-256 digests of APK files and
 * verifies SAF URI existence and integrity without ever modifying the original file.
 */
class ApkSecurityUtils {

    fun calculateSha256(file: File): String = Companion.calculateSha256(file)

    fun calculateSha256(inputStream: InputStream): String = Companion.calculateSha256(inputStream)

    fun calculateSha256(bytes: ByteArray): String = Companion.calculateSha256(bytes)

    fun calculateApkUriSha256(context: Context, apkUri: Uri): String =
        Companion.calculateApkUriSha256(context, apkUri)

    fun verifySafApkExistsReadOnly(context: Context, apkUri: Uri): SafApkVerificationResult =
        Companion.verifySafApkExistsReadOnly(context, apkUri)

    fun verifyApkIntegrity(
        context: Context,
        apkUri: Uri,
        expectedSha256Hex: String
    ): ApkIntegrityReport = Companion.verifyApkIntegrity(context, apkUri, expectedSha256Hex)

    companion object {
        private const val BUFFER_SIZE = 16 * 1024

        fun calculateSha256(file: File): String {
            require(file.exists() && file.isFile) {
                "SHA-256 hesaplanacak APK dosyası bulunamadı: ${file.name}"
            }
            FileInputStream(file).use { stream ->
                return calculateSha256(stream)
            }
        }

        fun calculateSha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun calculateSha256(inputStream: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun calculateApkUriSha256(context: Context, apkUri: Uri): String {
            val stream = context.contentResolver.openInputStream(apkUri)
                ?: throw SecurityException("Seçilen APK dosyası salt okunur olarak açılamadı.")
            stream.use { return calculateSha256(it) }
        }

        /**
         * Verifies via SAF / ContentResolver that the target APK URI exists and is readable
         * without opening write descriptors or modifying the underlying file, then computes SHA-256.
         */
        fun verifySafApkExistsReadOnly(context: Context, apkUri: Uri): SafApkVerificationResult {
            val isDocumentUri = runCatching {
                android.provider.DocumentsContract.isDocumentUri(context, apkUri)
            }.getOrDefault(false)
            val docFile = if (isDocumentUri) {
                runCatching { DocumentFile.fromSingleUri(context, apkUri) }.getOrNull()
            } else {
                null
            }
            var displayName = docFile?.name.orEmpty()
            var reportedSize = docFile?.length() ?: -1L

            if (apkUri.scheme == "file") {
                val localFile = apkUri.path?.let { File(it) }
                    ?: throw SecurityException("Geçersiz dosya URI yolu.")
                if (!localFile.exists() || !localFile.isFile) {
                    throw SecurityException("Seçilen APK dosyası bulunamadı.")
                }
                if (displayName.isBlank()) displayName = localFile.name
                if (reportedSize <= 0L) reportedSize = localFile.length()
            } else {
                context.contentResolver.query(
                    apkUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && displayName.isBlank()) {
                            displayName = cursor.getString(nameIdx).orEmpty()
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            reportedSize = cursor.getLong(sizeIdx)
                        }
                    }
                }

                if (docFile != null && !docFile.exists()) {
                    throw SecurityException("Seçilen APK dosyası cihaz depolamasında bulunamadı.")
                }
            }

            val stream = context.contentResolver.openInputStream(apkUri)
                ?: throw SecurityException("Seçilen APK dosyası okunamıyor veya artık mevcut değil.")

            val digest = MessageDigest.getInstance("SHA-256")
            var countedBytes = 0L
            stream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    countedBytes += read
                }
            }

            if (countedBytes <= 0L) {
                throw IllegalArgumentException("Seçilen APK dosyası boş (0 bayt).")
            }

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            val resolvedName = displayName.ifBlank {
                apkUri.lastPathSegment?.substringAfterLast('/') ?: "selected_app.apk"
            }

            return SafApkVerificationResult(
                uri = apkUri,
                exists = true,
                isReadable = true,
                displayName = resolvedName,
                sizeBytes = if (reportedSize > 0L) reportedSize else countedBytes,
                sha256Hex = shaHex
            )
        }

        fun verifyApkIntegrity(
            context: Context,
            apkUri: Uri,
            expectedSha256Hex: String
        ): ApkIntegrityReport {
            val currentHash = calculateApkUriSha256(context, apkUri)
            return ApkIntegrityReport(
                initialSha256 = expectedSha256Hex,
                finalSha256 = currentHash,
                isUnchanged = currentHash.equals(expectedSha256Hex, ignoreCase = true)
            )
        }
    }
}
