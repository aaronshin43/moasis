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
    private val supportedSlotKeys = setOf(
        "location",
        "patient_type",
        "response",
        "cardiac_arrest_confirmed",
        "burn_severity",
        "burn_emergency_red_flags",
        "can_cough_or_speak",
        "breathing_red_flags",
        "seizure_active_now",
        "can_swallow_safely",
        "poison_contact_exposure",
        "poison_inhalation_exposure",
        "has_massive_bleeding",
        "has_choking_signs",
        "has_seizure_signs",
        "has_chest_pain",
        "has_stroke_signs",
        "has_anaphylaxis_signs",
        "has_hypoglycemia_signs",
        "has_breathing_problem",
        "scene_safe",
        "responsive",
        "breathing_normal",
    )

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

    @Test
    fun every_protocol_is_referenced_by_at_least_one_tree_instruction() {
        val referencedProtocolIds = expectedTrees
            .mapNotNull { protocolRepository.getTree(it) }
            .flatMap { tree -> tree.nodes.mapNotNull { it.instructionId } }
            .toSet()

        for (protocolId in expectedProtocols) {
            assertTrue(
                "Protocol '$protocolId' is not referenced by any tree instruction node",
                protocolId in referencedProtocolIds,
            )
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

    @Test
    fun question_nodes_use_supported_slot_keys() {
        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            for (node in tree.nodes) {
                if (node.type == "question") {
                    assertTrue(
                        "Tree '$treeId', question node '${node.id}' uses unsupported slot key '${node.slotKey}'",
                        !node.slotKey.isNullOrBlank() && node.slotKey in supportedSlotKeys,
                    )
                }
            }
        }
    }

    @Test
    fun in_tree_node_references_resolve_to_existing_nodes_or_trees() {
        val knownTrees = expectedTrees.toSet()
        val knownProtocols = expectedProtocols.toSet()

        for (treeId in expectedTrees) {
            val tree = protocolRepository.getTree(treeId) ?: continue
            val nodeIds = tree.nodes.map { it.id }.toSet()

            for (node in tree.nodes) {
                node.next?.let { nextNodeId ->
                    assertTrue(
                        "Tree '$treeId', node '${node.id}' has next='$nextNodeId' which does not exist",
                        nextNodeId in nodeIds,
                    )
                }

                node.instructionId?.let { protocolId ->
                    assertTrue(
                        "Tree '$treeId', node '${node.id}' references missing protocol '$protocolId'",
                        protocolId in knownProtocols,
                    )
                }

                node.fallbackTo?.let { fallback ->
                    assertTrue(
                        "Tree '$treeId', node '${node.id}' has fallback_to='$fallback' which is neither a node nor a tree",
                        fallback in nodeIds || fallback in knownTrees,
                    )
                }

                for (transition in node.transitions) {
                    transition.to?.let { nextNodeId ->
                        assertTrue(
                            "Tree '$treeId', node '${node.id}' has transition to missing node '$nextNodeId'",
                            nextNodeId in nodeIds,
                        )
                    }
                    transition.toTree?.let { nextTreeId ->
                        assertTrue(
                            "Tree '$treeId', node '${node.id}' has transition to missing tree '$nextTreeId'",
                            nextTreeId in knownTrees,
                        )
                    }
                }

                for (route in node.routes) {
                    route.to?.let { nextNodeId ->
                        assertTrue(
                            "Tree '$treeId', node '${node.id}' has route to missing node '$nextNodeId'",
                            nextNodeId in nodeIds,
                        )
                    }
                    route.toTree?.let { nextTreeId ->
                        assertTrue(
                            "Tree '$treeId', node '${node.id}' has route to missing tree '$nextTreeId'",
                            nextTreeId in knownTrees,
                        )
                    }
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
    fun required_slots_use_known_supported_slot_keys() {
        for (protocolId in expectedProtocols) {
            val protocol = protocolRepository.getProtocol(protocolId) ?: continue
            for (requiredSlot in protocol.requiredSlots) {
                assertTrue(
                    "Protocol '$protocolId' requires unsupported slot '$requiredSlot'",
                    requiredSlot in supportedSlotKeys,
                )
            }
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
