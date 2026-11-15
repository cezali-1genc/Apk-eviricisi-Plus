package com.example.apktranslatorpro.core.apk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts the real application launcher icon from a copied APK file either via
 * [PackageManager.getPackageArchiveInfo] or by scanning `res/mipmap*` / `res/drawable*` raster assets.
 */
object ApkIconExtractor {

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

        // 2. Fallback: find highest-resolution launcher PNG/WEBP/JPG inside the APK ZIP
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
                    zip.getInputStream(entry).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            }
        }.getOrNull()
    }
}
