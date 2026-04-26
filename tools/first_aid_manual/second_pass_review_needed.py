#!/usr/bin/env python3
"""Split review-needed manual images into near-fit, ambiguous, or future-leaning."""

from __future__ import annotations

import csv
import json
from collections import Counter
from pathlib import Path


ROOT = Path("/Users/johnlee/AndroidStudioProjects/moasis")
INPUT_CSV = ROOT / "docs/manual_asset_import/first_aid_manual_inventory.csv"
OUTPUT_CSV = ROOT / "docs/manual_asset_import/review_needed_second_pass.csv"
OUTPUT_JSON = ROOT / "docs/manual_asset_import/review_needed_second_pass.json"
OUTPUT_SUMMARY_JSON = ROOT / "docs/manual_asset_import/review_needed_second_pass_summary.json"


NEAR_FIT_SECTIONS = set()

AMBIGUOUS_SECTIONS = {
    "Burns",
    "frictionblisters",
    "poisoning",
    "seizure",
    "wounddressing",
}

FUTURE_LEANING_SECTIONS = {
    "AED—Adult and Child",
    "CHECK the Patient",
    "Checking an Unconscious Person",
    "Dislocations",
    "Serious Brain Injuries",
    "Splinting",
    "Superficial Scalp Injuries",
    "Unconscious Choking—Adult and Child",
    "activedrowning",
    "disstressedswimmer",
    "passivedrowning",
    "logroll",
    "reachthrowrowgo",
    "spinalinjuries",
}


def classify(row: dict[str, str]) -> tuple[str, str]:
    section = row["section"]
    filename = row["canonical_filename"]

    if section in NEAR_FIT_SECTIONS:
        return (
            "near_fit",
            "The image family already aligns with an existing protocol area, but it was held back because the current UI already has stronger examples or the shot is a narrower variation.",
        )

    if section in AMBIGUOUS_SECTIONS:
        return (
            "ambiguous",
            "The image is relevant, but the current filename and review notes still leave real uncertainty about which exact deterministic step it should reinforce.",
        )

    if section in FUTURE_LEANING_SECTIONS:
        return (
            "future_leaning",
            "The image points toward a likely future protocol, sub-protocol, or richer step flow that does not cleanly exist in the current MVP guidance.",
        )

    if filename == "heartattack_77.png":
        return (
            "ambiguous",
            "It belongs near chest-pain guidance, but the shot does not cleanly illustrate one specific current step.",
        )

    if filename == "bleeding_84.png":
        return (
            "ambiguous",
            "This belongs near the bleeding flows, but the current shot does not clearly show one specific action such as direct pressure, layered pressure, or securing a bandage.",
        )

    if filename == "abrasions_89.png":
        return (
            "ambiguous",
            "This shows the injury state rather than a care action, so it still does not cleanly reinforce a current deterministic wound-care step.",
        )

    if filename == "CPR—Adult and Child_16_2.png":
        return (
            "future_leaning",
            "The image teaches rescue-breath technique, which suggests a richer CPR branch than the current compressions-first step set exposes.",
        )

    return (
        "ambiguous",
        "No second-pass override matched this item, so it remains in the ambiguous bucket pending manual review.",
    )


def main() -> int:
    rows = list(csv.DictReader(INPUT_CSV.open(encoding="utf-8")))
    review_rows = [row for row in rows if row["status"] == "review_needed"]

    enriched_rows: list[dict[str, str]] = []
    bucket_counts: Counter[str] = Counter()

    for row in review_rows:
        bucket, reason = classify(row)
        bucket_counts[bucket] += 1
        enriched_rows.append(
            {
                **row,
                "second_pass_bucket": bucket,
                "second_pass_reason": reason,
            }
        )

    with OUTPUT_CSV.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(enriched_rows[0].keys()))
        writer.writeheader()
        writer.writerows(enriched_rows)

    summary = {
        "review_needed_count": len(review_rows),
        "bucket_counts": dict(bucket_counts),
    }
    OUTPUT_JSON.write_text(
        json.dumps(
            {
                **summary,
                "items": enriched_rows,
            },
            indent=2,
            ensure_ascii=False,
        ) + "\n",
        encoding="utf-8",
    )
    OUTPUT_SUMMARY_JSON.write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print(f"Wrote {len(review_rows)} review-needed rows.")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
