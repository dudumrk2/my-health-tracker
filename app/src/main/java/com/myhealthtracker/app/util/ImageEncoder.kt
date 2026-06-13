package com.myhealthtracker.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageEncoder {
    private const val MAX_DIM = 1024
    private const val JPEG_QUALITY = 80

    /** Loads the image at [uri], downscales it, and returns base64 JPEG. Returns null on failure. */
    fun uriToBase64Jpeg(context: Context, uri: Uri): String? {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        return bitmapToBase64Jpeg(original)
    }

    fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val scaled = downscale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val bytes = out.toByteArray()
        if (scaled != bitmap) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_DIM) return bitmap
        val ratio = MAX_DIM.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
    }
}
