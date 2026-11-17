package com.stylecam.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

object ImageUtils {
    /**
     * Decodes [uri] (read-only - the original is never modified), honours EXIF orientation and
     * limits the longest edge to [maxSide]. Returns null if the image cannot be read.
     */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) {
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated !== bmp) bmp.recycle()
                bmp = rotated
            }
            limit(bmp, maxSide)
        } catch (t: Throwable) {
            android.util.Log.e("ImageUtils", "decode failed", t)
            null
        }
    }

    /** Scales [bmp] down so its longest edge is at most [maxSide] (never scales up). */
    fun limit(bmp: Bitmap, maxSide: Int, recycleSource: Boolean = true): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val s = maxSide.toFloat() / longest
        val out = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(2), (bmp.height * s).toInt().coerceAtLeast(2), true)
        if (recycleSource && out !== bmp) bmp.recycle()
        return out
    }
}
