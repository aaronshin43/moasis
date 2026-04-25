# Execution Guide

This document turns `PLAN.md` into an execution workflow for the hackathon build.
If this document conflicts with `docs/ARCHITECTURE.md`, `docs/ARCHITECTURE.md` wins.
If this document conflicts with `PLAN.md` on sequencing, preserve the architectural constraints first and use this document as the day-to-day build order.

## 1. Non-Negotiable Invariants

These rules stay fixed across every stage:

1. The deterministic core must complete a full scenario with `AI_ENABLED=false`.
2. The state machine decides what to do. AI only decides how to say it.
3. Protocol text comes from structured local data only.
4. Image input and image output are always optional.
5. Life-threat branching must never depend on LLM or vision output alone.
6. `VisionTaskRouter`, `MultimodalInterpreter`, `ObservedFact`, and `TurnContext` are stable interfaces from MVP onward.

## 2. Effective Build Order

`PLAN.md` is correct on milestone shape, but implementation should follow this order:

1. `S0`: project bootstrap
2. `S1`: core domain models
3. `S2-lite`: minimal local protocol assets + JSON loading needed by deterministic tests
4. `S3`: deterministic decision engine
5. `S4`: end-to-end non-AI demo gate
6. `S2-extended`: Room, visual catalog polish, repository hardening not required for the S4 gate
7. `S5`: audio
8. `S6`: image input transport
9. `S7`: LLM personalization + validator
10. `S8`: multimodal interpretation
11. `S9`: demo hardening
12. `S10`: hybrid vision stretch

Reason:

- `S3` needs protocol/tree fixtures, but it does not need the full repository layer completed first.
- The fastest path to a safe demo is to unblock deterministic routing and UI as early as possible.
- Anything that does not directly help reach `S4` should be deferred until the `mvp-deterministic` line is stable.

## 3. Stage Gates

Each stage is only done when its gate is true.

### Gate A: Contracts Frozen

Applies after `S1`.

- `UserTurn`
- `ObservedFact`
- `TurnContext`
- `DialogueState`
- `Protocol`
- `Tree`
- `LlmRequest`
- `LlmResponse`

After this point, contract changes require explicit review because they affect every higher layer.

### Gate B: Deterministic Engine Proven

Applies after `S3`.

- Burn scenario enters the burn tree and resolves `cool_water`
- Collapse scenario enters `CollapsedPersonEntryTree.scene_safe`
- Interruption routing preserves step context for clarification
- Life-threat interruption forces re-triage

### Gate C: Demo Safe Line

Applies after `S4`.

- Full burn and collapse walkthroughs complete with `AI_ENABLED=false`
- UI can advance, repeat, and re-triage without any AI path
- This commit must be preserved as the rollback-safe baseline

## 4. Current Repo Position

Current repository state is closest to pre-`S0 complete`:

- Android skeleton exists
- Compose entrypoint exists
- package tree from `ARCHITECTURE.md` is not created yet
- protocol assets do not exist yet
- deterministic domain logic does not exist yet

This means the immediate priority is not AI integration. It is `S0 -> S1 -> S2-lite -> S3 -> S4`.

## 5. Subagent Strategy

Do not parallelize everything from the start.

### Before `S1` is frozen

Single owner only.

Reason:

- model signatures will still move
- parallel edits create churn in every file above the model layer

### After `S1` is frozen

Parallel work is useful if ownership is explicit.

Recommended ownership split:

1. Core contracts owner
   - `domain/model/*`
   - `ai/model/*`
   - approves interface changes only
2. Data and assets owner
   - `assets/protocols/*`
   - `assets/visuals/*`
   - `data/protocol/*`
   - `data/visual/*`
3. Deterministic engine owner
   - `domain/nlu/*`
   - `domain/state/*`
   - `domain/safety/*`
4. Presentation owner
   - `presentation/*`
   - `ui/*`
5. Device I/O owner
   - `audio/*`
   - `imaging/*`
6. AI owner
   - `ai/melange/*`
   - `ai/orchestrator/*`
   - `ai/prompt/*`

### Rules for parallel execution

Every delegated task must include:

- owned files
- files that are read-only for that worker
- exact acceptance test or scenario
- reminder that the worker is not alone in the repo and must not revert others' work

### Recommended timing

- `S0-S1`: no subagents
- `S2-lite-S4`: one worker on data/assets, one worker on deterministic engine, main owner on integration
- `S5-S8`: audio, imaging, and AI can move in parallel once `S4` is stable
- `S9+`: use workers for verification and polish, not core contract rewrites

## 6. Context Management

Long documents should not be re-read in full on every task.

Keep one short execution log in the repo and update it at the end of each stage. The log should contain:

- current stage
- frozen contracts
- passed tests
- open blockers
- next unlock condition
- rollback point

Recommended file:

- `docs/EXECUTION_LOG.md`

### What stays in working context every time

These are the project rules that should always remain active:

1. AI optional
2. decision plane and response plane separated
3. canonical-first protocol sourcing
4. image optional
5. no life-threat confirmation from AI alone

### What gets summarized instead of repeated

- detailed schema examples from `ARCHITECTURE.md`
- stage checklists from `PLAN.md`
- earlier implementation history once tests already lock behavior

## 7. Practical Working Rhythm

For each stage:

1. define acceptance tests first
2. implement the thinnest path that satisfies those tests
3. keep interfaces stable
4. update execution log
5. only then move to the next stage

This repo should avoid premature polish in early stages.

Examples of work that should wait until after `S4` unless they unblock a test:

- Room schema expansion
- asset catalog variants
- visual polish beyond functional scanning
- specialized model support
- semantic retrieval

## 8. Immediate Next Actions

Use this exact near-term sequence:

1. Finish `S0`
   - dependencies
   - manifest permissions
   - package tree
   - note Melange procurement path
2. Freeze `S1`
   - create all core pure-Kotlin models
   - add serialization round-trip tests
3. Do `S2-lite`
   - create minimal protocol/tree JSON
   - create JSON loading and lookup
4. Finish `S3`
   - NLU
   - EntryTreeRouter
   - pure `ProtocolStateMachine`
   - InterruptionRouter
   - DialogueStateManager
5. Reach `S4`
   - minimal Compose screens
   - ViewModel reducer
   - AI disabled path only

## 9. Stop Conditions

Pause and re-evaluate if any of these happen:

- a proposed AI path changes deterministic branching
- a stage above `S4` blocks the ability to demo with `AI_ENABLED=false`
- multiple modules need the same contract change after `S1` freeze
- image analysis starts affecting life-threat slots without confirmation

When one of these occurs, fix the architecture issue first instead of continuing feature work.
