package com.stylecam.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/** Saves JPEGs into the system gallery (Pictures/StyleCam). Never overwrites anything. */
object MediaSaver {
    private const val FOLDER = "StyleCam"

    /** Must be called off the main thread. Returns the gallery Uri or null on failure. */
    fun saveJpeg(context: Context, bitmap: Bitmap, displayName: String, quality: Int = 95): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(context, bitmap, displayName, quality)
            else saveLegacy(context, bitmap, displayName, quality)
        } catch (t: Throwable) {
            android.util.Log.e("MediaSaver", "Save failed", t)
            null
        }
    }

    private fun saveScoped(context: Context, bitmap: Bitmap, name: String, quality: Int): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IllegalStateException("compress failed")
            } ?: throw IllegalStateException("no output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, name: String, quality: Int): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, "$name.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return Uri.fromFile(file)
    }
}
