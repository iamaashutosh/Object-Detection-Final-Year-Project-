package com.example.detectionapplication

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Matrix
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp

// ─── HTTP Client ──────────────────────────────────────────────────────────────

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

const val API_BASE_URL = "http://192.168.1.10:8000"

// ─── Latest Frame ─────────────────────────────────────────────────────────────

val latestFrame = AtomicReference<Bitmap?>(null)

// ─── Frame Analyzer ───────────────────────────────────────────────────────────

class FrameAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap().rotate(image.imageInfo.rotationDegrees.toFloat())
            val scaled = bitmap.scaleTo(maxWidth = 640, maxHeight = 480)
            latestFrame.set(scaled)
        } catch (e: Exception) {
            Log.e("FrameAnalyzer", "Frame error", e)
        } finally {
            image.close()
        }
    }
}

// ─── Detection Stream (Optimized) ─────────────────────────────────────────────

@Composable
fun DetectionStream(
    tts:      TtsManager,
    detector: DualModelDetector?,
    modifier: Modifier = Modifier
) {
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMsg       by remember { mutableStateOf("Starting…") }
    var detectionCount  by remember { mutableStateOf(0) }

    LaunchedEffect(detector) {
        withContext(Dispatchers.Default) {

            if (detector == null) {
                withContext(Dispatchers.Main) {
                    statusMsg = "Detector not loaded"
                }
                return@withContext
            }

            var waited = 0
            while (latestFrame.get() == null && isActive) {
                delay(100)
                if (++waited > 50) {
                    withContext(Dispatchers.Main) { statusMsg = "Camera not ready" }
                    return@withContext
                }
            }

            withContext(Dispatchers.Main) { statusMsg = "" }

            var lastAnnouncedClasses = setOf<String>()
            var lastAnnounceTime = 0L

            while (isActive) {
                val bitmap = latestFrame.get()
                if (bitmap == null) {
                    delay(100)
                    continue
                }

                try {
                    // Run detection (empty list = detect all classes)
                    val (annotated, detections) = detector.detect(
                        bitmap = bitmap,
                        targetClasses = emptyList()  // DETECT ALL
                    )

                    // Update UI
                    withContext(Dispatchers.Main) {
                        annotatedBitmap = annotated
                        detectionCount = detections.size
                        statusMsg = ""
                    }

                    // TTS announcements (with cooldown)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastAnnounceTime > 3000) {  // 3 second cooldown
                        val currentClasses = detections.map { it.className }.toSet()
                        val newClasses = currentClasses - lastAnnouncedClasses

                        if (newClasses.isNotEmpty()) {
                            // Announce up to 3 new objects
                            val toAnnounce = detections
                                .filter { it.className in newClasses }
                                .take(3)

                            for (detection in toAnnounce) {
                                val phrase = buildTtsPhrase(detection)
                                withContext(Dispatchers.Main) { tts.speak(phrase) }
                            }

                            lastAnnouncedClasses = currentClasses
                            lastAnnounceTime = currentTime
                        }
                    }

                    // Slower frame rate to reduce lag
                    delay(100)  // ~10 FPS (was 50ms = 20 FPS)

                } catch (e: Exception) {
                    Log.e("DetectionStream", "Error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        statusMsg = "⚠ ${e.message}"
                    }
                    delay(500)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (annotatedBitmap != null) {
            Image(
                bitmap             = annotatedBitmap!!.asImageBitmap(),
                contentDescription = "Detection result",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
        }

        // Detection count badge
        if (detectionCount > 0 && statusMsg.isEmpty()) {
            Text(
                text = "$detectionCount object${if (detectionCount == 1) "" else "s"}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (statusMsg.isNotEmpty()) {
            Text(
                text     = statusMsg,
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ─── TTS Phrase Builder ───────────────────────────────────────────────────────

private fun buildTtsPhrase(detection: DualModelDetector.Detection): String {
    val name = detection.className.replaceFirstChar { it.uppercase() }

    val positionPhrase = when (detection.position) {
        "left"   -> "on the left"
        "right"  -> "on the right"
        "center" -> "in the center"
        else     -> detection.position
    }

    val distancePhrase = detection.distanceM?.let { d ->
        if (d > 0.5f && d < 20f) {  // Only announce reasonable distances
            val rounded = Math.round(d)
            ", $rounded ${if (rounded == 1) "metre" else "metres"} away"
        } else {
            ""
        }
    } ?: ""

    return "$name $positionPhrase$distancePhrase"
}

// ─── Server BLIP ──────────────────────────────────────────────────────────────

suspend fun describeSceneWithBLIP(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
    Log.i("describeScene", "Calling server BLIP...")

    val jpegBytes = bitmap.toJpegBytes(quality = 85)
    val body      = jpegBytes.toRequestBody("image/jpeg".toMediaType())
    val request   = Request.Builder()
        .url("$API_BASE_URL/api/describe/")
        .post(body)
        .build()

    return@withContext try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string() ?: "Unknown error"
                Log.e("describeScene", "HTTP ${response.code}: $error")
                "Description failed"
            } else {
                val responseBytes = response.body?.bytes()
                if (responseBytes == null) {
                    "No description received"
                } else {
                    val json = org.json.JSONObject(String(responseBytes))
                    val desc = json.getString("description")
                    Log.i("describeScene", "BLIP result: $desc")
                    desc
                }
            }
        }
    } catch (e: Exception) {
        Log.e("describeScene", "Error: ${e.message}", e)
        "Description error: ${e.message}"
    }
}

// ─── Bitmap Helpers ───────────────────────────────────────────────────────────

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes  = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.scaleTo(maxWidth: Int, maxHeight: Int): Bitmap {
    if (width <= maxWidth && height <= maxHeight) return this
    val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    return Bitmap.createScaledBitmap(
        this, (width * scale).toInt(), (height * scale).toInt(), true
    )
}

private fun Bitmap.toJpegBytes(quality: Int = 85): ByteArray =
    ByteArrayOutputStream().also {
        compress(Bitmap.CompressFormat.JPEG, quality, it)
    }.toByteArray()