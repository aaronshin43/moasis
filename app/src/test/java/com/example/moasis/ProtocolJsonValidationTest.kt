package com.example.moasis

import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolJsonValidationTest {
    private val assetRoot = findAssetRoot()
    private val protocolRepository = ProtocolRepository(
        dataSource = JsonProtocolDataSource(
            assetTextSource = FileSystemAssetTextSource(assetRoot),
        ),
    )

    // --- All trees load ---

    private val assetIds = assetRoot.resolve("protocols")
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.map { it.nameWithoutExtension }
        ?.sorted()
        ?: error("No protocol JSON files were found under ${assetRoot.resolve("protocols")}")

    private val expectedTrees = assetIds.filter { id ->
        id.endsWith("_tree") || id == "collapsed_person_entry"
    }

    private val expectedProtocols = assetIds.filterNot { id -> id in expectedTrees }

    @Test
    fun all_expected_trees_load_successfully() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId)
            assertNotNull("Tree '$treeId' failed to load", tree)
        }
    }

    @Test
    fun all_expected_protocols_load_successfully() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId)
            assertNotNull("Protocol '$protocolId' failed to load", protocol)
        }
    }

    // --- Tree structural integrity ---

    @Test
    fun every_tree_has_a_valid_start_node() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            val nodeIds = tree.nodes.map { it.id }.toSet()
            assertTrue(
                "Tree '$treeId': startNode '${tree.startNode}' not found in nodes $nodeIds",
                tree.startNode in nodeIds,
            )
        }
    }

    @Test
    fun every_tree_node_has_a_non_empty_id() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            for (node in tree.nodes) {
                assertTrue(
                    "Tree '$treeId': node has empty id",
                    node.id.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun every_tree_has_at_least_one_node() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            assertTrue(
                "Tree '$treeId' has no nodes",
                tree.nodes.isNotEmpty(),
            )
        }
    }

    @Test
    fun question_nodes_have_transitions() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            for (node in tree.nodes) {
                if (node.type == "question") {
                    assertTrue(
                        "Tree '$treeId', question node '${node.id}' has no transitions",
                        node.transitions.isNotEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun router_nodes_have_routes() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            for (node in tree.nodes) {
                if (node.type == "router") {
                    assertTrue(
                        "Tree '$treeId', router node '${node.id}' has no routes",
                        node.routes.isNotEmpty(),
                    )
                }
            }
        }
    }

    // --- Protocol structural integrity ---

    @Test
    fun every_protocol_has_at_least_one_step() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            assertTrue(
                "Protocol '$protocolId' has no steps",
                protocol.steps.isNotEmpty(),
            )
        }
    }

    @Test
    fun every_step_has_non_empty_canonical_text() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            for (step in protocol.steps) {
                assertTrue(
                    "Protocol '$protocolId', step '${step.stepId}' has empty canonicalText",
                    step.canonicalText.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun every_step_has_non_empty_step_id() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            for (step in protocol.steps) {
                assertTrue(
                    "Protocol '$protocolId' has step with empty stepId",
                    step.stepId.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun every_protocol_has_non_empty_title() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            assertTrue(
                "Protocol '$protocolId' has empty title",
                protocol.title.isNotBlank(),
            )
        }
    }

    @Test
    fun step_ids_are_unique_within_each_protocol() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            val ids = protocol.steps.map { it.stepId }
            assertEquals(
                "Protocol '$protocolId' has duplicate step IDs: $ids",
                ids.size,
                ids.toSet().size,
            )
        }
    }

    // --- Safety: must_keep_keywords / forbidden_keywords ---

    @Test
    fun burn_cool_water_step_has_must_keep_keywords() {
        val protocol = protocolRepository.getProtocol("burn_second_degree_general")!!
        val coolWater = protocol.steps.first { it.stepId == "cool_water" }
        assertTrue(
            "cool_water step should have must_keep_keywords",
            coolWater.mustKeepKeywords.isNotEmpty(),
        )
    }

    @Test
    fun burn_cool_water_step_has_forbidden_keywords() {
        val protocol = protocolRepository.getProtocol("burn_second_degree_general")!!
        val coolWater = protocol.steps.first { it.stepId == "cool_water" }
        assertTrue(
            "cool_water step should have forbidden_keywords",
            coolWater.forbiddenKeywords.isNotEmpty(),
        )
    }

    @Test
    fun canonical_text_contains_its_own_must_keep_keywords() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            for (step in protocol.steps) {
                val lower = step.canonicalText.lowercase()
                for (keyword in step.mustKeepKeywords) {
                    assertTrue(
                        "Protocol '$protocolId', step '${step.stepId}': " +
                            "canonicalText does not contain must_keep_keyword '$keyword'",
                        lower.contains(keyword.lowercase()),
                    )
                }
            }
        }
    }

    @Test
    fun canonical_text_does_not_contain_its_own_forbidden_keywords() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            for (step in protocol.steps) {
                val lower = step.canonicalText.lowercase()
                for (keyword in step.forbiddenKeywords) {
                    assertFalse(
                        "Protocol '$protocolId', step '${step.stepId}': " +
                            "canonicalText contains forbidden_keyword '$keyword'",
                        lower.contains(keyword.lowercase()),
                    )
                }
            }
        }
    }

    // --- Missing data returns null ---

    @Test
    fun missing_tree_returns_null() {
        val tree = protocolRepository.getTree("nonexistent_tree")
        assertTrue("Missing tree should return null", tree == null)
    }

    @Test
    fun missing_protocol_returns_null() {
        val protocol = protocolRepository.getProtocol("nonexistent_protocol")
        assertTrue("Missing protocol should return null", protocol == null)
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
