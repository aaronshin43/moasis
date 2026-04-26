package com.example.moasis.ai.orchestrator

import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.domain.model.VisionTaskType

interface VisionDetectionEngine {
    suspend fun prepareIfNeeded(): Result<Unit>

    suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult>
}
