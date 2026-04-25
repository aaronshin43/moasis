# Execution Log

Use this file as the compact handoff and restart context for implementation work.

## Current Stage

- Active stage: `S5`
- Next gate: `manual image attachment verification`

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
- deterministic NLU, interruption routing, vision task routing, observation merge stub, and response validator stub added
- pure protocol state machine and dialogue state manager added
- deterministic scenario tests passed with `:app:testDebugUnitTest`
- deterministic Compose screens, `EmergencyViewModel`, and `AI_ENABLED=false` wiring added
- debug build passed with S4 UI wiring
- ViewModel flow tests cover burn start, collapse start, collapse branching, and re-triage UI transitions
- Android speech recognizer, TTS engine, audio controller, and voice event model added
- voice status bar and microphone permission flow added to the Compose UI
- `Repeat` now re-triggers step speech through a speech request key
- `:app:assembleDebug` passed after S5 audio integration
- `:app:testDebugUnitTest --rerun-tasks` passed after S5 audio integration
- gallery picker, camera capture manager, image input controller, and attached image preview strip added
- image-bearing `UserTurn` submission now carries cached internal image paths without analysis
- camera `FileProvider` and cache-backed file paths configured
- clean rebuild passed with `./gradlew.bat clean :app:testDebugUnitTest --rerun-tasks :app:assembleDebug`

## Open Blockers

- image attachment scenarios need manual emulator walkthrough confirmation
- Room persistence is still deferred until after the S4 line

## Next Unlock Condition

To close `S6`, the repo needs:

1. confirm gallery image attachment appears as a thumbnail in the active screen
2. confirm camera capture attaches and previews without a crash
3. confirm submitting text plus image keeps deterministic progress and shows the safe image-disabled status
4. confirm image-only submit leaves the current step intact and does not stall the flow

## Rollback Point

- current baseline: initial Android skeleton
- target rollback tag after `S4`: `mvp-deterministic`

## Notes

- Prioritize `S0 -> S1 -> S2-lite -> S3 -> S4`
- Do not start LLM integration before the deterministic demo line exists
- After `S1`, any contract change should be treated as a deliberate interface update
- `S2-extended` remains deferred until after the S4 gate
