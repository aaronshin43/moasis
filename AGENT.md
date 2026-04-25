# AGENT.md

## Purpose

This repository is for an **offline Android emergency response app**.

The app must:

- run primary functionality fully on-device
- use a deterministic state machine for triage, branching, and next-step decisions
- use on-device AI only for response personalization, multimodal interpretation, and step-related Q&A
- remain functional even if AI features are slow, degraded, or unavailable

## Source of Truth

Read these documents before making structural changes:

- `ARCHITECTURE.md`

If the two documents ever differ, preserve the existing implementation direction and update both documents together.

## Core Product Rules

- This is **not** a general medical chatbot.
- Do **not** let the LLM invent new emergency protocols.
- Canonical protocol steps must come from structured local data.
- Ambiguous user input must start from Entry Tree / triage logic.
- Images are optional input in any turn, not required input.
- Visual aid assets are optional output, not generated content.
- Image analysis results are suggestions or observations, not final truth.
- Life-threatening branching must not depend only on image analysis.

## Architecture Rules

- Keep **decision logic deterministic**.
- Keep **response generation constrained** by canonical protocol text.
- Preserve the separation between:
  - protocol/state logic
  - AI orchestration
  - UI rendering
- Design MVP changes so they can later support the hybrid vision path without large rewrites.

## AI Usage Rules

- Primary on-device LLM: `Gemma4 E2B` via `Melange`
- MVP multimodal path: Gemma handles image-containing turns directly
- Expansion path: optional specialized vision models may handle structured visual tasks such as:
  - kit detection
  - step verification
- Normalize multimodal outputs into structured observation data before using them in state logic

## Coding Rules

- Write all code and identifiers in clear English where reasonable.
- **All code comments must be written in English.**
- Keep comments short and useful.
- Do not add comments that merely restate obvious code.
- Prefer simple, explicit logic over clever abstractions.
- Do not introduce network dependence for core flows.

## Safety Rules

- Favor safer fallback behavior when confidence is low.
- If AI output fails validation, fall back to canonical protocol text or deterministic logic.
- Do not silently replace protocol meaning during personalization.

## When Implementing

Follow Steps in `PLAN.md`.

## File Placement

Keep new files aligned with the package structure defined in `ARCHITECTURE.md`.
