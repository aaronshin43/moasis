# Execution Log

Use this file as the compact handoff and restart context for implementation work.

## Current Stage

- Active stage: `S3`
- Next gate: `deterministic engine proven`

## Frozen Contracts

- `UserTurn`
- `ObservedFact`
- `TurnContext`
- `DialogueState`
- `EntryIntent`
- `DomainIntent`
- `UiState`
- `ChecklistItem`
- `VisualAid`
- `VisualAidType`
- `AppEvent`
- `UiAction`
- `Protocol`
- `ProtocolStep`
- `AssetRef`
- `Tree`
- `TreeNode`
- `Transition`
- `Route`
- `LlmRequest`
- `LlmResponse`

## Passed Checks

- `AGENT.md` reviewed
- `docs/ARCHITECTURE.md` reviewed
- `PLAN.md` reviewed
- execution workflow documented in `docs/EXECUTION_GUIDE.md`
- `:app:assembleDebug` passed
- app launches to a blank screen on emulator with no crash
- package scaffolding added for `ui`, `presentation`, `audio`, `imaging`, `domain`, `data`, and `ai`
- Android permissions added for audio, camera, and media images
- repository note added for Melange and Gemma4 E2B procurement planning
- core pure-Kotlin model layer added in `domain/model`, `presentation`, and `ai/model`
- serialization round-trip unit test passed with `:app:testDebugUnitTest`
- minimal protocol JSON assets added for entry, collapse, burn, and bleeding flows
- visual asset catalog JSON added
- file-backed JSON data sources and repositories added for protocols and visual assets
- repository lookup tests passed with `:app:testDebugUnitTest`

## Open Blockers

- deterministic NLU and state logic do not exist yet
- Room persistence is still deferred until after the S4 line

## Next Unlock Condition

To close `S3`, the repo needs:

1. implement `RegexIntentMatcher`, `SlotExtractor`, and `NluRouter`
2. implement `EntryTreeRouter`, `ProtocolStateMachine`, `InterruptionRouter`, `DialogueStateManager`, and `VisionTaskRouter`
3. add scenario tests for burn entry, collapse entry, clarification preservation, re-triage, and next-step control

## Rollback Point

- current baseline: initial Android skeleton
- target rollback tag after `S4`: `mvp-deterministic`

## Notes

- Prioritize `S0 -> S1 -> S2-lite -> S3 -> S4`
- Do not start LLM integration before the deterministic demo line exists
- After `S1`, any contract change should be treated as a deliberate interface update
- `S2-extended` remains deferred until after the S4 gate
