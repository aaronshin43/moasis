# PLAN.md

Implementation plan for the hackathon offline AI emergency response app (MOASIS).
Source documents: `CLAUDE.md`, `docs/ARCHITECTURE.md`. If conflicts arise, `ARCHITECTURE.md` wins.

---

## 0. Design Principles (Enforced Across Every Stage)

These principles override every per-stage checkpoint. If any stage violates them, that stage is considered incomplete regardless of other progress.

1. **AI-Optional Principle.** At any point during the build, the deterministic core must be able to drive a full emergency walkthrough end-to-end without the LLM or vision model. (Stage 4 is where this becomes a hard gate.)
2. **Decision Plane vs Response Plane Separation.** "What to do" is decided by the State Machine. The LLM only decides "how to say it." No code path may let the LLM add, skip, or reorder steps.
3. **Canonical-First.** Protocol body text comes only from structured local data (JSON / SQLite). LLM output is shown to the user only after passing the `must_keep_keywords` / `forbidden_keywords` Validator.
4. **Images Are Always Optional.** Images are auxiliary on both input (user uploads) and output (visual aids). The app must behave identically when no image is present or when image analysis fails.
5. **Lay the Hybrid Interfaces in MVP.** `VisionTaskRouter`, `MultimodalInterpreter`, `ObservedFact`, and `TurnContext` exist as interfaces from MVP onward. MVP ships with only `GemmaMultimodalInterpreter`, but it must be swappable for `HybridMultimodalInterpreter` later without rewrites.
6. **Build Order.** data models → protocol/state logic → repositories & local assets → AI adapters & validators → UI wiring. Do not start writing the next layer until the current layer's self-tests pass.

---

## Stage Overview

| Stage | Goal | AI Required? | What You See When Done |
|---|---|---|---|
| S0 | Project scaffolding, dependencies, package tree | None | Build passes + blank screen |
| S1 | Core domain data models | None | Unit tests green |
| S2 | Local protocol KB + repositories | None | JSON load/lookup tests green |
| S3 | Deterministic decision engine | None | NLU→EntryTree→StateMachine scenario unit tests green |
| **S4 (gate)** | **End-to-End demo without AI** | **None** | **Full burn / collapse scenarios complete via text-only input** |
| S5 | Audio I/O (STT/TTS, barge-in) | None | Full scenarios complete via voice |
| S6 | Image I/O (camera/gallery, UserTurn) | None | Image-attached turns flow through (no analysis yet) |
| S7 | Melange + Gemma personalization + Validator | LLM | Same step phrased differently per user context |
| S8 | Multimodal interpretation (GemmaMultimodalInterpreter + ObservationMerger) | LLM | Image-bearing turns produce / merge `ObservedFact`s |
| S9 | Scenario polish + demo hardening | LLM | 4 demo scenarios run reliably |
| S10 (stretch) | Hybrid Vision expansion | LLM + Vision | KIT_DETECTION / STEP_VERIFICATION split out |

> **Demo Safety Net:** If S7 is still unstable right before the demo, the team must be able to disable LLM calls and run in S4 mode. This is enforced by an `AI_ENABLED` toggle in code.

---

## S0. Project Bootstrap

### Goal
Lock in the build environment, finalize the package tree, and pre-investigate the Melange / Gemma integration path.

### Tasks
- Add dependencies in `app/build.gradle.kts`:
  - `androidx.lifecycle:lifecycle-viewmodel-compose`
  - `androidx.lifecycle:lifecycle-runtime-compose`
  - `kotlinx-coroutines-android`
  - `kotlinx-serialization-json`
  - `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (used from S2 onward)
  - `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view` (used from S6 onward)
- Update `gradle/libs.versions.toml`.
- Declare permissions in `AndroidManifest.xml` (RECORD_AUDIO, CAMERA, READ_MEDIA_IMAGES). Do **not** add INTERNET — offline is the whole point.
- Create empty package directories matching `ARCHITECTURE.md` §16: `ui/screen`, `ui/component`, `presentation`, `audio`, `imaging`, `domain/{model,nlu,state,safety,usecase}`, `data/{protocol,visual,local}`, `ai/{melange,orchestrator,prompt,model}`, `assets/{protocols,visuals/images,demo_inputs,knowledge}`.
- Document the Melange SDK package / Gemma4 E2B model file procurement path in the README or a side note (real integration happens in S7).

### Checkpoints
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] App launches to a blank screen with no crash.
- [ ] Package directory layout matches `ARCHITECTURE.md` §16 1:1.
- [ ] Melange SDK package name / version / model download steps are written down somewhere in the repo.

---

## S1. Core Domain Data Models

### Goal
Pin down the immutable data models every higher layer depends on. If signatures wobble here, everything above wobbles too.

### Tasks
Place all of the following under `domain/model/` (or the indicated package), as `data class` / `sealed class`, with `kotlinx.serialization` applied where appropriate:

- `UserTurn` (`ARCHITECTURE.md` §11.1)
- `ObservedFact`, `FactSource`, `VisionTaskType`, `TurnContext` (§11.2)
- `DialogueState` sealed class: `EntryMode`, `ProtocolMode`, `QuestionMode`, `ReTriageMode`, `Completed` (§11.3)
- `UiState`, `VisualAid`, `VisualAidType`, `ChecklistItem` (§11.4)
- `AppEvent` sealed class (§11.5)
- `EntryIntent`, `DomainIntent` enums (based on §6.4 examples)
- `Protocol`, `ProtocolStep`, `AssetRef`, `Tree`, `TreeNode`, `Transition`, `Route` (§12)
- `LlmRequest`, `LlmResponse` (`ai/model/`, §13)

### Checkpoints
- [ ] Every model lives in `domain/model/` (or the explicitly named package). No misplaced files.
- [ ] Every model is `kotlinx.serialization`-roundtrippable (one unit test proving this).
- [ ] `DialogueState` and `AppEvent` are sealed so `when` exhaustiveness is enforced at compile time.
- [ ] No model depends on Android SDK types — this layer is pure Kotlin.

---

## S2. Local Knowledge Base + Repositories

### Goal
Single source of truth for protocol body and visual assets. A deterministic path to fetch step text without any LLM in play.

### Tasks
- Author at least 3 trees / protocols as JSON under `assets/protocols/`:
  - `entry_general_emergency.json`
  - `collapsed_person_entry.json` (use §12.2 example)
  - `burn_tree.json` + `burn_second_degree_general` protocol (use §12.3 example)
  - `bleeding_tree.json` (a minimal version is acceptable)
- `assets/visuals/asset_catalog.json` plus 2–3 placeholder images (`burn_cool_water_arm_01.webp`, `bandage_wrap_arm_01.webp`).
- `data/protocol/JsonProtocolDataSource.kt`: read JSON from assets, parse into domain models.
- `data/protocol/ProtocolRepository.kt`: `getTree(treeId)`, `getProtocol(protocolId)`. (FTS is a stretch goal.)
- `data/visual/AssetCatalogDataSource.kt`, `data/visual/VisualAssetRepository.kt`: `getAssetsForStep(protocolId, stepId)`, `resolveAsset(assetId)`.
- `data/local/AppDatabase.kt`, `SessionDao.kt`: persist last state, slot cache, turn history (minimal Room schema).

### Checkpoints
- [ ] JSON schema matches `ARCHITECTURE.md` §12 exactly (`tree_id`, `start_node`, `nodes[].transitions`, etc.).
- [ ] Unit test: `ProtocolRepository.getProtocol("burn_second_degree_general")` returns exactly 2 steps.
- [ ] Unit test: `VisualAssetRepository.getAssetsForStep("burn_second_degree_general", "cool_water")` returns at least 1 asset.
- [ ] Looking up a missing `protocol_id` returns a clean `Result.failure` or `null` (no thrown exception).
- [ ] Steps with no images render correctly via empty `asset_refs` (no layout jump downstream).

---

## S3. Deterministic Decision Engine

### Goal
Prove "the next step is decided without AI" in code. By the end of this stage, scenario unit tests can drive a sequence to terminal.

### Tasks
`domain/nlu/`:
- `RegexIntentMatcher.kt`: §6.4 priority order (regex > keyword > phrase normalization). Minimum patterns: `PERSON_COLLAPSED`, `BURN`, `BLEEDING`, `BREATHING_PROBLEM`, `CHEST_PAIN`, `CHOKING`.
- `SlotExtractor.kt`: location (`arm` / `hand` / `face`), patient type (`adult` / `child`), responses (`yes` / `no`), and other essential slots.
- `NluRouter.kt`: glue the two together, return `entryIntent + domainHints + slots + confidence`.

`domain/state/`:
- `EntryTreeRouter.kt`: §6.5 routing rules — clear injury → domain tree direct, ambiguous → GeneralEntryTree, collapse → CollapsedPersonEntryTree.
- `ProtocolStateMachine.kt`: handle node types from §6.6 (`question` / `instruction` / `route` / `router` / `checklist` / `terminal`). Verify slot fulfillment, then decide the next node. **Implement as a pure function** (input: current state + event → output: new state + side-effect descriptor).
- `InterruptionRouter.kt`: §6.8 / §15.1 priority — life-threat keyword > control intent > clarification > out-of-domain. **Forced re-triage on life-threat keywords must sit at priority 1.**
- `DialogueStateManager.kt`: combine the above to perform a per-turn reduce.
- `VisionTaskRouter.kt`: §6.5b rule-based routing (using current protocolId / stepId / text). At this stage, classify only — do not call any image analyzer.
- `ObservationMerger.kt`: §6.6b — merge text / voice / image-analysis results into a single turn context. At this stage, only the no-image path is exercised.

`domain/safety/`:
- `ResponseValidator.kt`: interface plus a stub implementation (passes `canonicalText` through unchanged). The real Validator lands in S7.

### Checkpoints
- [ ] Scenario test 1: input "blisters on the arm" → `BURN` classification → `BurnTree` entry → resolve `burn_second_degree_general` → return step `cool_water`.
- [ ] Scenario test 2: input "my friend collapsed" → `PERSON_COLLAPSED` → enter `CollapsedPersonEntryTree.scene_safe` node.
- [ ] Scenario test 3: while in ProtocolMode, input "they can't breathe" → InterruptionRouter classifies as `STATE_CHANGING_REPORT` → transition to `ReTriageMode`.
- [ ] Scenario test 4: while in ProtocolMode, input "can I use ice?" → classified as `CLARIFICATION_QUESTION` → current stepIndex preserved.
- [ ] Scenario test 5: "next" → `CONTROL_INTENT.NEXT` → stepIndex + 1.
- [ ] StateMachine has zero dependencies on LLM or networking (verified by a dependency graph check).

---

## S4. End-to-End Demo Without AI (Mandatory Gate)

### Goal
**This is the safety line.** From this point forward, even if the LLM never runs a single token, the demo must still work.

### Tasks
- `presentation/EmergencyViewModel.kt`: `MutableStateFlow<UiState>` plus a `reduce(AppEvent)` pattern. Calls into S3's `DialogueStateManager`.
- `presentation/UiAction.kt`: `Next`, `Repeat`, `Back`, `CallEmergency`, `SubmitText`.
- `ui/screen/HomeScreen.kt`: text input plus a "Start" button.
- `ui/screen/ActiveProtocolScreen.kt`: current step text, warning, checklist, visual aid, "Next / Repeat / Emergency Call" buttons (§18.1 layout).
- `ui/component/StepCard.kt`, `WarningBanner.kt`, `VisualAidStrip.kt` (§18.3 rules: 1–2 max, below step text, `contentDescription` required).
- `MainActivity.kt`: inject the ViewModel into the composition tree.
- `AI_ENABLED` compile/runtime toggle (BuildConfig or a settings data class). At this stage it is hard-coded to `false`.

### Checkpoints
- [ ] Type "I burned my arm" → BurnTree entry → first step displayed → "Next" advances → terminal reached.
- [ ] Type "my friend collapsed" → CollapsedPersonEntryTree first question (`scene_safe`) shown → "yes/no" replies branch correctly.
- [ ] Step card displays a visual aid image when the step has one; layout stays clean when it does not.
- [ ] Submitting "they can't breathe" mid-step → immediately switches to ReTriage UI.
- [ ] All four scenarios above complete end-to-end with `AI_ENABLED=false`.
- [ ] **Tag this commit `mvp-deterministic` in git.** Any future regression can roll back to this tag for a guaranteed demo.

---

## S5. Audio I/O

### Goal
Drive the same scenarios via voice.

### Tasks
- `audio/AndroidSpeechRecognizer.kt`: `SpeechRecognizer` with offline preferred. Distinguish partial vs final results; use only final results for state transitions (§6.1).
- `audio/AndroidTtsEngine.kt`: `TextToSpeech` initialization, utterance queue, utteranceId tracking.
- `audio/AudioController.kt`: STT / TTS lifecycle plus barge-in (detect partial speech during TTS → stop immediately).
- `audio/VoiceEvent.kt` → mapped onto `AppEvent.VoiceTranscript`.
- `ui/component/VoiceStatusBar.kt`: listening / speaking indicator.
- Runtime permission request (RECORD_AUDIO).
- STT-failure fallback: auto-switch to text input plus "I didn't catch that" canned phrase (§19.3).

### Checkpoints
- [ ] Speak "my friend collapsed" → same scenario flow as text input.
- [ ] During TTS playback of the first step, saying "next" stops TTS immediately and advances.
- [ ] Microphone permission denied → text-only input still works fully.
- [ ] Empty or low-confidence STT result → re-asks the same question.

---

## S6. Image I/O

### Goal
Allow image attachments. The flow must behave identically whether or not an image is attached. **No analysis happens at this stage** — the image URI just rides along on the `UserTurn`.

### Tasks
- `imaging/CameraCaptureManager.kt`: single-shot CameraX `ImageCapture`.
- `imaging/GalleryPickerManager.kt`: `ActivityResultContracts.PickVisualMedia`.
- `imaging/ImageInputController.kt`: URI → copy into the app's internal cache directory → return a safe internal reference (§6.1b).
- ViewModel: assemble text / voice / image into a single `UserTurn` on input.
- `ui/screen/ActiveProtocolScreen.kt`: add camera / gallery attach buttons; show attached photo thumbnails.
- `VisionTaskRouter` already exists from S3 — wire its call here, but the analyzer call is a no-op until S8.

### Checkpoints
- [ ] Attach a photo + type "look at this" → analysis is skipped, but the turn flows through and step progress is uninterrupted (with a safe fallback message).
- [ ] `VisionTaskRouter` returns the correct `VisionTaskType` for a representative turn (covered by unit tests).
- [ ] Behavior with no image attached is 100% identical to S5.
- [ ] Camera permission denied → falls back to gallery. Both denied → attach buttons disabled (no crash).

---

## S7. Melange + Gemma Personalization + Validator (LLM Integration)

### Goal
The LLM rephrases canonical step text to match user context (slots, panic level, target listener). Critically, **only Validator-passing text reaches the user.**

### Tasks
- `ai/melange/MelangeModelManager.kt`: model file loading, backend selection (CPU / GPU / NPU), warmup.
- `ai/melange/MelangeLlmEngine.kt`: implement the `OnDeviceLlmEngine` interface (§10.4). Timeout / cancel / retry policies.
- `ai/orchestrator/InferenceOrchestrator.kt`: decide when to call the LLM (§6.9). Next step / repeat / canonical lookup do **not** call the LLM.
- `ai/prompt/PromptFactory.kt`: builders for §13.1 personalize_step requests and §13.2 answer_question requests. System prompt forces JSON output.
- Real `domain/safety/ResponseValidator.kt`: §14.2 — verify `must_keep_keywords` are present, `forbidden_keywords` are absent, length is bounded, no new steps were added. On failure, use the §14.2 canned bridge plus canonical_text fallback.
- Wire `AI_ENABLED=true` branch in the ViewModel: deterministic path decides the next step → Orchestrator personalizes → Validator passes → TTS speaks.
- `usecase/AnswerQuestionUseCase.kt`: clarification questions inject the current step context plus prohibitions into the prompt; resume per §15.2 (resume utterance must contain the step's key action verb).

### Checkpoints
- [ ] The same `cool_water` step reads differently for `target_listener=caregiver` vs `child`.
- [ ] Validator unit test: a mock LLM response missing `must_keep_keywords` → fallback fires and canonical_text is used.
- [ ] "Can I use ice?" → built as a §13.2 prompt → response followed by a resume utterance containing the step's key verb (e.g. "running water").
- [ ] LLM timeout (e.g. 3 s) → falls back to canonical_text without breaking the UX.
- [ ] Toggling `AI_ENABLED=false` cleanly returns the app to S4 mode.
- [ ] Life-threat branching never depends on LLM output (verified by code review).

---

## S8. Multimodal Interpretation

### Goal
For image-bearing turns, Gemma directly interprets the image → outputs are normalized into `ObservedFact`s → consumed by the state machine.

### Tasks
- `ai/orchestrator/MultimodalInterpreter.kt` interface (§10.4).
- `ai/orchestrator/GemmaMultimodalInterpreter.kt`: feed image + text + TurnContext to Gemma4 E2B; force structured `ObservedFact[]` JSON output.
- Real `ObservationMerger` (interface from S3): on USER_REPORTED vs VISION_SUGGESTED conflict, preserve the conflict and trigger a re-question. Life-threat-affecting slots cannot be confirmed by VISION_SUGGESTED alone (§6.6b).
- `usecase/AnalyzeImageTurnUseCase.kt`: VisionTaskRouter → MultimodalInterpreter → ObservationMerger pipeline.
- UI: thumbnail of the user's uploaded image plus an "observed: …" suggested-fact display (clearly marked as *not* confirmed).

### Checkpoints
- [ ] Burn photo + "how bad is this?" → routed to `INJURY_OBSERVATION` task → ObservedFact generated → entered as a *candidate* slot in the burn tree (not confirmed).
- [ ] Bandage photo + "did I wrap this right?" + active bandage step → routed to `STEP_VERIFICATION`.
- [ ] Image analysis failure (timeout / model error) → step progress does not stall; user sees "image analysis failed but we'll continue."
- [ ] No code path lets life-threat slots (`breathing_normal`, `responsive`, etc.) be confirmed by image alone (verified by grep).
- [ ] `MultimodalInterpreter` is interface-isolated; a Mock implementation enables unit tests.

---

## S9. Scenario Polish + Demo Hardening

### Goal
Lock in a specific demo script and run it reliably end-to-end.

### Tasks
- Finalize 4 demo scenarios (see §22.3):
  1. "blisters on the arm" → BurnTree end-to-end.
  2. "my friend collapsed" → CollapsedPersonEntryTree → branching.
  3. Mid-step interruption: "can I use ice?"
  4. Mid-step forced re-triage: "they can't breathe."
  5. (Optional) Emergency-kit photo + "what can I use here?"
- Pin each scenario as an integration test (`androidTest`).
- Tune TTS pacing (§18.2 — one sentence at a time, warnings spoken separately).
- Add a placeholder for steps missing a visual aid.
- Low-end device fallback: a settings-screen toggle for disabling the LLM (§20.3).
- Notes for the demo video / pitch script.

### Checkpoints
- [ ] All 4 integration tests pass.
- [ ] Every scenario works in airplane mode (fully offline).
- [ ] Cold-start to first step within 5 s (LLM off) / 8 s (LLM on).
- [ ] TTS barge-in feels natural during demo scenario 3.
- [ ] Presenter can run scenarios 1 → 4 without any hiccup (rehearsed at least once).

---

## S10. (Stretch) Hybrid Vision Expansion

Enter only after S9 is stable. Follows `ARCHITECTURE.md` §10.2 Profile B / §24.1 ordering.

### Tasks
- `ai/melange/MelangeVisionModelEngine.kt`: implement the `VisionModelEngine` interface (§10.4).
- `ai/orchestrator/HybridMultimodalInterpreter.kt`: based on VisionTaskRouter output, dispatch to a specialized vision model or to Gemma; normalize both into the same `ObservedFact` shape.
- First targets to split: `KIT_DETECTION` (an emergency-kit detector), then `STEP_VERIFICATION`.
- Keep `GENERAL_MULTIMODAL_QA` and context-heavy questions on Gemma.

### Checkpoints
- [ ] Swapping `GemmaMultimodalInterpreter` ↔ `HybridMultimodalInterpreter` requires no interface changes.
- [ ] KIT_DETECTION latency on an emergency-kit photo improves measurably vs Gemma-only (numbers logged).
- [ ] Vision model load failure auto-falls-back to Gemma.

---

## Cross-Cutting Work (Run Throughout)

- **Tests:** `domain/` covered by JVM unit tests. `presentation/` and above use instrumentation or Compose UI tests.
- **Logging:** All LLM I/O is logged to a local file in debug builds (stripped from release). Decisive for last-minute debugging.
- **Doc updates:** At the end of S3, S7, and S8, sync any divergence back into `ARCHITECTURE.md` (per CLAUDE.md).
- **`AI_ENABLED` toggle:** Regression-check that every LLM call site honors the toggle.
- **Permissions UX:** Manually verify mic / camera denial paths each stage.

---

## Risks / Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Melange + Gemma4 E2B integration takes longer than expected | S7 slips | S4 gate already passed → demo still possible. Toggle bypasses it. |
| LLM inference is slow on low-end devices | Bad UX | Skip-personalize mode (§20.3); speak canonical text first, async-replace with personalized version. |
| STT accuracy is poor | NLU misroutes | Always expose text input fallback; expand the keyword dictionary. |
| Image analysis produces wrong ObservedFact | Potential safety hazard | Code + tests forbid life-threat slots being confirmed by VISION_SUGGESTED alone. |
| Last-minute regression before demo | Demo failure | `mvp-deterministic` git tag enables instant rollback; S9 rehearsal mandatory. |

---

## One-Sentence Summary

Reach S4 (the AI-less demo) safely first, then layer voice → image → LLM → multimodal on top, while ensuring the demo remains runnable no matter where the build halts.
