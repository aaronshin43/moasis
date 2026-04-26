# YOLOE ONNX Runtime 테스트 가이드

YOLOE 모델을 Zetic이 아닌 ONNX Runtime(CPU)으로 돌리는 구조입니다.
Zetic NPU에는 Qwen + Embedding만 상주하고, YOLOE는 완전히 별도 런타임에서 동작합니다.

---

## 1단계: YOLOE 모델을 ONNX로 export

Python 환경에서 실행합니다. ultralytics가 설치되어 있어야 합니다.

```bash
pip install ultralytics
```

```python
from ultralytics import YOLO

# seg(segmentation) 계열을 사용해야 합니다.
# 앱의 파서가 seg 모델의 post-NMS 출력 shape [1, 300, 38]에 맞춰져 있습니다.
model = YOLO("yoloe-11s-seg.pt")
model.export(format="onnx", imgsz=640, simplify=True)
```

실행하면 같은 디렉토리에 `yoloe-11s-seg.onnx` 파일이 생성됩니다.

> **모델 선택 참고:**
> - `yoloe-11s-seg.pt`: YOLO11 계열, 가벼움, 모바일 권장 (~25MB)
> - `yoloe-v8s-seg.pt`: YOLOv8 계열, 호환 가능
> - **반드시 seg(segmentation) 변종을 사용하세요.** 일반 detection 모델은 출력 shape이 달라서 파서가 처리 못합니다.
> - MVP에서는 `yoloe-11s-seg` 권장

---

## 2단계: ONNX 파일을 프로젝트에 넣기

생성된 `.onnx` 파일을 아래 경로에 복사합니다:

```
app/src/main/assets/yoloe_s.onnx
```

파일명은 자유이지만, 3단계에서 설정한 이름과 일치해야 합니다.

> **주의:** 파일 크기가 25~50MB이므로 git에 직접 올리지 마세요.
> `.gitignore`에 `*.onnx`를 추가하고, 팀 내 공유 드라이브나 별도 경로로 전달하세요.

---

## 3단계: gradle.properties 설정

`gradle.properties` 파일에 아래 두 줄을 추가합니다:

```properties
MOASIS_VISION_ENABLED=true
MOASIS_VISION_ONNX_ASSET=yoloe_s.onnx
```

`MOASIS_VISION_ONNX_ASSET`의 값은 2단계에서 assets에 넣은 파일명과 동일해야 합니다.

> 기존 Zetic vision 관련 속성들(`MOASIS_VISION_PERSONAL_KEY`, `MOASIS_VISION_MODEL_NAME` 등)은
> 더 이상 사용하지 않습니다. 남아있어도 무시되지만, 정리해도 됩니다.

---

## 4단계: 빌드 및 실행

```bash
./gradlew :app:assembleDebug
```

Android Studio에서 Sync → Run으로도 됩니다.

---

## 5단계: 동작 확인

### logcat 필터

```
tag:ScopedKitDetector OR tag:OnnxYoloDetector OR tag:MainActivity
```

### 정상 동작 시 로그 순서

앱 시작 시:
```
MainActivity: YOLO detector enabled=true runtime=ONNX asset=yoloe_s.onnx
```

이미지 첨부로 kit detection 실행 시:
```
OnnxYoloDetector: ONNX YOLOE session ready (640x640) from yoloe_s.onnx
OnnxYoloDetector: ONNX YOLOE task=KIT_DETECTION objects=3
ScopedKitDetector: Kit detector ONNX session released.
```

핵심 확인 포인트:
- `session ready` → `objects=N` → `session released` 순서로 나오는지
- "released" 로그가 매 detect 후 반드시 나오는지 (메모리 누수 방지)
- Qwen/Embedding 응답이 detection 전후로 정상인지

### 에러 시 로그

```
ScopedKitDetector: Kit detector load failed: ...
```
→ assets 폴더에 .onnx 파일이 없거나 파일명이 다른 경우

---

## 구조 요약

```
Zetic NPU (항상 상주)          ONNX Runtime CPU (on-demand)
┌──────────────────┐           ┌──────────────────┐
│ Qwen (LLM)       │           │ YOLOE            │
│ Embedding         │           │ detect() 때만 로드 │
└──────────────────┘           │ 끝나면 즉시 해제   │
                               └──────────────────┘
```

- 두 런타임은 완전히 독립. 서로 메모리 경합 없음.
- YOLOE는 detect() 한 번에 로드 → 추론 → 해제. idle 시 메모리 0.
- ONNX Runtime은 CPU로 동작 (NPU 접근 안 함).

---

## 파일 목록

| 파일 | 설명 |
|------|------|
| `app/src/main/java/.../ai/onnx/OnnxYoloDetectionEngine.kt` | ONNX Runtime 기반 YOLOE 추론 엔진 (신규) |
| `app/src/main/java/.../ai/melange/ScopedKitDetectionEngine.kt` | on-demand lifecycle 관리 (Zetic→ONNX로 전환) |
| `app/src/main/java/.../MainActivity.kt` | 와이어링 (ONNX asset 기반으로 변경) |
| `app/build.gradle.kts` | onnxruntime-android 의존성 추가, .onnx 비압축 설정 |
| `app/src/test/.../ScopedKitDetectionEngineTest.kt` | 단위 테스트 (lifecycle 검증) |

---

## 트러블슈팅

**Q: 빌드는 되는데 detect 시 크래시**
→ `.onnx` 파일이 assets에 없거나 파일명이 `gradle.properties`와 다름. logcat에서 에러 메시지 확인.

**Q: 추론은 되는데 아무것도 검출 안 됨**
→ YOLOE 변종에 따라 출력 텐서 shape이 다를 수 있음. logcat에서 shape 관련 로그 확인.
→ confidence threshold가 높을 수 있음 (KIT_DETECTION: 0.40).

**Q: 추론이 너무 느림 (5초 이상)**
→ 모델이 너무 큰 경우 (m/l 변종). s 변종으로 교체.
→ `OrtSession.SessionOptions`에서 `setIntraOpNumThreads(4)` 값을 기기 코어 수에 맞게 조정.

**Q: 기존 Qwen/Embedding이 이상해짐**
→ 정상적으로는 영향 없음. RAM이 부족한 기기에서 동시 사용 시 시스템이 백그라운드 프로세스를 kill할 수 있음.
