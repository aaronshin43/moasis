#!/usr/bin/env python3
"""Standalone verifier for curated visual aid wiring."""

from __future__ import annotations

import json
import sys
from pathlib import Path


EXPECTED_STEP_ASSETS = {
    ("anaphylaxis_general", "help_use_epinephrine"): [
        "anaphylaxis_epinephrine_device_01",
        "anaphylaxis_epinephrine_outer_thigh_01",
        "anaphylaxis_epinephrine_press_hold_01",
    ],
    ("bleeding_external_general", "apply_direct_pressure"): [
        "bleeding_apply_direct_pressure_01",
    ],
    ("bleeding_external_general", "do_not_remove_embedded_object"): [
        "bleeding_embedded_object_stabilize_01",
    ],
    ("bleeding_minor_general", "rinse_and_check"): [
        "minor_wound_rinse_01",
    ],
    ("breathing_problem_general", "help_with_prescribed_inhaler"): [
        "breathing_prescribed_inhaler_use_01",
        "breathing_prescribed_inhaler_spacer_01",
    ],
    ("burn_minor_general", "cover_lightly"): [
        "burn_cover_clean_dressing_01",
    ],
    ("burn_second_degree_general", "cover_clean"): [
        "burn_cover_clean_dressing_01",
    ],
    ("nosebleed_general", "pinch_soft_nose"): [
        "nosebleed_pinch_soft_nose_01",
    ],
    ("cardiac_arrest_general", "hand_position"): [
        "cardiac_cpr_hand_position_01",
    ],
    ("cardiac_arrest_general", "use_aed_when_available"): [
        "cardiac_aed_power_on_01",
        "cardiac_aed_attach_pad_adult_01",
        "cardiac_aed_attach_pad_child_01",
    ],
    ("choking_general", "five_back_blows"): [
        "choking_back_blow_01",
    ],
    ("choking_general", "five_abdominal_thrusts"): [
        "choking_abdominal_thrust_hand_position_01",
        "choking_abdominal_thrust_01",
    ],
    ("choking_general", "if_unresponsive_start_cpr"): [
        "choking_unresponsive_chest_compressions_01",
    ],
    ("drowning_general", "rescue_without_entering_water"): [
        "drowning_reach_rescue_01",
        "drowning_throw_rescue_01",
        "drowning_row_rescue_01",
    ],
    ("fracture_suspected_general", "immobilize_joint_above_and_below"): [
        "fracture_forearm_improvised_splint_01",
        "fracture_leg_padded_splint_01",
        "fracture_lower_leg_splint_reference_01",
    ],
    ("heat_stroke_general", "cool_aggressively"): [
        "heat_stroke_cooling_01",
    ],
    ("general_assessment_general", "check_airway_breathing"): [
        "general_assessment_open_airway_01",
    ],
    ("head_injury_general", "control_bleeding_and_cold_pack"): [
        "head_injury_scalp_pressure_01",
    ],
    ("head_injury_general", "do_not_move_if_neck_suspected"): [
        "head_injury_support_head_in_place_01",
        "head_injury_support_head_with_helmet_01",
    ],
    ("hypothermia_general", "wrap_in_blankets"): [
        "hypothermia_wrap_blankets_01",
        "hypothermia_sleeping_bag_insulation_01",
    ],
    ("hypothermia_mild_general", "warm_core_first"): [
        "hypothermia_warm_core_01",
    ],
    ("seizure_general", "cushion_head"): [
        "seizure_cushion_head_01",
    ],
    ("stroke_fast_general", "check_fast_signs"): [
        "stroke_fast_face_droop_01",
        "stroke_fast_arm_drift_01",
    ],
    ("unresponsive_breathing_general", "confirm_breathing"): [
        "unresponsive_check_breathing_01",
    ],
}


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    protocols_dir = root / "app/src/main/assets/protocols"
    catalog_path = root / "app/src/main/assets/visuals/asset_catalog.json"
    drawable_dir = root / "app/src/main/res/drawable"
    drawable_nodpi_dir = root / "app/src/main/res/drawable-nodpi"

    catalog = json.loads(catalog_path.read_text())
    catalog_ids = {entry["asset_id"] for entry in catalog}

    protocols = {}
    for path in protocols_dir.glob("*.json"):
        payload = json.loads(path.read_text())
        protocol_id = payload.get("protocol_id")
        if protocol_id:
            protocols[protocol_id] = payload

    failures: list[str] = []

    for (protocol_id, step_id), expected_assets in EXPECTED_STEP_ASSETS.items():
        protocol = protocols.get(protocol_id)
        if not protocol:
            failures.append(f"Missing protocol: {protocol_id}")
            continue

        step = next((item for item in protocol.get("steps", []) if item["step_id"] == step_id), None)
        if not step:
            failures.append(f"Missing step: {protocol_id}/{step_id}")
            continue

        actual_assets = [ref["asset_id"] for ref in step.get("asset_refs", [])]
        if actual_assets != expected_assets:
            failures.append(
                f"{protocol_id}/{step_id} asset mismatch\n"
                f"  expected: {expected_assets}\n"
                f"  actual:   {actual_assets}"
            )

        for asset_id in actual_assets:
            if asset_id not in catalog_ids:
                failures.append(f"Missing catalog entry for {asset_id}")
            resource_exists = (
                (drawable_nodpi_dir / f"{asset_id}.png").exists()
                or (drawable_dir / f"{asset_id}.xml").exists()
                or (drawable_dir / f"{asset_id}.png").exists()
            )
            if not resource_exists:
                failures.append(f"Missing resource file for {asset_id}")

    if failures:
        print("Visual aid verification failed:\n")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Visual aid verification passed.")
    print(f"Checked {len(EXPECTED_STEP_ASSETS)} protocol steps.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
