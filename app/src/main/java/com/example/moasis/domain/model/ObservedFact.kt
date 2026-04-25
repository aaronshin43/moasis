package com.example.moasis.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ObservedFact(
    val key: String,
    val value: String,
    val confidence: Float,
    val source: FactSource,
    val evidence: String? = null,
)

@Serializable
enum class FactSource {
    USER_REPORTED,
    USER_CONFIRMED,
    VISION_SUGGESTED,
    SYSTEM_INFERRED,
}

@Serializable
enum class VisionTaskType {
    KIT_DETECTION,
    STEP_VERIFICATION,
    INJURY_OBSERVATION,
    GENERAL_MULTIMODAL_QA,
    UNKNOWN,
}

@Serializable
data class TurnContext(
    val dialogueState: DialogueState? = null,
    val currentProtocolId: String? = null,
    val currentStepId: String? = null,
    val lastAssistantAction: String? = null,
    val expectedVisualCheck: String? = null,
)
