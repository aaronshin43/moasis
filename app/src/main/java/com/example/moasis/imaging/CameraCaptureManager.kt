package com.example.moasis.imaging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class CameraCaptureManager(
    private val context: Context,
) {
    private var pendingCaptureFile: File? = null

    fun createCaptureUri(): Uri {
        val file = File(context.cacheDir, "camera-capture-${UUID.randomUUID()}.jpg")
        pendingCaptureFile = file
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun getPendingCaptureUri(): Uri? {
        val file = pendingCaptureFile ?: return null
        return Uri.fromFile(file)
    }

    fun clearPendingCapture() {
        pendingCaptureFile = null
    }
}
