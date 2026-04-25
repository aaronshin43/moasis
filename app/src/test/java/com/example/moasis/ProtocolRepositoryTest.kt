package com.example.moasis

import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolRepositoryTest {
    private val assetRoot = findAssetRoot()
    private val protocolRepository = ProtocolRepository(
        dataSource = JsonProtocolDataSource(
            assetTextSource = FileSystemAssetTextSource(assetRoot),
        ),
    )
    private val visualAssetRepository = VisualAssetRepository(
        protocolRepository = protocolRepository,
        assetCatalogDataSource = AssetCatalogDataSource(
            assetTextSource = FileSystemAssetTextSource(assetRoot),
        ),
    )

    @Test
    fun burn_protocol_exposes_multiple_steps_from_current_fixture() {
        val protocol = protocolRepository.getProtocol("burn_second_degree_general")

        assertNotNull(protocol)
        assertTrue((protocol?.steps?.size ?: 0) >= 2)
        assertEquals("stop_burning_source", protocol?.steps?.firstOrNull()?.stepId)
    }

    @Test
    fun cool_water_step_has_at_least_one_visual_asset() {
        val assets = visualAssetRepository.getAssetsForStep(
            protocolId = "burn_second_degree_general",
            stepId = "cool_water",
        )

        assertTrue(assets.isNotEmpty())
    }

    @Test
    fun missing_protocol_returns_null() {
        val protocol = protocolRepository.getProtocol("missing_protocol")

        assertNull(protocol)
    }

    @Test
    fun collapsed_person_entry_tree_is_available() {
        val tree = protocolRepository.getTree("collapsed_person_entry")

        assertNotNull(tree)
        assertEquals("scene_safe", tree?.startNode)
    }

    private fun findAssetRoot(): File {
        var current = File(".").absoluteFile
        repeat(6) {
            val candidate = File(current, "app/src/main/assets")
            if (candidate.exists()) {
                return candidate
            }
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate app/src/main/assets from test working directory.")
    }
}
