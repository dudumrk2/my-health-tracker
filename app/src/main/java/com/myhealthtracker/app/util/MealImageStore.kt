package com.myhealthtracker.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device, app-private storage for meal photos. Images NEVER leave the device — only
 * the absolute file path is referenced from the Firestore meal document. Files live in
 * filesDir/meal_images (not cacheDir, so the OS does not evict them).
 */
object MealImageStore {
    private const val DIR = "meal_images"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Downscales [uri] to a JPEG, writes a new file, and returns its absolute path (or null). */
    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = ImageEncoder.uriToJpegBytes(context, uri) ?: return@withContext null
        runCatching {
            val file = File.createTempFile("meal_", ".jpg", dir(context))
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    /** Reads the file at [path] and returns base64 (NO_WRAP), or null if missing/unreadable. */
    fun readAsBase64(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    fun delete(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    /** Deletes every file in [dir] whose absolute path is not in [referencedPaths]. Returns count deleted. */
    fun sweepOrphans(dir: File, referencedPaths: Set<String>): Int {
        val files = dir.listFiles() ?: return 0
        var deleted = 0
        for (f in files) if (f.isFile && f.absolutePath !in referencedPaths && f.delete()) deleted++
        return deleted
    }
}
