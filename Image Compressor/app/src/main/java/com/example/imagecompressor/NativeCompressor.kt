package com.example.imagecompressor

/**
 * Native JNI bridge providing both Pure Mathematical Lossless and
 * Visually Lossless JPEG compression routines using libjpeg-turbo.
 */
class NativeCompressor {

    companion object {
        init {
            System.loadLibrary("nativecompressor")
        }

        /**
         * Mode 1: Pure Mathematical Lossless JPEG Optimization.
         * - Copies DCT frequency coefficients directly without decoding pixels.
         * - Strips non-essential EXIF, XMP, and comment metadata.
         * - Recalculates and builds optimal Huffman coding tables.
         * - 0% loss in visual quality (identical decoded pixels).
         *
         * @return True on success, false on error.
         */
        @JvmStatic
        external fun optimizeJpegLossless(inputPath: String, outputPath: String): Boolean

        /**
         * Mode 2: Visually Lossless JPEG Compression.
         * - Uses 4:2:0 chroma subsampling and optimal 2-pass Huffman coding tables.
         * - Encodes with perceptual quantization at target quality (default 85%).
         * - Produces 30% to 70%+ file size reduction with imperceptible visual difference.
         * - Completely strips bloated EXIF and camera maker notes.
         *
         * @param quality Compression quality factor from 1 to 100 (recommended: 82 to 88).
         * @return True on success, false on error.
         */
        @JvmStatic
        external fun compressJpegVisuallyLossless(
            inputPath: String,
            outputPath: String,
            quality: Int
        ): Boolean

        /**
         * Unified compressor method selecting between Visually Lossless or Pure Lossless.
         */
        fun smartCompress(
            inputPath: String,
            outputPath: String,
            targetQuality: Int = 85,
            pureLossless: Boolean = false
        ): Boolean {
            return if (pureLossless) {
                optimizeJpegLossless(inputPath, outputPath)
            } else {
                compressJpegVisuallyLossless(inputPath, outputPath, targetQuality)
            }
        }
    }
}
