# MOASIS

Offline-first Android emergency response app.

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

Integrated runtime path:

- sponsor tooling: ZETIC Melange
- primary on-device LLM: Gemma4 E2B

By default the deterministic demo path remains active with `AI_ENABLED=false`.

To enable the real Melange-backed path, add these properties to your user Gradle properties file (`~/.gradle/gradle.properties`) or another injected Gradle property source:

```properties
MOASIS_AI_ENABLED=true
MOASIS_MELANGE_PERSONAL_KEY=your_personal_key
MOASIS_MELANGE_MODEL_NAME=your/model-name
MOASIS_MELANGE_MODEL_VERSION=1
MOASIS_MELANGE_MODEL_MODE=RUN_AUTO
```

Current runtime notes:

1. the app reads Melange credentials from Gradle properties and exposes them through `BuildConfig`
2. `MOASIS_MELANGE_MODEL_VERSION` is optional; use `-1` or omit it to let Melange resolve the latest published version
3. `MOASIS_MELANGE_MODEL_MODE` supports `RUN_AUTO`, `RUN_SPEED`, and `RUN_ACCURACY`
4. the app is pinned to `com.zeticai.mlange:mlange:1.6.1`
5. the first Melange model initialization may download runtime/model artifacts, so `INTERNET` permission is included for the AI-enabled path
6. when AI is enabled, the app now checks and prepares the Melange model during startup and shows progress in the UI
7. after the model is downloaded and initialized once, later launches should reuse the cached model artifacts
8. the current Melange runtime resolved by Gradle requires `minSdk 31`; this is stricter than the public setup page that still states `minSdk 24`
9. if Melange is not configured, the app falls back to deterministic guidance and keeps `AI_ENABLED=false` behavior effectively disabled in the UI

Procurement and model note:

1. confirm the exact Gemma4 E2B model identifier provisioned in the Melange dashboard for the demo device
2. verify first-run download and warmup on the physical target device before the demo
3. keep the app functional with `AI_ENABLED=false` as the rollback path until on-device inference is verified end to end

## Play Testing

If you need Melange paths that depend on Google Play delivery, use Play internal testing instead of a locally installed debug APK.

- guide: [docs/PLAY_INTERNAL_TESTING.md](D:/03_Coding/moasis/docs/PLAY_INTERNAL_TESTING.md)
