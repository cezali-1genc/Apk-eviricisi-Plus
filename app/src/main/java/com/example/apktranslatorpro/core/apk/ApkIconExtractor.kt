package com.example.apktranslatorpro.core.apk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Extracts the real application launcher icon from a copied APK file either via
 * [PackageManager.getPackageArchiveInfo] or by scanning `res/mipmap*` / `res/drawable*` raster assets.
 */
object ApkIconExtractor {

    private const val MAX_ICON_BYTES = 4 * 1024 * 1024
    private const val MAX_ICON_DIMENSION_PX = 256

    fun extractAppIcon(context: Context, apkFile: File): ImageBitmap? {
        if (!apkFile.exists()) return null

        // 1. Try Android PackageManager archive info first (supports adaptive/vector icons too)
        runCatching {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
            val appInfo = pkgInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                val drawable = appInfo.loadIcon(pm)
                val bmp = drawable.toBitmap(width = 128, height = 128)
                return bmp.asImageBitmap()
            }
        }

        // 2. Fallback: find highest-resolution launcher PNG/WEBP/JPG inside the APK ZIP with OOM-safe downsampling
        return runCatching {
            ZipFile(apkFile).use { zip ->
                val iconEntry = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter { entry ->
                        val lower = entry.name.lowercase()
                        (lower.startsWith("res/mipmap") || lower.startsWith("res/drawable")) &&
                            (lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg")) &&
                            (lower.contains("ic_launcher") || lower.contains("icon") || lower.contains("logo"))
                    }
                    .sortedByDescending { entry ->
                        when {
                            entry.name.contains("xxxhdpi") -> 6
                            entry.name.contains("xxhdpi") -> 5
                            entry.name.contains("xhdpi") -> 4
                            entry.name.contains("hdpi") -> 3
                            entry.name.contains("mdpi") -> 2
                            else -> 1
                        }
                    }
                    .firstOrNull()

                iconEntry?.let { entry ->
                    if (entry.size > MAX_ICON_BYTES) return@let null
                    zip.getInputStream(entry).use { stream ->
                        val bytes = readBoundedBytes(stream, MAX_ICON_BYTES) ?: return@use null
                        decodeSampledBitmap(bytes, MAX_ICON_DIMENSION_PX)?.asImageBitmap()
                    }
                }
            }
        }.getOrNull()
    }

    internal fun decodeSampledBitmap(bytes: ByteArray, maxDimensionPx: Int = MAX_ICON_DIMENSION_PX): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(
            width = boundsOpts.outWidth,
            height = boundsOpts.outHeight,
            reqWidth = maxDimensionPx,
            reqHeight = maxDimensionPx
        )
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    internal fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            total += read
            if (total > maxBytes) return null
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
