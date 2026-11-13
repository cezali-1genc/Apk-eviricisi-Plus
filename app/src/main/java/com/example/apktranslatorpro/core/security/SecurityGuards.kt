package com.example.apktranslatorpro.core.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurityGuards {

    private const val BUFFER_SIZE = 16 * 1024
    const val MAX_ZIP_ENTRIES = 60_000
    const val MAX_SINGLE_ENTRY_BYTES = 250L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L
    const val MAX_COMPRESSION_RATIO = 150.0

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(file: File): String {
        FileInputStream(file).use { return computeSha256(it) }
    }

    fun computeUriSha256(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw SecurityException("Seçilen APK dosyası okunamıyor.")
        stream.use { return computeSha256(it) }
    }

    fun validateZipEntryPath(entryName: String, rootDir: File? = null): String {
        if (entryName.isBlank()) {
            throw SecurityException("Geçersiz boş ZIP girişi tespit edildi.")
        }
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains("\u0000")) {
            throw SecurityException("Güvenlik ihlali: Mutlak veya hatalı ZIP yolu ($entryName)")
        }
        val segments = normalized.split('/')
        if (segments.any { it == ".." }) {
            throw SecurityException("Path traversal saldırısı engellendi: $entryName")
        }
        if (rootDir != null) {
            val canonicalRoot = rootDir.canonicalPath
            val targetFile = File(rootDir, normalized)
            val canonicalTarget = targetFile.canonicalPath
            if (canonicalTarget != canonicalRoot && !canonicalTarget.startsWith(canonicalRoot + File.separator)) {
                throw SecurityException("Path traversal engellendi (dizin dışı hedef): $entryName")
            }
        }
        return normalized
    }

    fun validateZipBombLimits(
        entryCount: Int,
        entryName: String,
        entryUncompressedBytes: Long,
        entryCompressedBytes: Long,
        runningTotalUncompressedBytes: Long
    ) {
        if (entryCount > MAX_ZIP_ENTRIES) {
            throw SecurityException("ZIP bombası şüphesi: Giriş sayısı sınırı aşıldı (>$MAX_ZIP_ENTRIES)")
        }
        if (entryUncompressedBytes > MAX_SINGLE_ENTRY_BYTES) {
            throw SecurityException("ZIP bombası şüphesi: '$entryName' tek dosya sınırını aşıyor (>250 MB)")
        }
        if (runningTotalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw SecurityException("ZIP bombası şüphesi: Toplam açılmış boyut sınırı aşıldı (>1 GB)")
        }
        if (entryCompressedBytes > 0 && entryUncompressedBytes > 10L * 1024L * 1024L) {
            val ratio = entryUncompressedBytes.toDouble() / entryCompressedBytes.toDouble()
            if (ratio > MAX_COMPRESSION_RATIO) {
                throw SecurityException(
                    "ZIP bombası şüphesi: '$entryName' aşırı sıkıştırma oranına sahip (${ratio.toInt()}x)"
                )
            }
        }
    }
}

class KeystoreSecretStorage {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "apk_translator_pro_api_secret_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_SEPARATOR = ":"
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = getOrCreateKey() ?: return "PLAIN:${Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "GCM$IV_SEPARATOR$ivB64$IV_SEPARATOR$dataB64"
        } catch (_: Throwable) {
            "PLAIN:${Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        if (cipherText.startsWith("PLAIN:")) {
            return try {
                String(Base64.decode(cipherText.removePrefix("PLAIN:"), Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        }
        if (!cipherText.startsWith("GCM$IV_SEPARATOR")) return cipherText
        val parts = cipherText.split(IV_SEPARATOR)
        if (parts.size != 3) return ""
        return try {
            val key = getOrCreateKey() ?: return ""
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
