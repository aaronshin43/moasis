package com.example.moasis.ai.melange

import android.util.Log

object MelangeInitCoordinator {
    private val lock = Any()

    fun <T> runExclusive(modelType: String, modelName: String, block: () -> T): T {
        Log.d(TAG, "Waiting for exclusive init type=$modelType model=$modelName")
        synchronized(lock) {
            Log.d(TAG, "Acquired exclusive init type=$modelType model=$modelName")
            return try {
                block()
            } finally {
                Log.d(TAG, "Released exclusive init type=$modelType model=$modelName")
            }
        }
    }

    private const val TAG = "MelangeInitCoordinator"
}
