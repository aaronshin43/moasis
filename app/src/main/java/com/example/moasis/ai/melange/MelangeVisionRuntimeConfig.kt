package com.example.moasis.ai.melange

data class MelangeVisionRuntimeConfig(
    val personalKey: String,
    val modelName: String,
    val modelVersion: Int? = null,
    val modelModeName: String = "RUN_AUTO",
    val quantTypeName: String? = null,
    val targetName: String? = null,
    val apTypeName: String? = null,
) {
    val isConfigured: Boolean
        get() = personalKey.isNotBlank() && modelName.isNotBlank()
}
