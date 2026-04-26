package com.example.moasis.ai.model

data class VisionDetectionResult(
    val objects: List<DetectedObject>,
    val rawObjects: List<DetectedObject> = objects,
)

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: NormalizedBoundingBox? = null,
)

data class NormalizedBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
