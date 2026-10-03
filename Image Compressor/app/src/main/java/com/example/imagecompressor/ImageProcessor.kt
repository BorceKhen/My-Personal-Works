package com.example.imagecompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

enum class TargetFormat(val displayName: String) {
    ORIGINAL("Keep Original"),
    JPEG("JPEG (.jpg)"),
    PNG("PNG (.png)"),
    WEBP("WebP (.webp)")
}

data class ProcessResult(
    val outputFile: File,
    val originalFormat: OutputImageFormat,
    val outputFormat: OutputImageFormat,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val savedBytes: Long,
    val reductionPercentage: Float,
    val elapsedMillis: Long,
    val usedQuality: Int,
    val convertedAlphaToWhite: Boolean
)

object ImageProcessor {

    /**
     * Color quantization and spatial dithering for PNGs.
     * Android's Bitmap.compress(PNG, quality, out) ignores the quality param.
     * By quantizing the color space with Floyd-Steinberg error diffusion according to quality,
     * the PNG DEFLATE compressor produces dramatically smaller file sizes while preserving
     * alpha channel transparency.
     */
    fun quantizePngBitmap(src: Bitmap, quality: Int): Bitmap {
        if (quality >= 98) return src

        val width = src.width
        val height = src.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Map quality (40..95) to quantization levels per color channel
        val levels = when {
            quality >= 90 -> 48 // ~5.5 bits
            quality >= 80 -> 32 // 5 bits
            quality >= 70 -> 24 // ~4.5 bits
            quality >= 60 -> 16 // 4 bits
            quality >= 50 -> 12 // ~3.5 bits
            else -> 8          // 3 bits
        }

        val step = 255.0f / (levels - 1).toFloat()

        // Line buffers for Floyd-Steinberg error diffusion
        val errCurrR = FloatArray(width + 2)
        val errCurrG = FloatArray(width + 2)
        val errCurrB = FloatArray(width + 2)

        val errNextR = FloatArray(width + 2)
        val errNextG = FloatArray(width + 2)
        val errNextB = FloatArray(width + 2)

        for (y in 0 until height) {
            val rowOffset = y * width

            for (x in 0 until width) {
                val pixel = pixels[rowOffset + x]
                val a = (pixel ushr 24) and 0xFF

                // If fully transparent, preserve without color alteration
                if (a == 0) {
                    pixels[rowOffset + x] = 0
                    continue
                }

                val bufX = x + 1
                val rIn = ((pixel ushr 16) and 0xFF) + errCurrR[bufX]
                val gIn = ((pixel ushr 8) and 0xFF) + errCurrG[bufX]
                val bIn = (pixel and 0xFF) + errCurrB[bufX]

                val rClamped = rIn.coerceIn(0f, 255f)
                val gClamped = gIn.coerceIn(0f, 255f)
                val bClamped = bIn.coerceIn(0f, 255f)

                // Quantize to nearest bucket
                val rQuant = ((rClamped / step).roundToInt() * step).coerceIn(0f, 255f)
                val gQuant = ((gClamped / step).roundToInt() * step).coerceIn(0f, 255f)
                val bQuant = ((bClamped / step).roundToInt() * step).coerceIn(0f, 255f)

                pixels[rowOffset + x] = (a shl 24) or
                        (rQuant.toInt() shl 16) or
                        (gQuant.toInt() shl 8) or
                        bQuant.toInt()

                // Diffuse quantization error: 7/16 right, 3/16 down-left, 5/16 down, 1/16 down-right
                val errR = rClamped - rQuant
                val errG = gClamped - gQuant
                val errB = bClamped - bQuant

                errCurrR[bufX + 1] += errR * (7f / 16f)
                errCurrG[bufX + 1] += errG * (7f / 16f)
                errCurrB[bufX + 1] += errB * (7f / 16f)

                errNextR[bufX - 1] += errR * (3f / 16f)
                errNextG[bufX - 1] += errG * (3f / 16f)
                errNextB[bufX - 1] += errB * (3f / 16f)

                errNextR[bufX] += errR * (5f / 16f)
                errNextG[bufX] += errG * (5f / 16f)
                errNextB[bufX] += errB * (5f / 16f)

                errNextR[bufX + 1] += errR * (1f / 16f)
                errNextG[bufX + 1] += errG * (1f / 16f)
                errNextB[bufX + 1] += errB * (1f / 16f)
            }

            // Swap line error buffers and clear next line
            System.arraycopy(errNextR, 0, errCurrR, 0, width + 2)
            System.arraycopy(errNextG, 0, errCurrG, 0, width + 2)
            System.arraycopy(errNextB, 0, errCurrB, 0, width + 2)

            errNextR.fill(0f)
            errNextG.fill(0f)
            errNextB.fill(0f)
        }

        outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }

    /**
     * Safely decodes a bitmap from a content URI.
     */
    fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Replaces transparent pixels with a solid white background for JPEG conversion,
     * preventing ugly black boxes around transparent PNGs/WebPs.
     */
    fun compositeOverWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val solid = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(solid)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return solid
    }

    /**
     * Master unified processing and conversion pipeline.
     */
    fun processAndConvertImage(
        context: Context,
        inputUri: Uri,
        targetFormatSelection: TargetFormat,
        targetQuality: Int,
        isPureLossless: Boolean
    ): ProcessResult? {
        val startTime = System.currentTimeMillis()

        // 1. Detect original format and size
        val mimeType = FileUtil.detectMimeType(context, inputUri).lowercase()
        val originalSize: Long = context.contentResolver.openFileDescriptor(inputUri, "r")?.use {
            it.statSize
        } ?: 0L

        val originalFormat = when {
            mimeType.contains("png") -> OutputImageFormat.PNG
            mimeType.contains("webp") -> OutputImageFormat.WEBP
            else -> OutputImageFormat.JPEG
        }

        // 2. Resolve destination format
        val resolvedFormat = when (targetFormatSelection) {
            TargetFormat.ORIGINAL -> originalFormat
            TargetFormat.JPEG -> OutputImageFormat.JPEG
            TargetFormat.PNG -> OutputImageFormat.PNG
            TargetFormat.WEBP -> OutputImageFormat.WEBP
        }

        val outputFile = FileUtil.createTempOutputFile(context, resolvedFormat)
        var convertedAlphaToWhite = false

        // 3. Fast path: JPEG-to-JPEG via native C++ NDK engine
        if (originalFormat == OutputImageFormat.JPEG && resolvedFormat == OutputImageFormat.JPEG) {
            val cachedInput = File(context.cacheDir, "temp_native_input_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(inputUri)?.use { input: InputStream ->
                FileOutputStream(cachedInput).use { out -> input.copyTo(out) }
            } ?: return null

            val success = if (isPureLossless) {
                NativeCompressor.optimizeJpegLossless(cachedInput.absolutePath, outputFile.absolutePath)
            } else {
                NativeCompressor.compressJpegVisuallyLossless(
                    cachedInput.absolutePath,
                    outputFile.absolutePath,
                    targetQuality
                )
            }
            cachedInput.delete()

            if (!success || !outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return null
            }
        } else {
            // General pipeline: Decode bitmap
            val rawBitmap = decodeBitmap(context, inputUri) ?: return null

            when (resolvedFormat) {
                OutputImageFormat.PNG -> {
                    // PNG compression: Quantize if lossy, otherwise pure lossless DEFLATE
                    val finalBitmap = if (isPureLossless) {
                        rawBitmap
                    } else {
                        quantizePngBitmap(rawBitmap, targetQuality)
                    }

                    FileOutputStream(outputFile).use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    if (finalBitmap != rawBitmap) {
                        finalBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }

                OutputImageFormat.WEBP -> {
                    // WebP compression
                    FileOutputStream(outputFile).use { out ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val format = if (isPureLossless) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            }
                            rawBitmap.compress(format, targetQuality, out)
                        } else {
                            @Suppress("DEPRECATION")
                            rawBitmap.compress(Bitmap.CompressFormat.WEBP, targetQuality, out)
                        }
                    }
                    rawBitmap.recycle()
                }

                OutputImageFormat.JPEG -> {
                    // JPEG conversion: replace alpha transparency with solid white
                    val readyBitmap = if (rawBitmap.hasAlpha()) {
                        convertedAlphaToWhite = true
                        compositeOverWhite(rawBitmap)
                    } else {
                        rawBitmap
                    }

                    // Temporary uncompressed JPEG for native optimization
                    val intermediateJpg = File(context.cacheDir, "temp_inter_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(intermediateJpg).use { out ->
                        readyBitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
                    }

                    if (readyBitmap != rawBitmap) {
                        readyBitmap.recycle()
                    }
                    rawBitmap.recycle()

                    // Route through native compressor for optimal Huffman + 4:2:0 subsampling
                    val success = if (isPureLossless) {
                        NativeCompressor.optimizeJpegLossless(
                            intermediateJpg.absolutePath,
                            outputFile.absolutePath
                        )
                    } else {
                        NativeCompressor.compressJpegVisuallyLossless(
                            intermediateJpg.absolutePath,
                            outputFile.absolutePath,
                            targetQuality
                        )
                    }
                    intermediateJpg.delete()

                    if (!success || !outputFile.exists() || outputFile.length() == 0L) {
                        outputFile.delete()
                        return null
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        val finalSize = outputFile.length()
        val realOriginalSize = if (originalSize > 0) originalSize else finalSize
        val saved = (realOriginalSize - finalSize).coerceAtLeast(0L)
        val reduction = if (realOriginalSize > 0) {
            ((realOriginalSize - finalSize).toFloat() / realOriginalSize.toFloat()) * 100f
        } else 0f

        return ProcessResult(
            outputFile = outputFile,
            originalFormat = originalFormat,
            outputFormat = resolvedFormat,
            originalSizeBytes = realOriginalSize,
            compressedSizeBytes = finalSize,
            savedBytes = saved,
            reductionPercentage = reduction,
            elapsedMillis = endTime - startTime,
            usedQuality = if (isPureLossless) 100 else targetQuality,
            convertedAlphaToWhite = convertedAlphaToWhite
        )
    }
}
