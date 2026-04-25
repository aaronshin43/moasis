# MOASIS

Offline Android emergency response app.

## Source of Truth

- `AGENT.md`
- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `PLAN.md`

If these documents conflict, preserve the implementation direction from `docs/ARCHITECTURE.md`.

## Build Direction

- Core emergency flow must work fully on-device.
- Deterministic state logic decides the next action.
- AI is optional and must never be required for the demo path.
- Protocol text comes from structured local data.

## Melange Notes

Planned runtime:

- sponsor tooling: ZETIC Melange
- primary on-device LLM: Gemma4 E2B

Procurement and integration note for later `S7` work:

1. confirm the exact Melange Android SDK artifact and integration guide from the sponsor delivery package
2. confirm the Gemma4 E2B model file format required by Melange on the target demo device
3. store model acquisition and local placement instructions in-repo before wiring runtime loading
4. keep the app functional with `AI_ENABLED=false` until on-device inference is verified
