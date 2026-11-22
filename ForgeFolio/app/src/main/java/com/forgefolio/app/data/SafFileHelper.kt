package com.forgefolio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object SafFileHelper {

    fun queryFileMeta(context: Context, uri: Uri, fallbackName: String = "document"): Pair<String, Long> {
        var displayName = fallbackName
        var sizeBytes = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }
        return displayName to sizeBytes
    }

    /**
     * Opens a seekable ParcelFileDescriptor for PdfRenderer. If the SAF provider
     * returns a non-seekable pipe, copies the stream into a temporary cache file first.
     */
    fun withSeekablePdfDescriptor(
        context: Context,
        uri: Uri,
        block: (ParcelFileDescriptor) -> Unit
    ) {
        val direct = context.contentResolver.openFileDescriptor(uri, "r")
        if (direct != null) {
            val isSeekable = runCatching { direct.statSize >= 0 }.getOrDefault(false)
            if (isSeekable) {
                direct.use(block)
                return
            }
            direct.close()
        }

        val tempFile = File.createTempFile("forgefolio_pdf_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open PDF stream.")
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use(block)
        } finally {
            tempFile.delete()
        }
    }

    fun readImageBounds(context: Context, uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    /**
     * Decodes an image safely with inSampleSize OOM protection and applies EXIF orientation.
     */
    fun decodeSampledBitmapWithExif(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int
    ): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        var sampleSize = 1
        val rawW = boundsOpts.outWidth.coerceAtLeast(1)
        val rawH = boundsOpts.outHeight.coerceAtLeast(1)
        while ((rawW / sampleSize) > maxDimensionPx || (rawH / sampleSize) > maxDimensionPx) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: error("Failed to decode image: $uri")

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return decoded
        }

        val transformed = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        )
        if (transformed !== decoded) {
            decoded.recycle()
        }
        return transformed
    }

    fun compressFormatCompat(ext: String): Bitmap.CompressFormat {
        return when (ext.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.JPEG
        }
    }
}
