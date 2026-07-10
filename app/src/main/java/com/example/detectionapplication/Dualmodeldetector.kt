package com.example.detectionapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

/**
 * DualModelDetector - SIMPLIFIED VERSION
 * Runs both YOLO models with visible bounding boxes
 */
class DualModelDetector(private val context: Context) {

    companion object {
        private const val COCO_MODEL_FILE = "yolo_new.tflite"
        private const val CUSTOM_MODEL_FILE = "best_new.tflite"
        private const val COCO_LABELS_FILE = "class_names.json"
        private const val CUSTOM_LABELS_FILE = "class_names_best.json"

        private const val INPUT_SIZE = 320
        private const val CONF_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.5f
        private const val PHONE_FOV_DEG = 60.0f
    }

    private var cocoInterpreter: Interpreter? = null
    private var customInterpreter: Interpreter? = null

    private var cocoLabels: Map<Int, String> = emptyMap()
    private var customLabels: Map<Int, String> = emptyMap()

    init {
        try {
            loadLabels()
            loadModels()
            Log.i("DualModelDetector", "✓ Initialization complete")
        } catch (e: Exception) {
            Log.e("DualModelDetector", "Init failed", e)
            throw e
        }
    }

    // ── Label Loading ─────────────────────────────────────────────────────────

    private fun loadLabels() {
        try {
            val cocoJson = context.assets.open(COCO_LABELS_FILE).bufferedReader().use { it.readText() }
            val cocoData = JSONObject(cocoJson)
            val cocoArray = cocoData.getJSONArray("class_names")
            cocoLabels = (0 until cocoArray.length()).associate { it to cocoArray.getString(it) }

            val customJson = context.assets.open(CUSTOM_LABELS_FILE).bufferedReader().use { it.readText() }
            val customData = JSONObject(customJson)
            val customArray = customData.getJSONArray("class_names")
            customLabels = (0 until customArray.length()).associate { it to customArray.getString(it) }

            Log.i("DualModelDetector", "✓ Labels: COCO=${cocoLabels.size}, Custom=${customLabels.size}")
        } catch (e: Exception) {
            Log.e("DualModelDetector", "Label loading failed", e)
            throw e
        }
    }

    // ── Model Loading (CPU only for stability) ────────────────────────────────

    private fun loadModels() {
        try {
            val cocoBuffer = loadModelFile(COCO_MODEL_FILE)
            val cocoOptions = Interpreter.Options().apply { setNumThreads(4) }
            cocoInterpreter = Interpreter(cocoBuffer, cocoOptions)
            Log.i("DualModelDetector", "✓ COCO model loaded (CPU)")

            val customBuffer = loadModelFile(CUSTOM_MODEL_FILE)
            val customOptions = Interpreter.Options().apply { setNumThreads(4) }
            customInterpreter = Interpreter(customBuffer, customOptions)
            Log.i("DualModelDetector", "✓ Custom model loaded (CPU)")
        } catch (e: Exception) {
            Log.e("DualModelDetector", "Model loading failed", e)
            throw e
        }
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    data class Detection(
        val className: String,
        val confidence: Float,
        val bbox: RectF,
        val position: String,
        val distanceM: Float?,
        val source: String
    )

    fun detect(bitmap: Bitmap, targetClasses: List<String>): Pair<Bitmap, List<Detection>> {
        val startTime = System.currentTimeMillis()

        val allDetections = mutableListOf<Detection>()

        // COCO model
        val cocoDetections = runModel(cocoInterpreter!!, bitmap, cocoLabels, targetClasses, "COCO")
        allDetections.addAll(cocoDetections)

        // Custom model
        val customDetections = runModel(customInterpreter!!, bitmap, customLabels, targetClasses, "Custom")
        allDetections.addAll(customDetections)

        // NMS
        val finalDetections = applyNMS(allDetections, IOU_THRESHOLD)

        // Draw
        val annotatedBitmap = drawDetections(bitmap, finalDetections)

        val elapsed = System.currentTimeMillis() - startTime
        Log.d("DualModelDetector", "Detected ${finalDetections.size} objects in ${elapsed}ms")

        return Pair(annotatedBitmap, finalDetections)
    }

    // ── Single Model Run ──────────────────────────────────────────────────────

    private fun runModel(
        interpreter: Interpreter,
        bitmap: Bitmap,
        labels: Map<Int, String>,
        targetClasses: List<String>,
        source: String
    ): List<Detection> {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = preprocessImage(resized)

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        val numOutputs = outputShape[1]
        val numDetections = outputShape[2]

        val output = Array(1) { Array(numOutputs) { FloatArray(numDetections) } }
        interpreter.run(inputBuffer, output)

        return parseYOLOv8Output(
            output = output[0],
            labels = labels,
            targetClasses = targetClasses,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            source = source,
            numClasses = numOutputs - 4
        )
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }

    private fun parseYOLOv8Output(
        output: Array<FloatArray>,
        labels: Map<Int, String>,
        targetClasses: List<String>,
        originalWidth: Int,
        originalHeight: Int,
        source: String,
        numClasses: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numDetections = output[0].size

        for (i in 0 until numDetections) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            var maxConf = 0f
            var bestClass = -1
            for (c in 0 until numClasses) {
                val confIndex = 4 + c
                if (confIndex < output.size && output[confIndex][i] > maxConf) {
                    maxConf = output[confIndex][i]
                    bestClass = c
                }
            }

            if (maxConf < CONF_THRESHOLD) continue

            val className = labels[bestClass] ?: continue

            if (targetClasses.isNotEmpty() &&
                !targetClasses.any { it.equals(className, ignoreCase = true) }) {
                continue
            }

            val scaleX = originalWidth.toFloat() / INPUT_SIZE
            val scaleY = originalHeight.toFloat() / INPUT_SIZE

            val left = ((cx - w / 2) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val top = ((cy - h / 2) * scaleY).coerceIn(0f, originalHeight.toFloat())
            val right = ((cx + w / 2) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val bottom = ((cy + h / 2) * scaleY).coerceIn(0f, originalHeight.toFloat())

            val bbox = RectF(left, top, right, bottom)
            val position = getPosition(bbox, originalWidth)
            val distance = estimateDistance(className, bbox.width(), originalWidth)

            detections.add(Detection(className, maxConf, bbox, position, distance, source))
        }

        return detections
    }

    // ── NMS ───────────────────────────────────────────────────────────────────

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()

        for (det in sorted) {
            var shouldKeep = true
            for (kept in keep) {
                if (calculateIoU(det.bbox, kept.bbox) > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }
            if (shouldKeep) keep.add(det)
        }

        return keep
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)

        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) return 0f

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    // ── Drawing (FIXED - GREEN BOXES, BLACK TEXT) ────────────────────────────

    private fun drawDetections(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        // GREEN box paint
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }

        // BLACK text background
        val bgPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 200
        }

        // GREEN text
        val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 40f
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (det in detections) {
            // Draw box
            canvas.drawRect(det.bbox, boxPaint)

            // Label
            val label = "${det.className} ${(det.confidence * 100).toInt()}% [${det.source}]"

            // Measure text
            val bounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, bounds)

            // Background rect
            val bgRect = RectF(
                det.bbox.left - 4f,
                det.bbox.top - bounds.height() - 12f,
                det.bbox.left + bounds.width() + 8f,
                det.bbox.top
            )

            canvas.drawRect(bgRect, bgPaint)
            canvas.drawText(label, det.bbox.left, det.bbox.top - 6f, textPaint)
        }

        return mutable
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getPosition(bbox: RectF, imageWidth: Int): String {
        val centerX = (bbox.left + bbox.right) / 2
        return when {
            centerX < imageWidth / 3 -> "left"
            centerX < 2 * imageWidth / 3 -> "center"
            else -> "right"
        }
    }

    private fun estimateDistance(className: String, bboxWidth: Float, imageWidth: Int): Float? {
        val knownWidths = mapOf(
            "person" to 0.50f, "chair" to 0.50f, "door" to 0.90f,
            "car" to 1.8f, "laptop" to 0.40f
        )

        val realWidth = knownWidths[className.lowercase()] ?: return null
        val focalLength = imageWidth / (2 * tan(Math.toRadians(PHONE_FOV_DEG / 2.0))).toFloat()
        val distance = (realWidth * focalLength) / bboxWidth

        return distance.coerceIn(0.5f, 15f).let { (it * 10).roundToInt() / 10f }
    }

    fun close() {
        cocoInterpreter?.close()
        customInterpreter?.close()
        cocoInterpreter = null
        customInterpreter = null
    }
}