package com.example.imagecompressor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

enum class OutputImageFormat(val extension: String, val mimeType: String, val displayName: String) {
    JPEG("jpg", "image/jpeg", "JPEG"),
    PNG("png", "image/png", "PNG"),
    WEBP("webp", "image/webp", "WebP")
}

data class PreparedImage(
    val file: File,
    val format: OutputImageFormat,
    val originalSizeBytes: Long
)

object FileUtil {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Detects MIME type accurately using ContentResolver, extension mapping,
     * and file header signature sniffing.
     */
    fun detectMimeType(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val type = resolver.getType(uri)
        if (!type.isNullOrBlank()) return type

        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!extension.isNullOrBlank()) {
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (!mapped.isNullOrBlank()) return mapped
        }

        return try {
            resolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                val read = stream.read(header)
                if (read >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                    "image/jpeg"
                } else if (read >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte()) {
                    "image/png"
                } else if (read >= 12 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte()) {
                    "image/webp"
                } else {
                    "image/jpeg"
                }
            } ?: "image/jpeg"
        } catch (e: Exception) {
            "image/jpeg"
        }
    }

    /**
     * Prepares the image while strictly preserving its original format:
     * - PNG stays PNG (preserving transparency and crisp lines).
     * - JPEG stays JPEG (ready for native libjpeg optimization).
     * - WebP stays WebP.
     * - HEIC/HEIF is converted to JPEG for universal Android compatibility.
     */
    fun prepareImage(context: Context, uri: Uri): PreparedImage? {
        return try {
            val mimeType = detectMimeType(context, uri).lowercase(Locale.US)

            val originalSize: Long = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L

            when {
                mimeType.contains("png") -> {
                    // PNG: Copy directly to cache as .png
                    val cachedFile = File(context.cacheDir, "booth_input_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return null

                    val actualSize = if (originalSize > 0) originalSize else cachedFile.length()
                    PreparedImage(cachedFile, OutputImageFormat.PNG, actualSize)
                }

                mimeType.contains("webp") -> {
                    // WebP: Copy directly to cache as .webp
                    val cachedFile = File(context.cacheDir, "booth_input_${System.currentTimeMillis()}.webp")
                    context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return null

                    val actualSize = if (originalSize > 0) originalSize else cachedFile.length()
                    PreparedImage(cachedFile, OutputImageFormat.WEBP, actualSize)
                }

                mimeType.contains("heic") || mimeType.contains("heif") -> {
                    // HEIC: Decode to bitmap and write clean JPEG for processing
                    val bitmap: Bitmap = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }) ?: return null

                    val convertedFile = File(context.cacheDir, "booth_input_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(convertedFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bitmap.recycle()

                    val actualSize = if (originalSize > 0) originalSize else convertedFile.length()
                    PreparedImage(convertedFile, OutputImageFormat.JPEG, actualSize)
                }

                else -> {
                    // JPEG default: Copy directly to cache as .jpg
                    val cachedFile = File(context.cacheDir, "booth_input_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return null

                    val actualSize = if (originalSize > 0) originalSize else cachedFile.length()
                    PreparedImage(cachedFile, OutputImageFormat.JPEG, actualSize)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compresses a PNG file while preserving full ARGB alpha channel transparency.
     */
    fun compressPng(inputFile: File, outputFile: File, quality: Int): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return false
            FileOutputStream(outputFile).use { out ->
                // PNG is inherently lossless; re-encoding cleans unneeded metadata chunks
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Compresses a WebP file with target quality factor.
     */
    fun compressWebp(inputFile: File, outputFile: File, quality: Int): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return false
            FileOutputStream(outputFile).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
                }
            }
            bitmap.recycle()
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createTempOutputFile(context: Context, format: OutputImageFormat): File {
        return File(context.cacheDir, "booth_print_${System.currentTimeMillis()}.${format.extension}")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
    }

    /**
     * Saves the photo print to the public Pictures/PhotoBooth folder using Scoped Storage.
     * Preserves original format and MIME type.
     */
    fun saveImageToPictures(
        context: Context,
        sourceFile: File,
        format: OutputImageFormat,
        displayName: String = "photo_booth_${System.currentTimeMillis()}.${format.extension}"
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoBooth")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareImageFile(
        context: Context,
        file: File,
        format: OutputImageFormat,
        chooserTitle: String = "Share Photo Booth Print"
    ) {
        try {
            val authority = "${context.packageName}$AUTHORITY_SUFFIX"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
