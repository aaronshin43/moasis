package com.example.moasis.ai.melange

data class MelangeEmbeddingRuntimeConfig(
    val personalKey: String,
    val modelName: String,
    val modelVersion: Int? = null,
    val modelModeName: String = "RUN_AUTO",
) {
    val isConfigured: Boolean
        get() = personalKey.isNotBlank() && modelName.isNotBlank()
}
