# PLAN.md

해커톤용 오프라인 AI 응급처치 가이드 앱(MOASIS) 구현 계획서.
근거 문서: `CLAUDE.md`, `docs/ARCHITECTURE.md`. 두 문서와 충돌이 생기면 `ARCHITECTURE.md`를 따른다.

---

## 0. 설계 원칙 (모든 스테이지에서 강제)

이 원칙은 모든 단계의 체크포인트보다 우선한다. 어떤 단계에서든 위반이 발견되면 그 단계는 미완료로 간주한다.

1. **AI-Optional 원칙.** 어떤 시점에 빌드를 잘라내도, LLM/비전 모델 없이 deterministic 코어만으로 핵심 응급 안내가 끝까지 진행되어야 한다. (스테이지 4가 끝나는 순간 이 조건이 충족되어야 한다.)
2. **Decision Plane vs Response Plane 분리.** "무엇을 할지"는 State Machine이, "어떻게 말할지"만 LLM이 담당한다. LLM이 step을 추가/생략/재배열하는 코드 경로는 절대 만들지 않는다.
3. **Canonical-First.** 프로토콜 본문은 항상 구조화된 로컬 데이터(JSON/SQLite)에서만 나온다. LLM 출력은 `must_keep_keywords`/`forbidden_keywords` Validator 통과 후에만 사용자에게 보인다.
4. **이미지는 항상 optional.** 입력에서도 출력(visual aid)에서도 이미지는 보조 정보다. 이미지가 없거나 분석이 실패해도 동일하게 동작해야 한다.
5. **MVP에서도 Hybrid 인터페이스를 먼저 깐다.** `VisionTaskRouter`, `MultimodalInterpreter`, `ObservedFact`, `TurnContext`는 MVP에서부터 인터페이스로 존재한다. MVP 구현은 `GemmaMultimodalInterpreter` 단 하나지만, 후속 `HybridMultimodalInterpreter`로 갈아끼울 수 있는 형태여야 한다.
6. **빌드 순서.** data models → protocol/state logic → repositories & local assets → AI adapters & validators → UI wiring. 한 단계가 자체 검증을 통과하기 전에 다음 단계의 코드를 작성하지 않는다.

---

## 스테이지 개요

| 스테이지 | 목표 | AI 의존? | 끝났을 때 보이는 것 |
|---|---|---|---|
| S0 | 프로젝트 골격, 의존성, 패키지 트리 | 없음 | 빌드 통과 + 빈 화면 |
| S1 | 핵심 도메인 데이터 모델 | 없음 | 단위 테스트 통과 |
| S2 | 로컬 프로토콜 KB + 리포지토리 | 없음 | JSON 로드/조회 테스트 |
| S3 | Deterministic 결정 엔진 | 없음 | NLU→EntryTree→StateMachine 시나리오 단위 테스트 |
| **S4 (게이트)** | **AI 없는 End-to-End 데모** | **없음** | **음성 없이 텍스트만으로도 화상 시나리오 완주** |
| S5 | 오디오 I/O (STT/TTS, barge-in) | 없음 | 음성으로 시나리오 완주 |
| S6 | 이미지 I/O (camera/gallery, UserTurn) | 없음 | 이미지 첨부 turn 처리 (분석 없이 통과) |
| S7 | Melange + Gemma 개인화 + Validator | LLM | 같은 step이 사용자 맥락에 맞게 톤 변화 |
| S8 | 멀티모달 해석 (GemmaMultimodalInterpreter + ObservationMerger) | LLM | 이미지 첨부 turn에서 ObservedFact 생성/병합 |
| S9 | 시나리오 폴리싱 + 데모 안정화 | LLM | 발표용 4개 시나리오 안정 동작 |
| S10 (stretch) | Hybrid Vision 확장 | LLM+Vision | KIT_DETECTION/STEP_VERIFICATION 분리 |

> **데모 안전선:** 발표 직전까지 S7이 불안정하면 LLM 호출을 비활성화하고 S4 모드로 demo를 진행할 수 있어야 한다. 이는 코드 옵션(`AI_ENABLED` 토글) 형태로 보장한다.

---

## S0. 프로젝트 부트스트랩

### 목표
빌드 환경 정리, 패키지 트리 확정, Melange/Gemma 의존성 사전 조사.

### 작업
- `app/build.gradle.kts`에 의존성 추가:
  - `androidx.lifecycle:lifecycle-viewmodel-compose`
  - `androidx.lifecycle:lifecycle-runtime-compose`
  - `kotlinx-coroutines-android`
  - `kotlinx-serialization-json`
  - `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (Stage 2부터 사용)
  - `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view` (Stage 6부터 사용)
- `gradle/libs.versions.toml` 갱신.
- `AndroidManifest.xml`에 권한 선언 (RECORD_AUDIO, CAMERA, READ_MEDIA_IMAGES, INTERNET 제거 — 오프라인이 핵심).
- 패키지 골격을 `ARCHITECTURE.md` §16에 맞춰 빈 디렉토리로 생성: `ui/screen`, `ui/component`, `presentation`, `audio`, `imaging`, `domain/{model,nlu,state,safety,usecase}`, `data/{protocol,visual,local}`, `ai/{melange,orchestrator,prompt,model}`, `assets/{protocols,visuals/images,demo_inputs,knowledge}`.
- Melange SDK / Gemma4 E2B 모델 파일 입수 경로를 README나 별도 메모에 기록 (실제 통합은 S7에서).

### 체크포인트
- [ ] `./gradlew :app:assembleDebug` 통과.
- [ ] 앱 실행 시 빈 화면이 떠야 함 (크래시 없음).
- [ ] 패키지 디렉토리 구조가 `ARCHITECTURE.md` §16과 1:1로 일치.
- [ ] Melange SDK 패키지 이름 / 버전 / 모델 다운로드 절차가 한 줄로 적혀 있음.

---

## S1. 핵심 도메인 데이터 모델

### 목표
모든 상위 레이어가 의존할 immutable 데이터 모델을 먼저 픽스. 이 단계에서 모델 시그니처가 흔들리면 뒤가 다 흔들린다.

### 작업
`domain/model/`에 다음을 작성 (모두 `data class` 또는 `sealed class`, `kotlinx.serialization` 적용):

- `UserTurn` (`ARCHITECTURE.md` §11.1)
- `ObservedFact`, `FactSource`, `VisionTaskType`, `TurnContext` (§11.2)
- `DialogueState` sealed class: `EntryMode`, `ProtocolMode`, `QuestionMode`, `ReTriageMode`, `Completed` (§11.3)
- `UiState`, `VisualAid`, `VisualAidType`, `ChecklistItem` (§11.4)
- `AppEvent` sealed class (§11.5)
- `EntryIntent`, `DomainIntent` enums (§6.4 예시 기반)
- `Protocol`, `ProtocolStep`, `AssetRef`, `Tree`, `TreeNode`, `Transition`, `Route` (§12)
- `LlmRequest`, `LlmResponse` (`ai/model/`, §13)

### 체크포인트
- [ ] 모든 모델이 `domain/model/` 또는 명시된 패키지에 있음 (잘못된 위치 금지).
- [ ] 모든 모델이 `kotlinx.serialization` 직렬화 가능 (라운드트립 단위 테스트 1개).
- [ ] `DialogueState`, `AppEvent`는 sealed로 when-exhaustive가 컴파일 타임에 강제됨.
- [ ] 어떤 모델도 Android SDK 타입에 의존하지 않음 (이 레이어는 순수 Kotlin).

---

## S2. 로컬 지식베이스 + 리포지토리

### 목표
프로토콜 본문과 비주얼 에셋의 단일 소스. LLM 없이도 step 텍스트를 가져올 수 있는 deterministic 경로.

### 작업
- `assets/protocols/`에 최소 3개 트리/프로토콜 JSON 작성:
  - `entry_general_emergency.json`
  - `collapsed_person_entry.json` (§12.2 예시)
  - `burn_tree.json` + `burn_second_degree_general` 프로토콜 (§12.3 예시)
  - `bleeding_tree.json` (간단 버전이라도)
- `assets/visuals/asset_catalog.json` + 플레이스홀더 이미지 2~3장 (`burn_cool_water_arm_01.webp`, `bandage_wrap_arm_01.webp`).
- `data/protocol/JsonProtocolDataSource.kt`: assets에서 JSON 읽어 도메인 모델로 파싱.
- `data/protocol/ProtocolRepository.kt`: `getTree(treeId)`, `getProtocol(protocolId)`, (FTS는 stretch).
- `data/visual/AssetCatalogDataSource.kt`, `data/visual/VisualAssetRepository.kt`: `getAssetsForStep(protocolId, stepId)`, `resolveAsset(assetId)`.
- `data/local/AppDatabase.kt`, `SessionDao.kt`: 마지막 state, slot 캐시, turn 히스토리 (Room 최소 스키마).

### 체크포인트
- [ ] JSON 스키마가 `ARCHITECTURE.md` §12와 일치 (`tree_id`, `start_node`, `nodes[].transitions` 등).
- [ ] 단위 테스트: `ProtocolRepository.getProtocol("burn_second_degree_general")`이 step 2개를 정확히 반환.
- [ ] 단위 테스트: `VisualAssetRepository.getAssetsForStep("burn_second_degree_general", "cool_water")`이 1개 이상의 asset을 반환.
- [ ] 누락된 `protocol_id` 조회 시 깔끔한 `Result.failure` 또는 `null` (예외 던지지 않음).
- [ ] 이미지 파일이 없는 step도 빈 `asset_refs`로 정상 동작.

---

## S3. Deterministic 결정 엔진

### 목표
"AI 없이도 다음 step이 결정된다"를 코드로 증명. 이 단계가 끝나면 시나리오 단위 테스트가 시퀀스를 끝까지 돌릴 수 있다.

### 작업
`domain/nlu/`:
- `RegexIntentMatcher.kt`: §6.4 우선순위 (regex > keyword > phrase normalization). 최소 패턴: `PERSON_COLLAPSED`, `BURN`, `BLEEDING`, `BREATHING_PROBLEM`, `CHEST_PAIN`, `CHOKING`.
- `SlotExtractor.kt`: 위치(`arm`/`hand`/`face`), 환자유형(`adult`/`child`), 응답(`yes`/`no`) 등 핵심 슬롯.
- `NluRouter.kt`: 위 둘을 묶어 `entryIntent + domainHints + slots + confidence` 반환.

`domain/state/`:
- `EntryTreeRouter.kt`: §6.5 라우팅 규칙 — clear injury → 도메인 트리 직행, ambiguous → GeneralEntryTree, collapse → CollapsedPersonEntryTree.
- `ProtocolStateMachine.kt`: §6.6 노드 타입(`question`/`instruction`/`route`/`router`/`checklist`/`terminal`) 핸들링. 슬롯 충족 검사 후 다음 노드 결정. **순수 함수 형태로** (입력: 현재 state + event, 출력: 새 state + side-effect 디스크립터).
- `InterruptionRouter.kt`: §6.8 / §15.1 우선순위 — life-threat keyword > control intent > clarification > out-of-domain. **life-threat 강제 재트리아지 룰을 반드시 1순위에 둔다.**
- `DialogueStateManager.kt`: 위 셋을 조합해 turn 단위 reduce 수행.
- `VisionTaskRouter.kt`: §6.5b 규칙 기반 라우팅 (현재 protocolId/stepId/text 기반). 이미지 분석 호출은 하지 않고 분류만 한다.
- `ObservationMerger.kt`: §6.6b — 텍스트/음성/이미지 결과를 하나의 turn 컨텍스트로 병합. 이 단계에서는 이미지 입력이 없는 케이스만 통과시킨다.

`domain/safety/`:
- `ResponseValidator.kt`: 인터페이스 + 더미 구현 (`canonicalText`를 그대로 통과). 실제 Validator 본체는 S7에서.

### 체크포인트
- [ ] 시나리오 테스트 1: 입력 "팔에 물집이 생겼어" → `BURN` 분류 → `BurnTree` 진입 → `burn_second_degree_general` 해결 → step `cool_water` 반환.
- [ ] 시나리오 테스트 2: 입력 "친구가 쓰러졌어" → `PERSON_COLLAPSED` → `CollapsedPersonEntryTree.scene_safe` 노드 진입.
- [ ] 시나리오 테스트 3: ProtocolMode 진행 중 입력 "숨을 못 쉬어" → InterruptionRouter가 `STATE_CHANGING_REPORT` 분류 → `ReTriageMode` 전환.
- [ ] 시나리오 테스트 4: ProtocolMode 진행 중 입력 "얼음 써도 돼?" → `CLARIFICATION_QUESTION` 분류 → 현재 stepIndex가 보존됨.
- [ ] 시나리오 테스트 5: "다음" → `CONTROL_INTENT.NEXT` → stepIndex+1.
- [ ] StateMachine은 LLM/네트워크에 의존하지 않음 (의존성 그래프 검증).

---

## S4. AI 없는 End-to-End 데모 (필수 게이트)

### 목표
**여기가 최대 안전선이다.** 이 시점에 LLM이 한 줄도 안 돌아도 시연이 가능해야 한다.

### 작업
- `presentation/EmergencyViewModel.kt`: `MutableStateFlow<UiState>` + `reduce(AppEvent)` 패턴. S3의 `DialogueStateManager`를 호출.
- `presentation/UiAction.kt`: `Next`, `Repeat`, `Back`, `CallEmergency`, `SubmitText`.
- `ui/screen/HomeScreen.kt`: 텍스트 입력 + "시작" 버튼.
- `ui/screen/ActiveProtocolScreen.kt`: 현재 step 텍스트, warning, checklist, visual aid, "다음/반복/긴급전화" 버튼 (§18.1 레이아웃).
- `ui/component/StepCard.kt`, `WarningBanner.kt`, `VisualAidStrip.kt` (§18.3 규칙: 1~2개, step 텍스트 아래, contentDescription 필수).
- `MainActivity.kt`: ViewModel을 컴포지션 트리에 주입.
- `AI_ENABLED` 컴파일/런타임 토글 (BuildConfig 또는 데이터 클래스). 이 단계에서는 항상 `false`.

### 체크포인트
- [ ] 텍스트로 "팔에 화상 입었어" 입력 → BurnTree 진입 → 첫 step 표시 → "다음" 누르면 다음 step 진행 → terminal 도달.
- [ ] 텍스트로 "친구가 쓰러졌어" 입력 → CollapsedPersonEntryTree의 첫 질문(`scene_safe`) 표시 → "예/아니오" 응답으로 분기.
- [ ] step 카드에 visual aid 이미지가 표시됨 (없는 step도 layout 깨지지 않음).
- [ ] step 진행 중 새 입력 "숨을 못 쉬어" → 즉시 ReTriage 화면으로 전환.
- [ ] `AI_ENABLED=false` 상태에서 위 4개 시나리오가 모두 끝까지 동작.
- [ ] **이 시점에 git tag `mvp-deterministic` 부여.** 이후 어떤 회귀가 생겨도 이 태그로 demo 가능.

---

## S5. 오디오 I/O

### 목표
음성으로 동일한 시나리오를 완주.

### 작업
- `audio/AndroidSpeechRecognizer.kt`: `SpeechRecognizer` offline preferred. partial vs final 결과 구분, final만 state transition에 사용 (§6.1).
- `audio/AndroidTtsEngine.kt`: `TextToSpeech` 초기화, 발화 큐, utteranceId 추적.
- `audio/AudioController.kt`: STT/TTS 라이프사이클 + barge-in (TTS 중 partial speech detect → 즉시 stop).
- `audio/VoiceEvent.kt` → `AppEvent.VoiceTranscript`로 매핑.
- `ui/component/VoiceStatusBar.kt`: 듣는중/말하는중 표시.
- 권한 런타임 요청 (RECORD_AUDIO).
- STT 실패 fallback: 텍스트 입력으로 자동 전환 + "잘 못 들었어요" canned phrase (§19.3).

### 체크포인트
- [ ] 음성으로 "친구가 쓰러졌어" 인식 → 동일 시나리오 진입.
- [ ] TTS가 첫 step을 발화하는 도중 사용자가 "다음"이라고 말하면 즉시 발화 멈추고 다음 step.
- [ ] 마이크 권한 거부 시 텍스트 입력만으로 정상 동작.
- [ ] STT 결과가 빈 문자열이거나 confidence 낮은 경우 같은 질문을 다시 묻는다.

---

## S6. 이미지 I/O

### 목표
이미지를 첨부할 수 있고, 첨부해도/안 해도 동일하게 동작. 이 단계에선 **분석을 하지 않는다** — 이미지 URI를 `UserTurn`에 실어 나르기만 한다.

### 작업
- `imaging/CameraCaptureManager.kt`: CameraX `ImageCapture` 단발 촬영.
- `imaging/GalleryPickerManager.kt`: `ActivityResultContracts.PickVisualMedia`.
- `imaging/ImageInputController.kt`: URI → 내부 캐시 디렉토리 복사 → 안전한 내부 ref 반환 (§6.1b).
- ViewModel: 입력 시 텍스트/음성/이미지를 하나의 `UserTurn`으로 조립.
- `ui/screen/ActiveProtocolScreen.kt`: 카메라/갤러리 첨부 버튼 추가, 첨부된 사진 썸네일 표시.
- `VisionTaskRouter`는 이미 S3에서 작성됨 — 여기선 호출만 연결, 분석 호출은 S8까지 no-op.

### 체크포인트
- [ ] 사진 첨부 후 "이거 봐줘" 입력 → 분석은 안 되지만 turn은 정상 처리되고 step 진행은 끊기지 않음 (안전 fallback 메시지).
- [ ] `VisionTaskRouter`가 turn에 따라 올바른 `VisionTaskType`을 반환 (단위 테스트로 검증).
- [ ] 이미지 첨부가 없을 때 동작은 S5와 100% 동일.
- [ ] 카메라 권한 거부 시 갤러리 fallback. 둘 다 거부 시 첨부 버튼 비활성화 (앱 크래시 X).

---

## S7. Melange + Gemma 개인화 + Validator (LLM 통합)

### 목표
LLM이 step 본문을 사용자 맥락(slot, panic_level, target_listener)에 맞게 다시 말한다. 단, **Validator를 통과한 텍스트만 사용자에게 도달**한다.

### 작업
- `ai/melange/MelangeModelManager.kt`: 모델 파일 로드, backend(CPU/GPU/NPU) 선택, warmup.
- `ai/melange/MelangeLlmEngine.kt`: `OnDeviceLlmEngine` 인터페이스 (§10.4) 구현. timeout/cancel/retry 정책.
- `ai/orchestrator/InferenceOrchestrator.kt`: 언제 LLM을 부를지 결정 (§6.9). 다음 step 이동/반복/canonical lookup은 LLM 호출하지 않음.
- `ai/prompt/PromptFactory.kt`: §13.1 personalize_step 요청, §13.2 answer_question 요청 빌더. JSON 출력 강제 system prompt.
- `domain/safety/ResponseValidator.kt` 본체: §14.2 — `must_keep_keywords` 포함 여부, `forbidden_keywords` 부재, 길이, 새 step 추가 여부 검사. 실패 시 §14.2 canned bridge + canonical_text fallback.
- `ViewModel`에 `AI_ENABLED=true` 분기 연결: deterministic이 다음 step을 결정 → Orchestrator가 personalize → Validator 통과 → TTS.
- `usecase/AnswerQuestionUseCase.kt`: clarification 질문 시 현재 step 컨텍스트 + 금지사항을 prompt에 주입, 응답 후 §15.2 resume 정책 (key action verb 포함 resume utterance).

### 체크포인트
- [ ] 같은 `cool_water` step이 `target_listener=caregiver`/`child`일 때 톤이 다르게 나옴.
- [ ] Validator 단위 테스트: `must_keep_keywords` 누락된 mock LLM 응답 → fallback이 발동하고 canonical_text가 사용됨.
- [ ] "얼음 써도 돼?" 질문 → §13.2 prompt 형식으로 호출 → 응답 후 동일 step의 핵심 동사("running water")를 포함한 resume 발화.
- [ ] LLM이 timeout(예: 3초) 시 canonical_text로 fallback, UX는 끊기지 않음.
- [ ] `AI_ENABLED=false` 토글로 즉시 S4 모드 복귀 가능.
- [ ] life-threat 분기는 절대 LLM 출력에 의존하지 않음 (코드 리뷰로 검증).

---

## S8. 멀티모달 해석

### 목표
이미지가 포함된 turn에서 Gemma가 직접 해석 → `ObservedFact`로 정규화 → state machine이 사용.

### 작업
- `ai/orchestrator/MultimodalInterpreter.kt` 인터페이스 (§10.4).
- `ai/orchestrator/GemmaMultimodalInterpreter.kt`: Gemma4 E2B에 image+text+TurnContext 전달, 구조화된 `ObservedFact[]` JSON 출력 강제.
- `ObservationMerger`(S3에서 만든 것) 본체: USER_REPORTED와 VISION_SUGGESTED 충돌 시 보존 + 재질문 트리거. life-threat 영향 슬롯은 VISION_SUGGESTED만으로 절대 확정하지 않음 (§6.6b).
- `usecase/AnalyzeImageTurnUseCase.kt`: VisionTaskRouter → MultimodalInterpreter → ObservationMerger 파이프라인.
- UI: 사용자가 올린 사진 썸네일 + "관찰됨: …" 형태의 suggested fact 표시 (확정 아님을 명시).

### 체크포인트
- [ ] 화상 사진 + "이게 어느 정도야?" → `INJURY_OBSERVATION` task로 라우팅 → ObservedFact 생성 → 화상 트리 슬롯에 후보로 들어감 (확정 아님).
- [ ] 붕대 사진 + "맞게 감았어?" + 진행 중인 bandage step → `STEP_VERIFICATION` task로 라우팅.
- [ ] 이미지 분석 실패(타임아웃/모델 오류) → step 진행은 멈추지 않고 "사진 분석은 실패했지만 계속 진행할게요" 메시지.
- [ ] life-threat 슬롯(`breathing_normal`, `responsive` 등)을 이미지만으로 결정하는 코드 경로가 없음 (검색으로 확인).
- [ ] `MultimodalInterpreter`가 인터페이스로 분리되어 있어, Mock 구현으로 단위 테스트 가능.

---

## S9. 시나리오 폴리싱 + 데모 안정화

### 목표
발표용 시연 시나리오를 골라 끝까지 안정적으로 돌린다.

### 작업
- 발표 시나리오 4개 확정 (§22.3 참고):
  1. "팔에 물집이 생겼어" → BurnTree 완주
  2. "친구가 쓰러졌어" → CollapsedPersonEntryTree → 분기
  3. step 진행 중 "얼음 써도 돼?" 끼어들기
  4. step 진행 중 "숨을 못 쉬어" 강제 재트리아지
  5. (선택) 응급키트 사진 + "여기서 뭘 쓸 수 있어?"
- 각 시나리오 통합 테스트 (`androidTest`)로 박제.
- TTS 발화 길이 조정 (§18.2 — 한 문장씩 끊기, warning 분리).
- Visual aid 누락된 step에 placeholder 처리.
- 저사양 디바이스 fallback: LLM 비활성화 모드를 설정 화면에서 토글 가능 (§20.3).
- 데모 영상/스크립트 작성용 노트.

### 체크포인트
- [ ] 4개 시나리오 통합 테스트 모두 통과.
- [ ] 비행기 모드(완전 오프라인)에서 모든 시나리오 동작.
- [ ] 콜드 스타트 후 첫 step까지 5초 이내 (LLM 없이) / 8초 이내 (LLM 포함).
- [ ] TTS 도중 barge-in으로 끼어들기 → 발표용 시나리오 3에서 자연스럽게 동작.
- [ ] 발표자가 1번부터 4번까지 끊김 없이 시연 가능 (리허설 1회 이상).

---

## S10. (Stretch) Hybrid Vision 확장

스테이지 9까지 안정적으로 끝났을 때만 진입. `ARCHITECTURE.md` §10.2 Profile B / §24.1 순서.

### 작업
- `ai/melange/MelangeVisionModelEngine.kt`: `VisionModelEngine` 인터페이스 (§10.4) 구현.
- `ai/orchestrator/HybridMultimodalInterpreter.kt`: VisionTaskRouter 결과에 따라 specialized vision 모델 또는 Gemma로 분기, 결과를 동일한 `ObservedFact`로 정규화.
- 우선 분리 대상: `KIT_DETECTION` (응급키트 인식 디텍터) → 그 다음 `STEP_VERIFICATION`.
- `GENERAL_MULTIMODAL_QA`와 컨텍스트 무거운 질문은 Gemma 그대로 유지.

### 체크포인트
- [ ] 인터페이스 변경 없이 `GemmaMultimodalInterpreter` ↔ `HybridMultimodalInterpreter` 교체 가능.
- [ ] 응급키트 사진의 KIT_DETECTION 응답 시간이 Gemma 단독 대비 단축됨 (정량 비교).
- [ ] vision 모델 로드 실패 시 Gemma로 자동 fallback.

---

## 횡단 작업 (모든 스테이지에서 병행)

- **테스트:** `domain/` 레이어는 JVM 단위 테스트로 커버, `presentation/` 이상은 instrumentation 또는 Compose UI 테스트.
- **로깅:** 모든 LLM 입출력은 디버그 빌드에서 로컬 파일로 기록 (배포 빌드는 제거). 발표 직전 디버깅에 결정적.
- **문서 업데이트:** S3, S7, S8 종료 시 `ARCHITECTURE.md`와 차이가 생기면 바로 동기화 (CLAUDE.md 규정).
- **`AI_ENABLED` 토글:** 모든 LLM 호출 지점에서 토글을 존중하는지 회귀 검사.
- **권한 UX:** 마이크/카메라 권한 거부 케이스를 단계별로 손으로 검증.

---

## 위험 / 완화

| 위험 | 영향 | 완화 |
|---|---|---|
| Melange + Gemma4 E2B 통합이 예상보다 오래 걸림 | S7 지연 | S4 게이트가 이미 통과되어 있으므로 데모는 가능. 토글로 즉시 우회. |
| 저사양 디바이스에서 LLM 추론이 느림 | UX 저하 | personalize 생략 모드(§20.3), TTS 먼저 canonical로 발화 후 personalize는 비동기 갱신. |
| STT 정확도가 낮음 | NLU 분기 실패 | 텍스트 입력 fallback 항상 노출, NLU의 keyword 사전을 풍부하게. |
| 이미지 분석이 잘못된 ObservedFact 생성 | 안전 위협 가능 | life-threat 슬롯은 VISION_SUGGESTED 단독 확정 금지 룰을 코드와 테스트로 강제. |
| 시연 직전 회귀 | 데모 실패 | `mvp-deterministic` git tag로 즉시 롤백 가능, S9 리허설 필수. |

---

## 한 줄 요약

S4(AI 없는 데모)를 가장 먼저 안전하게 도달시키고, 그 위에 음성→이미지→LLM→멀티모달을 쌓되, 어느 단계에서 멈춰도 시연이 가능한 형태를 유지한다.
