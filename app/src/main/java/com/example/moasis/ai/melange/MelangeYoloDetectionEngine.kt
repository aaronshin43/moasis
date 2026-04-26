package com.example.moasis.ai.melange

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.moasis.ai.model.DetectedObject
import com.example.moasis.ai.model.NormalizedBoundingBox
import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.ai.orchestrator.VisionDetectionEngine
import com.example.moasis.domain.model.VisionTaskType
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.ZeticMLangeModelMetadata
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor
import kotlin.math.max
import kotlin.math.min

class MelangeYoloDetectionEngine(
    private val modelManager: MelangeVisionModelManager,
) : VisionDetectionEngine {
    override suspend fun prepareIfNeeded(): Result<Unit> {
        return modelManager.getOrCreateSession().map { Unit }
    }

    override suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult> {
        return runCatching {
            val session = modelManager.getOrCreateSession().getOrThrow()
            val bitmap = BitmapFactory.decodeFile(imagePath)
                ?: error("Could not decode image at $imagePath")
            val inputTensor = createImageTensor(bitmap, session.metadata)
            val outputs = session.model.run(arrayOf(inputTensor))
            val result = parseDetections(outputs, session.metadata, taskType)
            Log.d(TAG, "YOLO detection task=$taskType objects=${result.objects.size}")
            result
        }
    }

    private fun createImageTensor(
        bitmap: Bitmap,
        metadata: ZeticMLangeModelMetadata,
    ): Tensor {
        val inputTensor = metadata.profileResult.inputTensors.firstOrNull()
            ?: error("YOLO metadata has no input tensors")
        val shape = inputTensor.shape
        require(shape.size == 4) { "Unsupported YOLO input shape: $shape" }

        val (width, height, nchw) = when {
            shape[1] == 3 -> Triple(shape[3], shape[2], true)
            shape.last() == 3 -> Triple(shape[2], shape[1], false)
            else -> error("Unsupported YOLO input layout: $shape")
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val input = FloatArray(width * height * 3)

        if (nchw) {
            val channelSize = width * height
            for (index in pixels.indices) {
                val pixel = pixels[index]
                input[index] = ((pixel shr 16) and 0xFF) / 255f
                input[channelSize + index] = ((pixel shr 8) and 0xFF) / 255f
                input[channelSize * 2 + index] = (pixel and 0xFF) / 255f
            }
        } else {
            var cursor = 0
            for (pixel in pixels) {
                input[cursor++] = ((pixel shr 16) and 0xFF) / 255f
                input[cursor++] = ((pixel shr 8) and 0xFF) / 255f
                input[cursor++] = (pixel and 0xFF) / 255f
            }
        }

        return Tensor.of(
            input,
            if (DataType.from(inputTensor.dtype) == DataType.Float16) DataType.Float16 else DataType.Float32,
            shape.toIntArray(),
        )
    }

    private fun parseDetections(
        outputs: Array<Tensor>,
        metadata: ZeticMLangeModelMetadata,
        taskType: VisionTaskType,
    ): VisionDetectionResult {
        val firstOutput = outputs.firstOrNull() ?: return VisionDetectionResult(emptyList())
        val shape = metadata.profileResult.outputTensors.firstOrNull()?.shape.orEmpty()
        if (shape.size != 3) {
            return VisionDetectionResult(emptyList())
        }

        val values = DataUtils.convertByteBufferToFloatArray(firstOutput.data.duplicate())
        val rawDetections = when {
            shape[1] == 300 && shape[2] == 38 ->
                parsePostNmsYoloeSegmentation(
                    values = values,
                    proposalCount = shape[1],
                    channelCount = shape[2],
                    metadata = metadata,
                )
            shape[1] in 5..256 && shape[2] > shape[1] ->
                parseChannelFirstYolo(values, channelCount = shape[1], proposalCount = shape[2])
            shape[2] in 5..256 && shape[1] > shape[2] ->
                parseProposalFirstYolo(values, proposalCount = shape[1], channelCount = shape[2])
            else -> emptyList()
        }

        val deduplicated = nonMaxSuppression(rawDetections)
        val filtered = deduplicated
            .filter { detection -> detection.label in allowedLabelsFor(taskType) }
            .filter { detection -> detection.confidence >= confidenceThresholdFor(taskType) }
            .sortedByDescending { it.confidence }
            .take(MAX_RESULTS)

        return VisionDetectionResult(
            objects = filtered,
            rawObjects = deduplicated.sortedByDescending { it.confidence }.take(MAX_RESULTS),
        )
    }

    private fun parsePostNmsYoloeSegmentation(
        values: FloatArray,
        proposalCount: Int,
        channelCount: Int,
        metadata: ZeticMLangeModelMetadata,
    ): List<DetectedObject> {
        val inputShape = metadata.profileResult.inputTensors.firstOrNull()?.shape.orEmpty()
        val inputWidth = when {
            inputShape.size == 4 && inputShape[1] == 3 -> inputShape[3].toFloat()
            inputShape.size == 4 && inputShape.last() == 3 -> inputShape[2].toFloat()
            else -> 640f
        }
        val inputHeight = when {
            inputShape.size == 4 && inputShape[1] == 3 -> inputShape[2].toFloat()
            inputShape.size == 4 && inputShape.last() == 3 -> inputShape[1].toFloat()
            else -> 640f
        }

        return buildList {
            for (proposalIndex in 0 until proposalCount) {
                val offset = proposalIndex * channelCount
                val left = values[offset]
                val top = values[offset + 1]
                val right = values[offset + 2]
                val bottom = values[offset + 3]
                val score = values[offset + 4]
                val classId = values[offset + 5].toInt()
                if (score < SCORE_THRESHOLD) {
                    continue
                }
                if (right <= left || bottom <= top) {
                    continue
                }

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
    }

    private fun parseChannelFirstYolo(
        values: FloatArray,
        channelCount: Int,
        proposalCount: Int,
    ): List<DetectedObject> {
        return buildList {
            for (proposalIndex in 0 until proposalCount) {
                val x = values[proposalIndex]
                val y = values[proposalCount + proposalIndex]
                val w = values[(proposalCount * 2) + proposalIndex]
                val h = values[(proposalCount * 3) + proposalIndex]
                addDetectionIfConfident(
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    classScores = FloatArray(channelCount - 4) { classIndex ->
                        values[(proposalCount * (classIndex + 4)) + proposalIndex]
                    },
                )?.let(::add)
            }
        }
    }

    private fun parseProposalFirstYolo(
        values: FloatArray,
        proposalCount: Int,
        channelCount: Int,
    ): List<DetectedObject> {
        return buildList {
            for (proposalIndex in 0 until proposalCount) {
                val offset = proposalIndex * channelCount
                val x = values[offset]
                val y = values[offset + 1]
                val w = values[offset + 2]
                val h = values[offset + 3]
                addDetectionIfConfident(
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    classScores = values.copyOfRange(offset + 4, offset + channelCount),
                )?.let(::add)
            }
        }
    }

    private fun addDetectionIfConfident(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        classScores: FloatArray,
    ): DetectedObject? {
        if (classScores.isEmpty()) {
            return null
        }
        var bestIndex = -1
        var bestScore = 0f
        for (index in classScores.indices) {
            if (classScores[index] > bestScore) {
                bestScore = classScores[index]
                bestIndex = index
            }
        }
        if (bestIndex < 0 || bestScore < SCORE_THRESHOLD || bestIndex >= COCO_LABELS.size) {
            return null
        }

        val left = clamp01(x - (w / 2f))
        val top = clamp01(y - (h / 2f))
        val right = clamp01(x + (w / 2f))
        val bottom = clamp01(y + (h / 2f))
        if (right <= left || bottom <= top) {
            return null
        }

        return DetectedObject(
            label = labelForClassId(bestIndex),
            confidence = bestScore,
            boundingBox = NormalizedBoundingBox(left, top, right, bottom),
        )
    }

    private fun labelForClassId(classId: Int): String {
        return COCO_LABELS.getOrNull(classId) ?: "class_$classId"
    }

    private fun nonMaxSuppression(detections: List<DetectedObject>): List<DetectedObject> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<DetectedObject>()
        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            kept += current
            sorted.removeAll { candidate ->
                candidate.label == current.label && iou(current.boundingBox, candidate.boundingBox) >= IOU_THRESHOLD
            }
        }
        return kept
    }

    private fun iou(
        first: NormalizedBoundingBox?,
        second: NormalizedBoundingBox?,
    ): Float {
        if (first == null || second == null) {
            return 0f
        }
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        if (right <= left || bottom <= top) {
            return 0f
        }
        val intersection = (right - left) * (bottom - top)
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    private fun allowedLabelsFor(taskType: VisionTaskType): Set<String> {
        return when (taskType) {
            VisionTaskType.KIT_DETECTION -> KIT_LABELS
            VisionTaskType.STEP_VERIFICATION -> STEP_CHECK_LABELS
            VisionTaskType.OBJECT_PRESENCE_CHECK -> OBJECT_PRESENCE_LABELS
            else -> OBJECT_PRESENCE_LABELS
        }
    }

    private fun confidenceThresholdFor(taskType: VisionTaskType): Float {
        return when (taskType) {
            VisionTaskType.KIT_DETECTION -> 0.40f
            VisionTaskType.STEP_VERIFICATION -> 0.35f
            VisionTaskType.OBJECT_PRESENCE_CHECK -> 0.35f
            else -> SCORE_THRESHOLD
        }
    }

    companion object {
        private const val TAG = "MelangeYoloDetector"
        private const val SCORE_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_RESULTS = 8

        private val KIT_LABELS = setOf(
            "backpack",
            "handbag",
            "suitcase",
            "bottle",
            "cup",
            "scissors",
            "knife",
            "spoon",
            "book",
            "cell phone",
            "clock",
            "chair",
        )

        private val STEP_CHECK_LABELS = setOf(
            "person",
            "bottle",
            "cup",
            "scissors",
            "knife",
            "spoon",
            "chair",
            "couch",
            "bed",
            "sink",
            "toilet",
            "cell phone",
            "book",
            "clock",
            "backpack",
            "handbag",
        )

        private val OBJECT_PRESENCE_LABELS = setOf(
            "person",
            "bottle",
            "cup",
            "scissors",
            "knife",
            "spoon",
            "book",
            "clock",
            "chair",
            "couch",
            "bed",
            "sink",
            "toilet",
            "backpack",
            "handbag",
            "suitcase",
            "laptop",
            "keyboard",
            "mouse",
            "remote",
            "tv",
            "cell phone",
            "sports ball",
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
