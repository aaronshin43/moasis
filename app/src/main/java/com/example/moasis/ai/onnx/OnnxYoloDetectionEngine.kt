package com.example.moasis.ai.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.moasis.ai.model.DetectedObject
import com.example.moasis.ai.model.NormalizedBoundingBox
import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.ai.orchestrator.VisionDetectionEngine
import com.example.moasis.domain.model.VisionTaskType
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * YOLOE detection engine backed by ONNX Runtime.
 *
 * Completely independent from the Zetic / Melange stack, so loading this
 * model does not compete for the NPU memory budget used by Qwen + Embedding.
 * By default inference runs on CPU (XNNPACK / internal threadpool); this is
 * sufficient for single-frame kit-detection use cases.
 *
 * The engine is designed for on-demand use: create → detect → close.
 * [ScopedKitDetectionEngine] wraps this lifecycle.
 */
class OnnxYoloDetectionEngine(
    private val appContext: Context,
    private val modelAssetName: String,
) : VisionDetectionEngine, AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    // Resolved from the model's first input tensor on load.
    private var inputWidth: Int = 640
    private var inputHeight: Int = 640

    /** Whether the named asset actually exists in the APK. Checked once, cached. */
    private val assetExists: Boolean by lazy {
        if (modelAssetName.isBlank()) return@lazy false
        runCatching { appContext.assets.open(modelAssetName).close() }.isSuccess
    }

    // ── lifecycle ──────────────────────────────────────────────────────

    override fun isConfigured(): Boolean = modelAssetName.isNotBlank() && assetExists

    override fun isPreparedInMemory(): Boolean = session != null

    /**
     * Load the ONNX model from app assets into an [OrtSession].
     * Safe to call repeatedly — returns immediately if already loaded.
     */
    override suspend fun prepareIfNeeded(): Result<Unit> = runCatching {
        if (session != null) return Result.success(Unit)
        val bytes = appContext.assets.open(modelAssetName).use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
        }
        val ortSession = env.createSession(bytes, opts)

        // Read input shape to know the expected image size.
        val inputInfo = ortSession.inputInfo.values.firstOrNull()
        val tensorInfo = inputInfo?.info as? ai.onnxruntime.TensorInfo
        val shape = tensorInfo?.shape  // typically [1, 3, H, W]
        if (shape != null && shape.size == 4) {
            inputHeight = shape[2].toInt()
            inputWidth = shape[3].toInt()
        }

        session = ortSession
        Log.d(TAG, "ONNX YOLOE session ready (${inputWidth}x${inputHeight}) from $modelAssetName")
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
        Log.d(TAG, "ONNX YOLOE session closed.")
    }

    // ── detection ──────────────────────────────────────────────────────

    override suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult> = runCatching {
        val ortSession = session ?: error("ONNX session not loaded. Call prepareIfNeeded() first.")

        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: error("Could not decode image at $imagePath")

        val inputTensor = createInputTensor(bitmap)
        var outputs: OrtSession.Result? = null
        try {
            val inputName = ortSession.inputNames.firstOrNull() ?: "images"
            outputs = ortSession.run(mapOf(inputName to inputTensor))

            // YOLOE output: typically one tensor with shape [1, N, C] or [1, C, N]
            val outputTensor = outputs.firstOrNull()?.value as? OnnxTensor
                ?: error("No output tensor from ONNX YOLOE")
            val outputShape = outputTensor.info.shape  // long[]
            val floats = outputTensor.floatBuffer.let { buf ->
                FloatArray(buf.remaining()).also { buf.get(it) }
            }

            val rawDetections = parseOutput(floats, outputShape)
            val deduplicated = nonMaxSuppression(rawDetections)
            val filtered = deduplicated
                .filter { it.label in allowedLabelsFor(taskType) }
                .filter { it.confidence >= confidenceThresholdFor(taskType) }
                .sortedByDescending { it.confidence }
                .take(MAX_RESULTS)

            Log.d(TAG, "ONNX YOLOE task=$taskType objects=${filtered.size}")
            VisionDetectionResult(
                objects = filtered,
                rawObjects = deduplicated.sortedByDescending { it.confidence }.take(MAX_RESULTS),
            )
        } finally {
            runCatching { inputTensor.close() }
            runCatching { outputs?.close() }
        }
    }

    // ── input preprocessing ────────────────────────────────────────────

    private fun createInputTensor(bitmap: Bitmap): OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // NCHW layout, normalized to [0, 1]
        val channelSize = inputWidth * inputHeight
        val buffer = FloatBuffer.allocate(3 * channelSize)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            buffer.put(index, ((pixel shr 16) and 0xFF) / 255f)                 // R
            buffer.put(channelSize + index, ((pixel shr 8) and 0xFF) / 255f)    // G
            buffer.put(channelSize * 2 + index, (pixel and 0xFF) / 255f)        // B
        }
        buffer.rewind()

        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    // ── output parsing ─────────────────────────────────────────────────

    /**
     * Handles the common YOLOE output layouts:
     *  - Post-NMS segmentation: [1, 300, 38] — (x1, y1, x2, y2, score, classId, ...)
     *  - Channel-first: [1, C, N] where C < N — xywh + class scores
     *  - Proposal-first: [1, N, C] where C < N — xywh + class scores
     */
    private fun parseOutput(values: FloatArray, shape: LongArray): List<DetectedObject> {
        if (shape.size != 3) return emptyList()
        val dim1 = shape[1].toInt()
        val dim2 = shape[2].toInt()

        return when {
            // Post-NMS YOLOE segmentation output: [1, 300, 38]
            dim1 == 300 && dim2 == 38 -> parsePostNms(values, dim1, dim2)
            // Channel-first: [1, C, N] where C is small (box coords + classes)
            dim1 in 5..256 && dim2 > dim1 -> parseChannelFirst(values, dim1, dim2)
            // Proposal-first: [1, N, C]
            dim2 in 5..256 && dim1 > dim2 -> parseProposalFirst(values, dim1, dim2)
            else -> emptyList()
        }
    }

    private fun parsePostNms(
        values: FloatArray,
        proposalCount: Int,
        channelCount: Int,
    ): List<DetectedObject> = buildList {
        for (i in 0 until proposalCount) {
            val off = i * channelCount
            val left = values[off]
            val top = values[off + 1]
            val right = values[off + 2]
            val bottom = values[off + 3]
            val score = values[off + 4]
            val classId = values[off + 5].toInt()
            if (score < SCORE_THRESHOLD) continue
            if (right <= left || bottom <= top) continue
            add(
                DetectedObject(
                    label = labelForClassId(classId),
                    confidence = score,
                    boundingBox = NormalizedBoundingBox(
                        left = clamp01(left / inputWidth),
                        top = clamp01(top / inputHeight),
                        right = clamp01(right / inputWidth),
                        bottom = clamp01(bottom / inputHeight),
                    ),
                ),
            )
        }
    }

    private fun parseChannelFirst(
        values: FloatArray,
        channelCount: Int,
        proposalCount: Int,
    ): List<DetectedObject> = buildList {
        for (p in 0 until proposalCount) {
            val x = values[p]
            val y = values[proposalCount + p]
            val w = values[proposalCount * 2 + p]
            val h = values[proposalCount * 3 + p]
            bestDetection(x, y, w, h, FloatArray(channelCount - 4) { c ->
                values[proposalCount * (c + 4) + p]
            })?.let(::add)
        }
    }

    private fun parseProposalFirst(
        values: FloatArray,
        proposalCount: Int,
        channelCount: Int,
    ): List<DetectedObject> = buildList {
        for (p in 0 until proposalCount) {
            val off = p * channelCount
            bestDetection(
                values[off], values[off + 1], values[off + 2], values[off + 3],
                values.copyOfRange(off + 4, off + channelCount),
            )?.let(::add)
        }
    }

    private fun bestDetection(
        x: Float, y: Float, w: Float, h: Float,
        classScores: FloatArray,
    ): DetectedObject? {
        if (classScores.isEmpty()) return null
        var bestIdx = -1
        var bestScore = 0f
        for (i in classScores.indices) {
            if (classScores[i] > bestScore) {
                bestScore = classScores[i]
                bestIdx = i
            }
        }
        if (bestIdx < 0 || bestScore < SCORE_THRESHOLD || bestIdx >= COCO_LABELS.size) return null
        val left = clamp01(x - w / 2f)
        val top = clamp01(y - h / 2f)
        val right = clamp01(x + w / 2f)
        val bottom = clamp01(y + h / 2f)
        if (right <= left || bottom <= top) return null
        return DetectedObject(
            label = labelForClassId(bestIdx),
            confidence = bestScore,
            boundingBox = NormalizedBoundingBox(left, top, right, bottom),
        )
    }

    // ── NMS ────────────────────────────────────────────────────────────

    private fun nonMaxSuppression(detections: List<DetectedObject>): List<DetectedObject> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<DetectedObject>()
        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            kept += current
            sorted.removeAll { candidate ->
                candidate.label == current.label &&
                        iou(current.boundingBox, candidate.boundingBox) >= IOU_THRESHOLD
            }
        }
        return kept
    }

    private fun iou(a: NormalizedBoundingBox?, b: NormalizedBoundingBox?): Float {
        if (a == null || b == null) return 0f
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val inter = (right - left) * (bottom - top)
        val union = (a.right - a.left) * (a.bottom - a.top) +
                (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    // ── helpers ────────────────────────────────────────────────────────

    private fun labelForClassId(classId: Int): String =
        COCO_LABELS.getOrNull(classId) ?: "class_$classId"

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    private fun allowedLabelsFor(taskType: VisionTaskType): Set<String> = when (taskType) {
        VisionTaskType.KIT_DETECTION -> KIT_LABELS
        VisionTaskType.STEP_VERIFICATION -> STEP_CHECK_LABELS
        VisionTaskType.OBJECT_PRESENCE_CHECK -> OBJECT_PRESENCE_LABELS
        else -> OBJECT_PRESENCE_LABELS
    }

    private fun confidenceThresholdFor(taskType: VisionTaskType): Float = when (taskType) {
        VisionTaskType.KIT_DETECTION -> 0.40f
        VisionTaskType.STEP_VERIFICATION -> 0.35f
        VisionTaskType.OBJECT_PRESENCE_CHECK -> 0.35f
        else -> SCORE_THRESHOLD
    }

    companion object {
        private const val TAG = "OnnxYoloDetector"
        private const val SCORE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_RESULTS = 8

        private val KIT_LABELS = setOf(
            "backpack", "handbag", "suitcase", "bottle", "cup",
            "scissors", "knife", "spoon", "book", "cell phone",
            "clock", "chair",
        )

        private val STEP_CHECK_LABELS = setOf(
            "person", "bottle", "cup", "scissors", "knife", "spoon",
            "chair", "couch", "bed", "sink", "toilet", "cell phone",
            "book", "clock", "backpack", "handbag",
        )

        private val OBJECT_PRESENCE_LABELS = setOf(
            "person", "bottle", "cup", "scissors", "knife", "spoon",
            "book", "clock", "chair", "couch", "bed", "sink", "toilet",
            "backpack", "handbag", "suitcase", "laptop", "keyboard",
            "mouse", "remote", "tv", "cell phone", "sports ball",
            "baseball glove",
        )

        private val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
            "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
        )
    }
}
