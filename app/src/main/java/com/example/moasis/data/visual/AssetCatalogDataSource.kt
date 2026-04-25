package com.example.moasis.data.visual

import com.example.moasis.data.protocol.AssetTextSource
import com.example.moasis.domain.model.VisualAidType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AssetCatalogDataSource(
    private val assetTextSource: AssetTextSource,
    private val json: Json = defaultJson,
) {
    fun loadCatalog(): List<AssetCatalogEntry> {
        val content = assetTextSource.read(ASSET_CATALOG_PATH)
        return json.decodeFromString(ListSerializer(AssetCatalogEntry.serializer()), content)
    }

    companion object {
        const val ASSET_CATALOG_PATH = "visuals/asset_catalog.json"

        val defaultJson = Json {
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
data class AssetCatalogEntry(
    @SerialName("asset_id")
    val assetId: String,
    @SerialName("file_name")
    val fileName: String,
    val type: VisualAidType,
    val tags: List<String> = emptyList(),
    @SerialName("usage_scope")
    val usageScope: List<String> = emptyList(),
    val caption: String? = null,
    @SerialName("content_description")
    val contentDescription: String,
    val variants: Map<String, String> = emptyMap(),
)
