package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Helper for creating camera FileProvider URIs, cropping square camera captures,
 * decoding large or HEIC/HEIF images safely, resizing to max 1000px, and
 * converting to JPEG before uploading to Firebase Storage.
 */
object ProductImageUtil {

    const val DEFAULT_MAX_DIMENSION_PX = 1000
    const val DEFAULT_MAX_BYTES = 200 * 1024

    fun isLocalPhotoUri(uriString: String): Boolean {
        val trimmed = uriString.trim()
        return trimmed.isNotBlank() &&
            !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
    }

    fun isLocalFilePresent(context: Context, uriString: String): Boolean {
        val trimmed = uriString.trim()
        if (trimmed.isBlank()) return false
        val uri = Uri.parse(trimmed)
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val f = File(path)
            return f.exists() && f.length() > 0L
        }
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun createTempCameraPhotoFile(context: Context): File {
        val photoDir = File(context.cacheDir, "product_photos").apply { mkdirs() }
        return File(photoDir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun createTempCameraPhotoUri(context: Context): Uri {
        val photoFile = createTempCameraPhotoFile(context)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    fun saveCompressedBytesLocally(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): String {
        val dir = File(context.filesDir, "product_photos").apply { mkdirs() }
        val outFile = File(dir, fileName)
        outFile.writeBytes(bytes)
        return Uri.fromFile(outFile).toString()
    }

    fun deleteLocalPhotoIfOwned(context: Context, uriString: String) {
        val uri = Uri.parse(uriString.trim())
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val file = File(path)
        val ownedFilesDir = File(context.filesDir, "product_photos").absolutePath
        val ownedCacheDir = File(context.cacheDir, "product_photos").absolutePath
        val absPath = file.absolutePath
        if (absPath.startsWith(ownedFilesDir) || absPath.startsWith(ownedCacheDir)) {
            runCatching { file.delete() }
        }
    }

    /**
     * Persists any local URI (gallery content:// or file://) into app-private filesDir/product_photos
     * as a resized <= 1000px JPEG so it stays readable across app restarts if upload fails offline.
     */
    fun persistResizedLocalJpeg(
        context: Context,
        sourceUri: Uri,
        fileName: String = "prod_local_${System.currentTimeMillis()}.jpg",
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): Pair<String, ByteArray> {
        val bytes = compressImageForUpload(
            context = context,
            uri = sourceUri,
            maxDimensionPx = maxDimensionPx,
            maxBytes = maxBytes
        )
        val localUri = saveCompressedBytesLocally(context, fileName, bytes)
        return localUri to bytes
    }

    /**
     * Crops the captured camera photo to ONLY the square frame centered inside the PreviewView
     * (FILL_CENTER), resizes to max 1000px, compresses to JPEG, and saves in filesDir/product_photos.
     */
    fun cropSquareAndSaveCameraPhoto(
        context: Context,
        rawCaptureFile: File,
        previewWidthPx: Int,
        previewHeightPx: Int,
        squareFrameSizePx: Float,
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): Uri {
        require(rawCaptureFile.exists() && rawCaptureFile.length() > 0L) {
            ShopLocale.get("err_open_image", ShopLocale.activeLanguage)
        }

        val fullBitmap = decodeFileOriented(rawCaptureFile, maxDimensionPx = 2400)
        val bmpW = fullBitmap.width
        val bmpH = fullBitmap.height
        require(bmpW > 0 && bmpH > 0) { ShopLocale.get("err_open_image", ShopLocale.activeLanguage) }

        val minSide = min(bmpW, bmpH)
        val cropSide = if (previewWidthPx > 0 && previewHeightPx > 0 && squareFrameSizePx > 0f) {
            // PreviewView uses ScaleType.FILL_CENTER: uniform scale = max(viewW / bmpW, viewH / bmpH)
            val previewScale = max(
                previewWidthPx.toFloat() / bmpW.toFloat(),
                previewHeightPx.toFloat() / bmpH.toFloat()
            )
            (squareFrameSizePx / previewScale).roundToInt().coerceIn(1, minSide)
        } else {
            minSide
        }

        val cropLeft = ((bmpW - cropSide) / 2f).roundToInt().coerceIn(0, bmpW - cropSide)
        val cropTop = ((bmpH - cropSide) / 2f).roundToInt().coerceIn(0, bmpH - cropSide)

        val cropped = Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropSide, cropSide)
        if (cropped != fullBitmap) {
            fullBitmap.recycle()
        }

        val compressedBytes = encodeScaledJpeg(cropped, maxDimensionPx, maxBytes)
        val dir = File(context.filesDir, "product_photos").apply { mkdirs() }
        val outFile = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        outFile.writeBytes(compressedBytes)
        return Uri.fromFile(outFile)
    }

    fun compressImageForUpload(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): ByteArray {
        val (sourceFile, shouldDeleteTemp) = materializeUriToReadableFile(context, uri)
        try {
            val bitmap = decodeFileOriented(sourceFile, maxDimensionPx)
            return encodeScaledJpeg(bitmap, maxDimensionPx, maxBytes)
        } finally {
            if (shouldDeleteTemp) {
                runCatching { sourceFile.delete() }
            }
        }
    }

    private fun materializeUriToReadableFile(context: Context, uri: Uri): Pair<File, Boolean> {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: throw IllegalArgumentException(ShopLocale.get("err_open_image", ShopLocale.activeLanguage))
            val file = File(path)
            if (!file.exists() || !file.canRead() || file.length() <= 0L) {
                throw IllegalArgumentException(ShopLocale.get("err_open_image", ShopLocale.activeLanguage))
            }
            return file to false
        }

        val resolver = context.contentResolver
        val tempDir = File(context.cacheDir, "product_photos").apply { mkdirs() }
        val tempFile = File(tempDir, "src_${System.currentTimeMillis()}.tmp")

        val copied = try {
            val inputStream = resolver.openInputStream(uri)
            if (inputStream != null) {
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } ?: false
            }
        } catch (_: Exception) {
            false
        }

        if (!copied || !tempFile.exists() || tempFile.length() <= 0L) {
            runCatching { tempFile.delete() }
            throw IllegalArgumentException(ShopLocale.get("err_open_image", ShopLocale.activeLanguage))
        }
        return tempFile to true
    }

    private fun decodeFileOriented(file: File, maxDimensionPx: Int): Bitmap {
        // On Android 9+ (API 28+), ImageDecoder natively decodes HEIC/HEIF, WebP, PNG, JPEG,
        // applies EXIF orientation automatically, and downsamples big photos during decode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedWithImageDecoder = try {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val rawW = info.size.width
                    val rawH = info.size.height
                    val longest = max(rawW, rawH)
                    if (longest > maxDimensionPx && rawW > 0 && rawH > 0) {
                        val scale = maxDimensionPx.toFloat() / longest.toFloat()
                        val targetW = max(1, (rawW * scale).roundToInt())
                        val targetH = max(1, (rawH * scale).roundToInt())
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (decodedWithImageDecoder != null &&
                decodedWithImageDecoder.width > 0 &&
                decodedWithImageDecoder.height > 0
            ) {
                return decodedWithImageDecoder
            }
        }

        // Fallback (API < 28 or Robolectric JVM environment)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val rawWidth = boundsOpts.outWidth
        val rawHeight = boundsOpts.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            throw IllegalArgumentException(ShopLocale.get("err_open_image", ShopLocale.activeLanguage))
        }

        val exifOrientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        var inSampleSize = 1
        val rawMax = max(rawWidth, rawHeight)
        while ((rawMax / (inSampleSize * 2)) >= maxDimensionPx) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var workingBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw IllegalArgumentException(ShopLocale.get("err_open_image", ShopLocale.activeLanguage))

        val matrix = Matrix()
        var needsTransform = true
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> needsTransform = false
        }

        if (needsTransform) {
            val transformed = Bitmap.createBitmap(
                workingBitmap,
                0,
                0,
                workingBitmap.width,
                workingBitmap.height,
                matrix,
                true
            )
            if (transformed != workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = transformed
            }
        }

        return workingBitmap
    }

    private fun encodeScaledJpeg(
        inputBitmap: Bitmap,
        maxDimensionPx: Int,
        maxBytes: Int
    ): ByteArray {
        var workingBitmap = inputBitmap

        // Scale proportionally so longest side <= maxDimensionPx (1000px)
        val longestSide = max(workingBitmap.width, workingBitmap.height)
        if (longestSide > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / longestSide.toFloat()
            val targetW = max(1, (workingBitmap.width * scale).roundToInt())
            val targetH = max(1, (workingBitmap.height * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(workingBitmap, targetW, targetH, true)
            if (scaled != workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = scaled
            }
        }

        // Compress to JPEG until within byte budget
        val baos = ByteArrayOutputStream()
        var quality = 85
        workingBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > maxBytes && quality > 35) {
            quality -= 10
            baos.reset()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }

        // Shrink dimensions slightly if still above budget
        var shrinkAttempts = 0
        while (baos.size() > maxBytes && shrinkAttempts < 3) {
            shrinkAttempts++
            val nextW = max(1, (workingBitmap.width * 0.82f).roundToInt())
            val nextH = max(1, (workingBitmap.height * 0.82f).roundToInt())
            val smaller = Bitmap.createScaledBitmap(workingBitmap, nextW, nextH, true)
            if (smaller != workingBitmap) {
                workingBitmap.recycle()
                workingBitmap = smaller
            }
            baos.reset()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 45, baos)
        }

        workingBitmap.recycle()
        return baos.toByteArray()
    }
}
