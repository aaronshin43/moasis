#!/usr/bin/env python3
"""Build a deduplicated inventory and draft mapping for manual image assets."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
import zipfile
from collections import Counter, OrderedDict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


SOURCE_MANUAL_URL = "https://sbbchidaho.org/PDF/FirstAidManual.pdf"
DEFAULT_ZIP_PATH = Path("/Users/johnlee/Downloads/drive-download-20260425T223841Z-3-001.zip")
DEFAULT_OUTPUT_DIR = Path("/Users/johnlee/AndroidStudioProjects/moasis/docs/manual_asset_import")
PROTOCOLS_DIR = Path("/Users/johnlee/AndroidStudioProjects/moasis/app/src/main/assets/protocols")


SECTION_SLUG_OVERRIDES = {
    "AED—Adult and Child": "aed_adult_child",
    "CHECK the Patient": "check_patient",
    "CPR—Adult and Child": "cpr_adult_child",
    "Checking an Unconscious Person": "checking_unconscious_person",
    "Conscious Choking—Adult and Child": "conscious_choking_adult_child",
    "Flail Chest": "flail_chest",
    "Glove Removal": "glove_removal",
    "High Altitude Cerebral Edema": "high_altitude_cerebral_edema",
    "Non-Life-Threatening Allergic Reactions": "non_life_threatening_allergic_reactions",
    "Rib Injuries": "rib_injuries",
    "Serious Brain Injuries": "serious_brain_injuries",
    "Splinting": "splinting",
    "Sucking Chest Wound": "sucking_chest_wound",
    "Superficial Scalp Injuries": "superficial_scalp_injuries",
    "Unconscious Choking—Adult and Child": "unconscious_choking_adult_child",
    "Wilderness and Remote First Aid Kits": "wilderness_remote_first_aid_kits",
    "activedrowning": "active_drowning",
    "asthmaattack": "asthma_attack",
    "blanketdrag": "blanket_drag",
    "closedabdominalinjuries": "closed_abdominal_injuries",
    "clothesdrag": "clothes_drag",
    "disstressedswimmer": "distressed_swimmer",
    "footdrag": "foot_drag",
    "frictionblisters": "friction_blisters",
    "heartattack": "heart_attack",
    "heatstroke": "heat_stroke",
    "immersionfoot": "immersion_foot",
    "impaledobjects": "impaled_objects",
    "logroll": "log_roll",
    "mildhypothermia": "mild_hypothermia",
    "nosebleeds": "nosebleeds",
    "openabdominalinjuries": "open_abdominal_injuries",
    "packstrapcarry": "pack_strap_carry",
    "passivedrowning": "passive_drowning",
    "poisoning": "poisoning",
    "reachthrowrowgo": "reach_throw_row_go",
    "severehypothermia": "severe_hypothermia",
    "spinalinjuries": "spinal_injuries",
    "tourniquets": "tourniquets",
    "twopersonseatcarry": "two_person_seat_carry",
    "walkingassist": "walking_assist",
    "woundcleaning": "wound_cleaning",
    "wounddressing": "wound_dressing",
}


@dataclass(frozen=True)
class MappingDraft:
    asset_id: str
    resource_name: str
    asset_role: str
    proposed_protocol_id: str
    proposed_step_id: str
    confidence: str
    review_needed: bool
    status: str
    caption: str
    content_description: str
    notes: str


@dataclass
class InventoryRow:
    original_filename: str
    canonical_filename: str
    section: str
    section_slug: str
    source_page: str
    source_figure: str
    duplicate_count: int
    duplicate_variants: str
    source_manual_url: str
    asset_id: str
    resource_name: str
    asset_role: str
    proposed_protocol_id: str
    proposed_step_id: str
    confidence: str
    review_needed: str
    status: str
    caption: str
    content_description: str
    notes: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip-path", type=Path, default=DEFAULT_ZIP_PATH)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    return parser.parse_args()


def normalize_basename(name: str) -> str:
    base = name.rsplit("/", 1)[-1]
    base = unicodedata.normalize("NFKC", base)
    base = base.replace("\ufffd", "—")
    base = re.sub(r"\((\d+)\)(?=\.[^.]+$)", "", base)
    base = re.sub(r"\s+", " ", base).strip()
    return base


def slugify_section(section: str) -> str:
    if section in SECTION_SLUG_OVERRIDES:
        return SECTION_SLUG_OVERRIDES[section]
    ascii_section = unicodedata.normalize("NFKD", section).encode("ascii", "ignore").decode("ascii")
    ascii_section = ascii_section.lower()
    ascii_section = re.sub(r"[^a-z0-9]+", "_", ascii_section)
    return ascii_section.strip("_")


def parse_name_parts(canonical_filename: str) -> Tuple[str, str, str]:
    stem = canonical_filename.rsplit(".", 1)[0]
    match = re.match(r"(.+?)_(\d+)(?:_(\d+))?$", stem)
    if not match:
        return stem, "", ""
    section, page, figure = match.groups()
    return section, page, figure or ""


def load_protocol_steps(protocols_dir: Path) -> Dict[str, set[str]]:
    protocol_steps: Dict[str, set[str]] = {}
    for path in sorted(protocols_dir.glob("*.json")):
        payload = json.loads(path.read_text())
        protocol_id = payload.get("protocol_id")
        if not protocol_id:
            continue
        protocol_steps[protocol_id] = {step["step_id"] for step in payload.get("steps", [])}
    return protocol_steps


def default_caption(section: str, page: str, figure: str) -> str:
    if figure:
        return f"Illustration from {section} (page {page}, figure {figure})"
    return f"Illustration from {section} (page {page})"


def default_description(section: str, page: str, figure: str) -> str:
    if figure:
        return f"Reference image from the {section} section on page {page}, figure {figure}."
    return f"Reference image from the {section} section on page {page}."


def mapping_for(section: str, section_slug: str, page: str, figure: str) -> MappingDraft:
    figure_suffix = figure or "1"
    page_suffix = page or "0"

    def draft(
        *,
        asset_id: str,
        asset_role: str = "reference",
        protocol_id: str = "",
        step_id: str = "",
        confidence: str = "low",
        review_needed: bool = True,
        status: str = "review_needed",
        caption: Optional[str] = None,
        description: Optional[str] = None,
        notes: str = "",
    ) -> MappingDraft:
        return MappingDraft(
            asset_id=asset_id,
            resource_name=asset_id,
            asset_role=asset_role,
            proposed_protocol_id=protocol_id,
            proposed_step_id=step_id,
            confidence=confidence,
            review_needed=review_needed,
            status=status,
            caption=caption or default_caption(section, page, figure),
            content_description=description or default_description(section, page, figure),
            notes=notes,
        )

    if section == "bleeding":
        if page == "83":
            return draft(
                asset_id=f"bleeding_apply_direct_pressure_{figure_suffix.zfill(2)}",
                asset_role="step_visual_aid",
                protocol_id="bleeding_external_general",
                step_id="apply_direct_pressure",
                confidence="medium",
                status="candidate_for_current_protocol",
                caption="Apply firm direct pressure to the bleeding wound",
                description="A responder applying direct pressure to a bleeding arm wound.",
                notes="Useful for the main external bleeding action step.",
            )
        return draft(
            asset_id=f"bleeding_reference_page_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="bleeding_external_general",
            confidence="low",
            status="review_needed",
            notes="Bleeding family image, but the exact step is not clear from the filename alone.",
        )

    if section == "impaledobjects":
        return draft(
            asset_id=f"bleeding_embedded_object_stabilize_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="bleeding_external_general",
            step_id="do_not_remove_embedded_object",
            confidence="medium",
            status="candidate_for_current_protocol",
            caption="Do not remove an embedded object from the wound",
            description="An impaled object left in place while the surrounding wound is stabilized.",
            notes="Good candidate for the embedded-object warning step.",
        )

    if section == "tourniquets":
        return draft(
            asset_id=f"tourniquet_application_{figure_suffix.zfill(2)}",
            asset_role="future_protocol_visual",
            confidence="low",
            status="future_protocol",
            notes="The current app protocols do not include a tourniquet step yet.",
        )

    if section == "woundcleaning":
        return draft(
            asset_id=f"minor_wound_rinse_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="bleeding_minor_general",
            step_id="rinse_and_check",
            confidence="medium",
            status="candidate_for_current_protocol",
            caption="Rinse and clean a minor wound",
            description="A responder flushing a small open wound with clean water.",
            notes="Fits the minor bleeding rinse step.",
        )

    if section == "wounddressing":
        return draft(
            asset_id=f"wound_dressing_apply_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="bleeding_minor_general" if page == "87" else "bleeding_external_general",
            confidence="low",
            status="review_needed",
            caption="Wound dressing reference image",
            description="A wound dressing reference image from the manual.",
            notes="Manual review held this family back. The current images show wound state or closure strips rather than a clear generic dressing action.",
        )

    if section == "abrasions":
        return draft(
            asset_id=f"abrasion_clean_and_cover_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="bleeding_minor_general",
            confidence="low",
            status="review_needed",
            caption="Abrasion reference image",
            description="A close-up reference image of an abrasion on the knee.",
            notes="Manual review held this image back because it shows the injury state, not a clear care action.",
        )

    if section == "nosebleeds":
        return draft(
            asset_id=f"nosebleed_pinch_soft_nose_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="nosebleed_general",
            step_id="pinch_soft_nose",
            confidence="medium",
            status="candidate_for_current_protocol",
            caption="Pinch the soft part of the nose while leaning forward",
            description="A person pinching the soft part of the nose to control a nosebleed.",
            notes="High-value visual for nosebleed guidance.",
        )

    if section == "Splinting":
        if page == "44" and figure == "2":
            return draft(
                asset_id="fracture_lower_leg_splint_reference_01",
                asset_role="step_visual_aid",
                protocol_id="fracture_suspected_general",
                step_id="immobilize_joint_above_and_below",
                confidence="medium",
                status="candidate_for_current_protocol",
                caption="Secure a lower-leg splint with padding and ties",
                description="A lower leg supported with padding and tied in place to limit movement.",
                notes="Strong match, but not imported in the first UI pass because the app already shows two fracture examples.",
            )
        if page == "45" and figure == "2":
            return draft(
                asset_id="fracture_forearm_improvised_splint_01",
                asset_role="step_visual_aid",
                protocol_id="fracture_suspected_general",
                step_id="immobilize_joint_above_and_below",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Support the forearm with padding and an improvised rigid splint",
                description="A forearm secured against a padded improvised splint with cloth wraps.",
                notes="Imported into the app after manual review.",
            )
        if page == "48":
            return draft(
                asset_id="fracture_leg_padded_splint_01",
                asset_role="step_visual_aid",
                protocol_id="fracture_suspected_general",
                step_id="immobilize_joint_above_and_below",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Immobilize the leg with padding and ties above and below the injury",
                description="A leg immobilized with padding and multiple cloth ties to keep the limb still.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"splinting_immobilize_limb_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="fracture_suspected_general",
            confidence="low",
            status="review_needed",
            caption="Splinting reference image",
            description="A splinting reference image from the manual.",
            notes="Manual review held this image back because it shows a setup step or a narrow sub-technique rather than the clearest generic example.",
        )

    if section == "Dislocations":
        return draft(
            asset_id=f"dislocation_do_not_realign_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="fracture_suspected_general",
            step_id="do_not_realign",
            confidence="low",
            status="review_needed",
            notes="The dislocation family fits, but the exact step still needs manual confirmation.",
        )

    if section == "Burns":
        if page == "54":
            return draft(
                asset_id="burn_cover_clean_dressing_01",
                asset_role="step_visual_aid",
                protocol_id="burn_second_degree_general",
                step_id="cover_clean",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Loosely cover the burn with a clean dressing",
                description="A clean rectangular dressing being placed gently over a burn on the forearm.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"burn_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="burn_second_degree_general",
            confidence="low",
            status="review_needed",
            notes="The burns pages may mix severity examples and care steps, so keep them in review until checked against the manual page.",
        )

    if section == "heatstroke":
        return draft(
            asset_id="heat_stroke_cooling_01",
            asset_role="step_visual_aid",
            protocol_id="heat_stroke_general",
            step_id="cool_aggressively",
            confidence="medium",
            status="candidate_for_current_protocol",
            caption="Cool the person as fast as possible",
            description="A rescuer actively cooling a person with suspected heat stroke near the waterline.",
            notes="Imported into the app after manual review.",
        )

    if section == "mildhypothermia":
        return draft(
            asset_id="hypothermia_warm_core_01",
            asset_role="step_visual_aid",
            protocol_id="hypothermia_mild_general",
            step_id="warm_core_first",
            confidence="medium",
            status="candidate_for_current_protocol",
            caption="Wrap the person and warm the core first",
            description="A person wrapped in blankets holding a warm drink during rewarming for mild hypothermia.",
            notes="Imported into the app after manual review.",
        )

    if section == "severehypothermia":
        if figure in ("", "1"):
            return draft(
                asset_id="hypothermia_wrap_blankets_01",
                asset_role="step_visual_aid",
                protocol_id="hypothermia_general",
                step_id="wrap_in_blankets",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Wrap the person fully and keep cold air off the body",
                description="A person wrapped in blankets and reflective material during hypothermia care.",
                notes="Imported into the app after manual review.",
            )
        if figure == "2":
            return draft(
                asset_id="hypothermia_sleeping_bag_insulation_01",
                asset_role="step_visual_aid",
                protocol_id="hypothermia_general",
                step_id="wrap_in_blankets",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Use a sleeping bag or thick wrap and insulate from the ground",
                description="A person enclosed in a sleeping bag setup with insulating material underneath.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"hypothermia_handle_gently_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="hypothermia_general",
            step_id="call_and_handle_gently",
            confidence="low",
            status="review_needed",
            notes="This family fits hypothermia, but the precise step needs visual confirmation.",
        )

    if section == "stroke":
        if figure in ("", "1"):
            return draft(
                asset_id="stroke_fast_face_droop_01",
                asset_role="step_visual_aid",
                protocol_id="stroke_fast_general",
                step_id="check_fast_signs",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Check for facial droop when the person smiles",
                description="A face showing one-sided drooping consistent with a FAST stroke check.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id="stroke_fast_arm_drift_01",
            asset_role="step_visual_aid",
            protocol_id="stroke_fast_general",
            step_id="check_fast_signs",
            confidence="high",
            review_needed=False,
            status="candidate_for_current_protocol",
            caption="Ask the person to raise both arms and look for weakness or drift",
            description="A person raising both arms unevenly during a FAST stroke check.",
            notes="Imported into the app after manual review.",
        )

    if section == "heartattack":
        return draft(
            asset_id="heart_attack_reference_01",
            asset_role="reference",
            protocol_id="chest_pain_general",
            confidence="low",
            status="review_needed",
            caption="Heart-attack reference image",
            description="A seated person with possible cardiac symptoms in an outdoor setting.",
            notes="Manual review held this image back because it does not clearly show a specific current chest-pain step.",
        )

    if section == "seizure":
        if page == "116":
            return draft(
                asset_id="seizure_cushion_head_01",
                asset_role="step_visual_aid",
                protocol_id="seizure_general",
                step_id="cushion_head",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Protect the head with something soft during the seizure",
                description="A jacket being positioned under the head of a person lying on the ground during a seizure.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"seizure_safety_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="seizure_general",
            step_id="clear_hazards",
            confidence="low",
            status="review_needed",
            notes="Seizure family fits, but the exact image content needs checking.",
        )

    if section == "asthmaattack":
        if page == "99":
            return draft(
                asset_id="breathing_prescribed_inhaler_use_01",
                asset_role="step_visual_aid",
                protocol_id="breathing_problem_general",
                step_id="help_with_prescribed_inhaler",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Help the person use their prescribed inhaler as directed",
                description="A child using a prescribed inhaler with the mouthpiece sealed at the lips.",
                notes="Imported into the app after manual review.",
            )
        if page == "100":
            return draft(
                asset_id="breathing_prescribed_inhaler_spacer_01",
                asset_role="step_visual_aid",
                protocol_id="breathing_problem_general",
                step_id="help_with_prescribed_inhaler",
                confidence="medium",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="If prescribed, connect the inhaler to the spacer before use",
                description="Hands attaching an inhaler to a spacer device before assisting with medication use.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"breathing_problem_support_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="breathing_problem_general",
            step_id="help_with_prescribed_inhaler",
            confidence="low",
            status="review_needed",
            notes="May map to breathing problem or breathing distress, depending on the image details.",
        )

    if section == "Conscious Choking—Adult and Child":
        if figure in ("", "1"):
            return draft(
                asset_id="choking_back_blow_01",
                asset_role="step_visual_aid",
                protocol_id="choking_general",
                step_id="five_back_blows",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Give firm back blows between the shoulder blades",
                description="A hand positioned to deliver a back blow between the shoulder blades.",
                notes="Imported into the app after manual review.",
            )
        if figure == "2":
            return draft(
                asset_id="choking_abdominal_thrust_hand_position_01",
                asset_role="step_visual_aid",
                protocol_id="choking_general",
                step_id="five_abdominal_thrusts",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Place the fist just above the navel for abdominal thrusts",
                description="A close-up showing fist placement just above the navel for an abdominal thrust.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id="choking_abdominal_thrust_01",
            asset_role="step_visual_aid",
            protocol_id="choking_general",
            step_id="five_abdominal_thrusts",
            confidence="high",
            review_needed=False,
            status="candidate_for_current_protocol",
            caption="Stand behind the person and deliver abdominal thrusts inward and upward",
            description="A rescuer standing behind a choking adult to deliver abdominal thrusts.",
            notes="Imported into the app after manual review.",
        )

    if section == "Unconscious Choking—Adult and Child":
        if page == "18" and figure == "3":
            return draft(
                asset_id="choking_unresponsive_chest_compressions_01",
                asset_role="step_visual_aid",
                protocol_id="choking_general",
                step_id="if_unresponsive_start_cpr",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="If the person becomes unresponsive, lower them to the floor and start chest compressions",
                description="A rescuer performing chest compressions on an unresponsive choking adult.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"choking_unresponsive_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="choking_general",
            confidence="low",
            status="review_needed",
            caption="Unresponsive choking reference image",
            description="A reference image from the unresponsive choking sequence.",
            notes="Manual review held this image back because it shows rescue breaths or airway steps that the current generic step text does not explicitly cover.",
        )

    if section == "CPR—Adult and Child":
        if figure in ("", "1"):
            return draft(
                asset_id="cardiac_cpr_hand_position_01",
                asset_role="step_visual_aid",
                protocol_id="cardiac_arrest_general",
                step_id="hand_position",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Place the heel of one hand in the center of the chest",
                description="A rescuer placing both hands in the center of the chest for CPR compressions.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"cpr_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="cardiac_arrest_general",
            confidence="low",
            status="review_needed",
            caption="CPR reference image",
            description="A CPR reference image from the manual.",
            notes="Manual review held this image back because it shows rescue breaths, while the current cardiac-arrest step sequence focuses on compressions and AED guidance.",
        )

    if section == "AED—Adult and Child":
        if page == "20" and figure in ("", "1"):
            return draft(
                asset_id="cardiac_aed_power_on_01",
                asset_role="step_visual_aid",
                protocol_id="cardiac_arrest_general",
                step_id="use_aed_when_available",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Turn the AED on and follow the voice prompts",
                description="A gloved hand pressing the power button on an AED.",
                notes="Imported into the app after manual review.",
            )
        if page == "20" and figure == "3":
            return draft(
                asset_id="cardiac_aed_attach_pad_adult_01",
                asset_role="step_visual_aid",
                protocol_id="cardiac_arrest_general",
                step_id="use_aed_when_available",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Attach the AED pads to the exposed chest as shown",
                description="An AED pad being attached to an adult chest in the recommended position.",
                notes="Imported into the app after manual review.",
            )
        if page == "20" and figure == "4":
            return draft(
                asset_id="cardiac_aed_attach_pad_child_01",
                asset_role="step_visual_aid",
                protocol_id="cardiac_arrest_general",
                step_id="use_aed_when_available",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="For a child, attach the AED pads in the position shown on the pad diagrams",
                description="An AED pad being attached to a child chest using the placement shown on the pad.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"aed_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="cardiac_arrest_general",
            confidence="low",
            status="review_needed",
            caption="AED reference image",
            description="An AED reference image from the manual.",
            notes="Manual review kept this image as reference because it is too specific or redundant for the current UI pass.",
        )

    if section == "reachthrowrowgo":
        if page == "80" and figure in ("", "1"):
            return draft(
                asset_id="drowning_reach_rescue_01",
                asset_role="step_visual_aid",
                protocol_id="drowning_general",
                step_id="rescue_without_entering_water",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Reach with a long object from shore instead of entering the water",
                description="A rescuer on shore using a long branch to reach a person in the water.",
                notes="Imported into the app after manual review.",
            )
        if page == "81" and figure in ("", "1"):
            return draft(
                asset_id="drowning_throw_rescue_01",
                asset_role="step_visual_aid",
                protocol_id="drowning_general",
                step_id="rescue_without_entering_water",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Throw a flotation aid to the person if you cannot reach them",
                description="A rescuer throwing a flotation aid attached to a rope toward a person in the water.",
                notes="Imported into the app after manual review.",
            )
        if page == "81" and figure == "2":
            return draft(
                asset_id="drowning_row_rescue_01",
                asset_role="step_visual_aid",
                protocol_id="drowning_general",
                step_id="rescue_without_entering_water",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="If available, row out in a boat instead of swimming directly to the person",
                description="A rescuer in a boat using an oar to reach a person in the water.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"drowning_reach_throw_row_go_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="drowning_general",
            confidence="low",
            status="review_needed",
            caption="Reach-throw-row-go reference image",
            description="A water-rescue reference image from the reach-throw-row-go sequence.",
            notes="Manual review kept this image as reference because the current UI pass already includes a reach, throw, and row example.",
        )

    if section in {"activedrowning", "passivedrowning", "disstressedswimmer"}:
        return draft(
            asset_id=f"{section_slug}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="drowning_general",
            step_id="rescue_without_entering_water",
            confidence="low",
            status="review_needed",
            notes="Likely useful for drowning recognition or rescue prioritization, but the exact role still needs review.",
        )

    if section == "poisoning":
        return draft(
            asset_id=f"poisoning_reference_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="poisoning_general",
            confidence="low",
            status="review_needed",
            notes="Poisoning pages may illustrate route-specific handling and need manual review before step assignment.",
        )

    if section == "Checking an Unconscious Person":
        if figure == "2":
            return draft(
                asset_id="unresponsive_check_breathing_01",
                asset_role="step_visual_aid",
                protocol_id="unresponsive_breathing_general",
                step_id="confirm_breathing",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Open the airway and look, listen, and feel for normal breathing",
                description="A responder tilting the head and listening close to the mouth while checking breathing.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"unconscious_person_check_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="unresponsive_breathing_general",
            confidence="low",
            status="review_needed",
            notes="Useful family match, but the current protocols do not have a dedicated responsiveness-check step.",
        )

    if section == "CHECK the Patient":
        if figure in ("", "1"):
            return draft(
                asset_id="general_assessment_open_airway_01",
                asset_role="step_visual_aid",
                protocol_id="general_assessment_general",
                step_id="check_airway_breathing",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Open the airway and look for normal breathing",
                description="A responder opening the airway with head tilt and chin lift while checking breathing.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"general_assessment_check_patient_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="general_assessment_general",
            step_id="check_airway_breathing",
            confidence="low",
            status="review_needed",
            notes="May support the general assessment flow after manual review.",
        )

    if section in {"Superficial Scalp Injuries", "Serious Brain Injuries"}:
        if section == "Superficial Scalp Injuries":
            return draft(
                asset_id="head_injury_scalp_pressure_01",
                asset_role="step_visual_aid",
                protocol_id="head_injury_general",
                step_id="control_bleeding_and_cold_pack",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Apply gentle pressure to a scalp cut with a clean dressing",
                description="Gloved hands holding a clean pad against a bleeding scalp wound.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"{section_slug}_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="reference",
            protocol_id="head_injury_general",
            confidence="low",
            status="review_needed",
            notes="Head injury family match, but these look more diagnostic than step-by-step.",
        )

    if section in {"spinalinjuries", "logroll"}:
        if section == "spinalinjuries" and page == "63":
            return draft(
                asset_id="head_injury_support_head_in_place_01",
                asset_role="step_visual_aid",
                protocol_id="head_injury_general",
                step_id="do_not_move_if_neck_suspected",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Support the head in the position you found it",
                description="A rescuer stabilizing the head of a cyclist lying still after a crash.",
                notes="Imported into the app after manual review.",
            )
        if section == "spinalinjuries" and page == "64" and figure in ("", "1"):
            return draft(
                asset_id="head_injury_support_head_with_helmet_01",
                asset_role="step_visual_aid",
                protocol_id="head_injury_general",
                step_id="do_not_move_if_neck_suspected",
                confidence="medium",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Keep the person still and support the head without forcing movement",
                description="A rescuer supporting the head of an injured cyclist while the helmet remains in place.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"{section_slug}_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="step_visual_aid",
            protocol_id="fracture_emergency_general",
            step_id="keep_still_until_help_arrives",
            confidence="low",
            status="review_needed",
            notes="The images are relevant, but the current app has no dedicated spinal procedure steps yet.",
        )

    if section == "Non-Life-Threatening Allergic Reactions":
        if page == "34":
            return draft(
                asset_id="anaphylaxis_epinephrine_device_01",
                asset_role="step_visual_aid",
                protocol_id="anaphylaxis_general",
                step_id="help_use_epinephrine",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Use the person's prescribed epinephrine auto-injector",
                description="Two prescribed epinephrine auto-injector devices shown side by side.",
                notes="Imported into the app after manual review.",
            )
        if page == "36" and figure in ("", "1"):
            return draft(
                asset_id="anaphylaxis_epinephrine_outer_thigh_01",
                asset_role="step_visual_aid",
                protocol_id="anaphylaxis_general",
                step_id="help_use_epinephrine",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Place the auto-injector against the outer thigh",
                description="A responder positioning an epinephrine auto-injector against the outer thigh of a seated child.",
                notes="Imported into the app after manual review.",
            )
        if page == "37" and figure == "2":
            return draft(
                asset_id="anaphylaxis_epinephrine_press_hold_01",
                asset_role="step_visual_aid",
                protocol_id="anaphylaxis_general",
                step_id="help_use_epinephrine",
                confidence="high",
                review_needed=False,
                status="candidate_for_current_protocol",
                caption="Press and hold the injector in the outer thigh as directed",
                description="A close-up of an epinephrine auto-injector pressed into the outer thigh during use.",
                notes="Imported into the app after manual review.",
            )
        return draft(
            asset_id=f"{section_slug}_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="future_protocol_visual",
            confidence="low",
            status="future_protocol",
            notes="Most of this section stays outside the current MVP, but the prescribed epinephrine-use images were imported after manual review.",
        )

    if section in {"Flail Chest", "Rib Injuries", "Sucking Chest Wound",
                   "Wilderness and Remote First Aid Kits", "Glove Removal", "High Altitude Cerebral Edema",
                   "frostbite", "immersionfoot", "lightning", "openabdominalinjuries", "closedabdominalinjuries",
                   "blanketdrag", "clothesdrag", "footdrag", "packstrapcarry", "walkingassist",
                   "twopersonseatcarry"}:
        return draft(
            asset_id=f"{section_slug}_{page_suffix}_{figure_suffix.zfill(2)}",
            asset_role="future_protocol_visual",
            confidence="low",
            status="future_protocol",
            notes="Relevant medical content, but there is no direct home for it in the current MVP protocols.",
        )

    return draft(
        asset_id=f"{section_slug}_{page_suffix}_{figure_suffix.zfill(2)}",
        asset_role="reference",
        confidence="low",
        status="review_needed",
        notes="No specific mapping rule exists yet for this image family.",
    )


def validate_mapping(draft: MappingDraft, protocol_steps: Dict[str, set[str]]) -> MappingDraft:
    if not draft.proposed_protocol_id:
        return draft
    if draft.proposed_protocol_id not in protocol_steps:
        return MappingDraft(
            **{**asdict(draft), "status": "invalid_protocol", "review_needed": True, "confidence": "low"},
        )
    if draft.proposed_step_id and draft.proposed_step_id not in protocol_steps[draft.proposed_protocol_id]:
        return MappingDraft(
            **{**asdict(draft), "status": "invalid_step", "review_needed": True, "confidence": "low"},
        )
    return draft


def build_rows(zip_path: Path, protocol_steps: Dict[str, set[str]]) -> Tuple[List[InventoryRow], Dict[str, int]]:
    grouped: "OrderedDict[str, List[str]]" = OrderedDict()
    with zipfile.ZipFile(zip_path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            canonical = normalize_basename(info.filename)
            grouped.setdefault(canonical, []).append(info.filename.rsplit("/", 1)[-1])

    rows: List[InventoryRow] = []
    status_counter: Counter[str] = Counter()

    for canonical_filename, variants in grouped.items():
        section, page, figure = parse_name_parts(canonical_filename)
        section_slug = slugify_section(section)
        draft = validate_mapping(mapping_for(section, section_slug, page, figure), protocol_steps)
        status_counter[draft.status] += 1

        rows.append(
            InventoryRow(
                original_filename=variants[0],
                canonical_filename=canonical_filename,
                section=section,
                section_slug=section_slug,
                source_page=page,
                source_figure=figure,
                duplicate_count=len(variants),
                duplicate_variants=" | ".join(variants),
                source_manual_url=SOURCE_MANUAL_URL,
                asset_id=draft.asset_id,
                resource_name=draft.resource_name,
                asset_role=draft.asset_role,
                proposed_protocol_id=draft.proposed_protocol_id,
                proposed_step_id=draft.proposed_step_id,
                confidence=draft.confidence,
                review_needed="yes" if draft.review_needed else "no",
                status=draft.status,
                caption=draft.caption,
                content_description=draft.content_description,
                notes=draft.notes,
            )
        )

    rows.sort(key=lambda row: (row.section.lower(), int(row.source_page or 0), int(row.source_figure or 0), row.canonical_filename.lower()))
    return rows, dict(status_counter)


def write_outputs(rows: Iterable[InventoryRow], status_counts: Dict[str, int], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    rows = list(rows)
    section_counts = Counter(row.section for row in rows)
    protocol_candidate_counts = Counter(
        row.proposed_protocol_id
        for row in rows
        if row.status == "candidate_for_current_protocol" and row.proposed_protocol_id
    )

    csv_path = output_dir / "first_aid_manual_inventory.csv"
    json_path = output_dir / "first_aid_manual_inventory.json"
    summary_path = output_dir / "first_aid_manual_inventory_summary.json"

    with csv_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(asdict(rows[0]).keys()))
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))

    json_payload = {
        "source_manual_url": SOURCE_MANUAL_URL,
        "unique_image_count": len(rows),
        "status_counts": status_counts,
        "section_counts": dict(section_counts),
        "candidate_protocol_counts": dict(protocol_candidate_counts),
        "items": [asdict(row) for row in rows],
    }
    json_path.write_text(json.dumps(json_payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    summary_path.write_text(json.dumps({
        "source_manual_url": SOURCE_MANUAL_URL,
        "unique_image_count": len(rows),
        "status_counts": status_counts,
        "section_counts": dict(section_counts),
        "candidate_protocol_counts": dict(protocol_candidate_counts),
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    protocol_steps = load_protocol_steps(PROTOCOLS_DIR)
    rows, status_counts = build_rows(args.zip_path, protocol_steps)
    write_outputs(rows, status_counts, args.output_dir)
    print(f"Wrote {len(rows)} unique inventory rows to {args.output_dir}")
    print(json.dumps(status_counts, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
