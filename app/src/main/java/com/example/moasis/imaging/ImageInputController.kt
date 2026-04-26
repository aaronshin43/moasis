package com.example.moasis.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageInputController(
    private val context: Context,
) {
    fun copyToInternalCache(sourceUri: Uri): Result<String> {
        return runCatching {
            val targetFile = File(context.cacheDir, "attached-image-${UUID.randomUUID()}.jpg")
            val normalizedBitmap = decodeNormalizedBitmap(sourceUri)
            FileOutputStream(targetFile).use { output ->
                normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            targetFile.absolutePath
        }
    }

    private fun decodeNormalizedBitmap(sourceUri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri).useRequired { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(sourceUri).useRequired { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        } ?: error("Unable to decode image data.")

        val orientation = context.contentResolver.openInputStream(sourceUri).useRequired { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }

        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it != bitmap) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth > maxDimension || sampledHeight > maxDimension) {
            sampleSize *= 2
            sampledWidth = width / sampleSize
            sampledHeight = height / sampleSize
        }
        return sampleSize.coerceAtLeast(1)
    }

    private inline fun <T : AutoCloseable?, R> T?.useRequired(block: (T) -> R): R {
        val resource = requireNotNull(this) { "Unable to open image stream." }
        return resource.use(block)
    }

    companion object {
        private const val MAX_DECODE_DIMENSION = 2048
    }
}
