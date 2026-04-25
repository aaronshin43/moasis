# Execution Log

Use this file as the compact handoff and restart context for implementation work.

## Current Stage

- Active stage: `S7-real`
- Next gate: `manual Melange-enabled verification on a physical Android 12+ device`

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
- prompt factory, on-device LLM interface, Melange model manager stub, Melange LLM stub, orchestrator, and answer-question use case added
- keyword-based response validator now enforces required and forbidden keyword checks
- ViewModel has an AI-enabled branch while preserving canonical fallback and the existing `AI_ENABLED=false` path
- S7 AI and validator tests passed with `./gradlew.bat clean :app:testDebugUnitTest --rerun-tasks`
- `:app:assembleDebug` passed after S7 integration
- real Melange SDK dependency added with `jniLibs.useLegacyPackaging = true`
- Melange dependency pinned to `com.zeticai.mlange:mlange:1.6.1`
- BuildConfig plumbing added for `MOASIS_AI_ENABLED`, `MOASIS_MELANGE_PERSONAL_KEY`, `MOASIS_MELANGE_MODEL_NAME`, `MOASIS_MELANGE_MODEL_VERSION`, and `MOASIS_MELANGE_MODEL_MODE`
- `INTERNET` permission added for the first-run Melange download path
- app LLM runtime now selects a real Melange-backed adapter when configured and falls back to a deterministic rule-based engine otherwise
- Melange adapter now uses the direct SDK path with `ZeticMLangeLLMModel(context, personalKey, name, version, modelMode, onProgress)`
- Melange calls are now offloaded from the main thread in `EmergencyViewModel`; canonical guidance renders first and is updated when AI output returns
- app startup now preloads the Melange model when AI is enabled and shows progress / readiness in the UI
- current resolved Melange runtime requires `minSdk 31`, so the app baseline was raised from 24 to 31
- `:app:testDebugUnitTest --rerun-tasks` passed after S7-real wiring
- `:app:assembleDebug` passed after S7-real wiring

## Open Blockers

- Room persistence is still deferred until after the S4 line
- real Melange output still needs on-device verification with valid dashboard credentials and a physical Android 12+ device
- exact runtime package layout is handled reflectively right now because the public docs do not publish stable Android import examples for `ZeticMLangeLLMModel`

## Next Unlock Condition

To close `S7-real`, the repo needs:

1. supply valid `MOASIS_MELANGE_PERSONAL_KEY` and `MOASIS_MELANGE_MODEL_NAME` values
2. verify AI-enabled personalization path on a physical Android 12+ device
3. confirm question answering resumes the step cleanly when Melange is enabled
4. confirm invalid output or runtime failure still falls back to canonical text in the UI
5. keep `AI_ENABLED=false` as the guaranteed demo fallback

## Rollback Point

- current baseline: initial Android skeleton
- target rollback tag after `S4`: `mvp-deterministic`

## Notes

- Prioritize `S0 -> S1 -> S2-lite -> S3 -> S4`
- Do not start LLM integration before the deterministic demo line exists
- After `S1`, any contract change should be treated as a deliberate interface update
- `S2-extended` remains deferred until after the S4 gate
