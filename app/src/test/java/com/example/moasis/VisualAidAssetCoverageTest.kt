package com.example.moasis

import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAidAssetCoverageTest {
    private val projectRoot = findProjectRoot()
    private val assetRoot = File(projectRoot, "app/src/main/assets")
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
    private val drawableRoot = File(projectRoot, "app/src/main/res/drawable")
    private val drawableNoDpiRoot = File(projectRoot, "app/src/main/res/drawable-nodpi")

    @Test
    fun curated_steps_expose_expected_visual_assets() {
        val expected = mapOf(
            StepKey("anaphylaxis_general", "help_use_epinephrine") to listOf(
                "anaphylaxis_epinephrine_device_01",
                "anaphylaxis_epinephrine_outer_thigh_01",
                "anaphylaxis_epinephrine_press_hold_01",
            ),
            StepKey("bleeding_external_general", "apply_direct_pressure") to listOf(
                "bleeding_apply_direct_pressure_01",
            ),
            StepKey("bleeding_external_general", "do_not_remove_embedded_object") to listOf(
                "bleeding_embedded_object_stabilize_01",
            ),
            StepKey("bleeding_minor_general", "rinse_and_check") to listOf(
                "minor_wound_rinse_01",
            ),
            StepKey("breathing_problem_general", "help_with_prescribed_inhaler") to listOf(
                "breathing_prescribed_inhaler_use_01",
                "breathing_prescribed_inhaler_spacer_01",
            ),
            StepKey("burn_minor_general", "cover_lightly") to listOf(
                "burn_cover_clean_dressing_01",
            ),
            StepKey("burn_second_degree_general", "cover_clean") to listOf(
                "burn_cover_clean_dressing_01",
            ),
            StepKey("nosebleed_general", "pinch_soft_nose") to listOf(
                "nosebleed_pinch_soft_nose_01",
            ),
            StepKey("cardiac_arrest_general", "hand_position") to listOf(
                "cardiac_cpr_hand_position_01",
            ),
            StepKey("cardiac_arrest_general", "use_aed_when_available") to listOf(
                "cardiac_aed_power_on_01",
                "cardiac_aed_attach_pad_adult_01",
                "cardiac_aed_attach_pad_child_01",
            ),
            StepKey("choking_general", "five_back_blows") to listOf(
                "choking_back_blow_01",
            ),
            StepKey("choking_general", "five_abdominal_thrusts") to listOf(
                "choking_abdominal_thrust_hand_position_01",
                "choking_abdominal_thrust_01",
            ),
            StepKey("choking_general", "if_unresponsive_start_cpr") to listOf(
                "choking_unresponsive_chest_compressions_01",
            ),
            StepKey("drowning_general", "rescue_without_entering_water") to listOf(
                "drowning_reach_rescue_01",
                "drowning_throw_rescue_01",
                "drowning_row_rescue_01",
            ),
            StepKey("general_assessment_general", "check_airway_breathing") to listOf(
                "general_assessment_open_airway_01",
            ),
            StepKey("head_injury_general", "control_bleeding_and_cold_pack") to listOf(
                "head_injury_scalp_pressure_01",
            ),
            StepKey("head_injury_general", "do_not_move_if_neck_suspected") to listOf(
                "head_injury_support_head_in_place_01",
                "head_injury_support_head_with_helmet_01",
            ),
            StepKey("fracture_suspected_general", "immobilize_joint_above_and_below") to listOf(
                "fracture_forearm_improvised_splint_01",
                "fracture_leg_padded_splint_01",
                "fracture_lower_leg_splint_reference_01",
            ),
            StepKey("heat_stroke_general", "cool_aggressively") to listOf(
                "heat_stroke_cooling_01",
            ),
            StepKey("hypothermia_general", "wrap_in_blankets") to listOf(
                "hypothermia_wrap_blankets_01",
                "hypothermia_sleeping_bag_insulation_01",
            ),
            StepKey("hypothermia_mild_general", "warm_core_first") to listOf(
                "hypothermia_warm_core_01",
            ),
            StepKey("seizure_general", "cushion_head") to listOf(
                "seizure_cushion_head_01",
            ),
            StepKey("stroke_fast_general", "check_fast_signs") to listOf(
                "stroke_fast_face_droop_01",
                "stroke_fast_arm_drift_01",
            ),
            StepKey("unresponsive_breathing_general", "confirm_breathing") to listOf(
                "unresponsive_check_breathing_01",
            ),
        )

        expected.forEach { (stepKey, expectedAssetIds) ->
            val actualAssetIds = visualAssetRepository.getAssetsForStep(
                protocolId = stepKey.protocolId,
                stepId = stepKey.stepId,
            ).map { it.assetId }

            assertEquals(
                "Unexpected asset ids for ${stepKey.protocolId}/${stepKey.stepId}",
                expectedAssetIds,
                actualAssetIds,
            )
        }
    }

    @Test
    fun curated_visual_assets_exist_in_catalog_and_resources() {
        val curatedAssetIds = listOf(
            "anaphylaxis_epinephrine_device_01",
            "anaphylaxis_epinephrine_outer_thigh_01",
            "anaphylaxis_epinephrine_press_hold_01",
            "bleeding_apply_direct_pressure_01",
            "bleeding_embedded_object_stabilize_01",
            "minor_wound_rinse_01",
            "breathing_prescribed_inhaler_use_01",
            "breathing_prescribed_inhaler_spacer_01",
            "burn_cover_clean_dressing_01",
            "nosebleed_pinch_soft_nose_01",
            "cardiac_cpr_hand_position_01",
            "cardiac_aed_power_on_01",
            "cardiac_aed_attach_pad_adult_01",
            "cardiac_aed_attach_pad_child_01",
            "choking_back_blow_01",
            "choking_abdominal_thrust_hand_position_01",
            "choking_abdominal_thrust_01",
            "choking_unresponsive_chest_compressions_01",
            "drowning_reach_rescue_01",
            "drowning_throw_rescue_01",
            "drowning_row_rescue_01",
            "general_assessment_open_airway_01",
            "head_injury_scalp_pressure_01",
            "head_injury_support_head_in_place_01",
            "head_injury_support_head_with_helmet_01",
            "fracture_forearm_improvised_splint_01",
            "fracture_leg_padded_splint_01",
            "fracture_lower_leg_splint_reference_01",
            "heat_stroke_cooling_01",
            "hypothermia_wrap_blankets_01",
            "hypothermia_sleeping_bag_insulation_01",
            "hypothermia_warm_core_01",
            "seizure_cushion_head_01",
            "stroke_fast_face_droop_01",
            "stroke_fast_arm_drift_01",
            "unresponsive_check_breathing_01",
        )

        curatedAssetIds.forEach { assetId ->
            assertTrue("Missing catalog entry for $assetId", visualAssetRepository.resolveAsset(assetId) != null)
            assertTrue("Missing drawable resource for $assetId", assetResourceExists(assetId))
        }
    }

    private fun assetResourceExists(assetId: String): Boolean {
        return File(drawableNoDpiRoot, "$assetId.png").exists() ||
            File(drawableRoot, "$assetId.xml").exists() ||
            File(drawableRoot, "$assetId.png").exists()
    }

    private fun findProjectRoot(): File {
        var current = File(".").absoluteFile
        repeat(6) {
            val candidate = File(current, "app/src/main/assets")
            if (candidate.exists()) {
                return current
            }
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate project root from test working directory.")
    }

    private data class StepKey(
        val protocolId: String,
        val stepId: String,
    )
}
