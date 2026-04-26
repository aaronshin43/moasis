package com.example.moasis.ai.melange

data class MelangeRuntimeConfig(
    val personalKey: String,
    val modelName: String,
    val modelVersion: Int? = null,
    val modelModeName: String = "RUN_AUTO",
    val targetName: String = "LLAMA_CPP",
    val quantTypeName: String = "GGUF_QUANT_Q4_K_M",
    val apTypeName: String = "AUTO",
) {
    val isConfigured: Boolean
        get() = personalKey.isNotBlank() && modelName.isNotBlank()
}
