package com.example.moasis.ai.orchestrator

import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.domain.model.VisionTaskType

/**
 * Abstraction over the on-device vision detector (YOLO / YOLOE).
 *
 * Two implementations exist:
 *  - [com.example.moasis.ai.melange.MelangeYoloDetectionEngine]: classic resident
 *    engine that keeps the loaded YOLO session in memory between calls.
 *  - [com.example.moasis.ai.melange.ScopedKitDetectionEngine]: on-demand engine
 *    that loads the model only for the duration of a single [detect] call and
 *    releases it immediately. Used when the always-resident LLM + embedding
 *    stack would otherwise overflow the Zetic NPU memory budget.
 */
interface VisionDetectionEngine {
    /**
     * Whether the engine has the configuration it needs to run.
     * Cheap, non-suspending — safe to call from UI status code.
     */
    fun isConfigured(): Boolean = true

    /**
     * Whether a loaded model session currently lives in memory.
     * Always `false` for on-demand implementations.
     */
    fun isPreparedInMemory(): Boolean = false

    /**
     * Optionally warm the engine. On-demand implementations may treat this
     * as a config-only check and skip the actual load.
     */
    suspend fun prepareIfNeeded(): Result<Unit>

    suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult>
}
