package com.example.moasis.ai.model

interface OnDeviceLlmEngine {
    fun generate(request: LlmRequest): Result<LlmResponse>
}
