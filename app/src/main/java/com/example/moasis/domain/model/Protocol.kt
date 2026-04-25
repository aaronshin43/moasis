package com.example.moasis.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Protocol(
    @SerialName("protocol_id")
    val protocolId: String,
    val title: String,
    val category: String,
    @SerialName("required_slots")
    val requiredSlots: List<String> = emptyList(),
    @SerialName("safety_flags")
    val safetyFlags: List<String> = emptyList(),
    val steps: List<ProtocolStep> = emptyList(),
)

@Serializable
data class ProtocolStep(
    @SerialName("step_id")
    val stepId: String,
    @SerialName("canonical_text")
    val canonicalText: String,
    @SerialName("must_keep_keywords")
    val mustKeepKeywords: List<String> = emptyList(),
    @SerialName("forbidden_keywords")
    val forbiddenKeywords: List<String> = emptyList(),
    @SerialName("asset_refs")
    val assetRefs: List<AssetRef> = emptyList(),
)

@Serializable
data class AssetRef(
    @SerialName("asset_id")
    val assetId: String,
    val type: VisualAidType,
    val caption: String? = null,
    @SerialName("content_description")
    val contentDescription: String,
    val priority: Int = 0,
)
