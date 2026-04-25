package com.example.moasis.imaging

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class ImageInputController(
    private val context: Context,
) {
    fun copyToInternalCache(sourceUri: Uri): Result<String> {
        return runCatching {
            val targetFile = File(context.cacheDir, "attached-image-${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri).useRequired { inputStream ->
                FileOutputStream(targetFile).use { output ->
                    inputStream.copyTo(output)
                }
            }
            targetFile.absolutePath
        }
    }

    private inline fun <T : AutoCloseable?, R> T?.useRequired(block: (T) -> R): R {
        val resource = requireNotNull(this) { "Unable to open image stream." }
        return resource.use(block)
    }
}
