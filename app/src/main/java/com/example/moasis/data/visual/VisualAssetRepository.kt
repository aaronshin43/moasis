package com.example.moasis.data.visual

import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.domain.model.VisualAid

class VisualAssetRepository(
    private val protocolRepository: ProtocolRepository,
    private val assetCatalogDataSource: AssetCatalogDataSource,
) {
    fun getAssetsForStep(protocolId: String, stepId: String): List<VisualAid> {
        val step = protocolRepository.getProtocol(protocolId)
            ?.steps
            ?.firstOrNull { it.stepId == stepId }
            ?: return emptyList()

        val catalogById = assetCatalogDataSource.loadCatalog().associateBy { it.assetId }

        return step.assetRefs.map { assetRef ->
            val catalogEntry = catalogById[assetRef.assetId]
            VisualAid(
                assetId = assetRef.assetId,
                type = catalogEntry?.type ?: assetRef.type,
                caption = assetRef.caption ?: catalogEntry?.caption,
                contentDescription = catalogEntry?.contentDescription ?: assetRef.contentDescription,
                priority = assetRef.priority,
            )
        }
    }

    fun resolveAsset(assetId: String): AssetCatalogEntry? {
        return assetCatalogDataSource.loadCatalog().firstOrNull { it.assetId == assetId }
    }
}
