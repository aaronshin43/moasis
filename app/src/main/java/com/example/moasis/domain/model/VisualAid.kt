package com.example.moasis.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisualAid(
    val assetId: String,
    val type: VisualAidType,
    val caption: String? = null,
    val contentDescription: String,
    val priority: Int = 0,
)

@Serializable
enum class VisualAidType {
    @SerialName("image")
    IMAGE,

    @SerialName("warning_illustration")
    WARNING_ILLUSTRATION,

    @SerialName("diagram")
    DIAGRAM,
}
