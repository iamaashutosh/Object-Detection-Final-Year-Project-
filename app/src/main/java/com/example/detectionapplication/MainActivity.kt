//package com.example.detectionapplication
//
//import android.Manifest
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.animation.*
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Menu
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import com.example.detectionapplication.ui.theme.DetectionApplicationTheme
//import com.google.accompanist.permissions.ExperimentalPermissionsApi
//import com.google.accompanist.permissions.isGranted
//import com.google.accompanist.permissions.rememberPermissionState
//import java.util.concurrent.Executors
//import kotlinx.coroutines.launch
//
//const val API_BASE_URL = "http://192.168.1.10:8000"
//
//// ─── Detection Categories ─────────────────────────────────────────────────────
//// Each category has a display name, an emoji icon, and the YOLO class names
//// that Django will filter to. These must match YOLO's class name strings exactly.
//
//data class DetectionCategory(
//    val label:      String,
//    val icon:       String,
//    val yoloClasses: List<String>
//)
//
//val DETECTION_CATEGORIES = listOf(
//    DetectionCategory(
//        label       = "Person",
//        icon        = "🧑",
//        yoloClasses = listOf("person")
//    ),
//    DetectionCategory(
//        label       = "Chairs & Tables",
//        icon        = "🪑",
//        yoloClasses = listOf("chair", "dining table")
//    ),
//    DetectionCategory(
//        label       = "Doors",
//        icon        = "🚪",
//        // YOLOv8n (COCO) doesn't have a "door" class — closest is these.
//        // Replace with your custom class name if you trained your own model.
//        yoloClasses = listOf("door")
//    ),
//    DetectionCategory(
//        label       = "Stairs",
//        icon        = "🪜",
//        yoloClasses = listOf("stairs")
//    ),
//    DetectionCategory(
//        label       = "Whiteboard",
//        icon        = "📋",
//        yoloClasses = listOf("whiteboard", "tv")  // tv is closest in COCO
//    ),
//)
//
//// ─── Activity ─────────────────────────────────────────────────────────────────
//
//class MainActivity : ComponentActivity() {
//
//    private lateinit var ttsManager: TtsManager
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        ttsManager = TtsManager(this)
//
//        setContent {
//            DetectionApplicationTheme {
//                Box(modifier = Modifier.fillMaxSize()) {
//                    CameraScreen()
//                    Scaffold(
//                        modifier       = Modifier.fillMaxSize(),
//                        containerColor = Color.Transparent
//                    ) { innerPadding ->
//                        ControlScreen(
//                            tts      = ttsManager,
//                            modifier = Modifier.padding(innerPadding)
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        ttsManager.shutdown()
//    }
//}
//
//// ─── Control Screen with Drawer ───────────────────────────────────────────────
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ControlScreen(
//    tts:      TtsManager,
//    modifier: Modifier = Modifier
//) {
//    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
//    val scope       = rememberCoroutineScope()
//
//    var isDetecting      by remember { mutableStateOf(false) }
//    // Start with the first category (Person) selected by default
//    var activeCategory   by remember { mutableStateOf(DETECTION_CATEGORIES[0]) }
//
//    ModalNavigationDrawer(
//        drawerState   = drawerState,
//        // Scrim (dim overlay) color when drawer is open
//        scrimColor    = Color.Black.copy(alpha = 0.5f),
//        drawerContent = {
//            CategoryDrawer(
//                categories     = DETECTION_CATEGORIES,
//                activeCategory = activeCategory,
//                onCategorySelected = { category ->
//                    activeCategory = category
//                    // Close drawer after selection
//                    scope.launch { drawerState.close() }
//                    // If already detecting, the new category takes effect
//                    // on the very next frame — no restart needed
//                }
//            )
//        },
//        modifier = modifier
//    ) {
//        // ── Main content ──────────────────────────────────────────────────────
//        Box(modifier = Modifier.fillMaxSize()) {
//
//            // Detection overlay
//            AnimatedVisibility(
//                visible  = isDetecting,
//                enter    = fadeIn(),
//                exit     = fadeOut(),
//                modifier = Modifier.fillMaxSize()
//            ) {
//                DetectionStream(
//                    tts            = tts,
//                    activeCategory = activeCategory,
//                    modifier       = Modifier.fillMaxSize()
//                )
//            }
//
//            // ── Top bar ───────────────────────────────────────────────────────
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .align(Alignment.TopCenter)
//                    .padding(horizontal = 12.dp, vertical = 10.dp),
//                verticalAlignment     = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                // Hamburger menu button
//                IconButton(
//                    onClick = { scope.launch { drawerState.open() } },
//                    modifier = Modifier
//                        .background(Color.Black.copy(0.45f), RoundedCornerShape(8.dp))
//                        .size(44.dp)
//                ) {
//                    Icon(
//                        imageVector        = Icons.Default.Menu,
//                        contentDescription = "Open category menu",
//                        tint               = Color.White
//                    )
//                }
//
//                // Active category badge
//                Row(
//                    modifier = Modifier
//                        .background(Color.Black.copy(0.45f), RoundedCornerShape(20.dp))
//                        .padding(horizontal = 14.dp, vertical = 6.dp),
//                    verticalAlignment     = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(6.dp)
//                ) {
//                    Text(activeCategory.icon, fontSize = 16.sp)
//                    Text(
//                        text  = activeCategory.label,
//                        color = Color.White,
//                        style = MaterialTheme.typography.labelLarge
//                    )
//                }
//
//                // Status dot
//                Text(
//                    text  = if (isDetecting) "● LIVE" else "●",
//                    color = if (isDetecting) Color.Green else Color.Gray,
//                    style = MaterialTheme.typography.labelLarge,
//                    modifier = Modifier
//                        .background(Color.Black.copy(0.45f), RoundedCornerShape(20.dp))
//                        .padding(horizontal = 12.dp, vertical = 6.dp)
//                )
//            }
//
//            Column(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(bottom = 50.dp)
//                    .fillMaxWidth()
//                    .padding(horizontal = 20.dp),
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//
//                // Start / Stop row
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    Button(
//                        onClick  = { isDetecting = true },
//                        enabled  = !isDetecting,
//                        modifier = Modifier.weight(1f)
//                    ) {
//                        Text("Start")
//                    }
//
//                    Button(
//                        onClick  = { isDetecting = false },
//                        enabled  = isDetecting,
//                        modifier = Modifier.weight(1f),
//                        colors   = ButtonDefaults.buttonColors(containerColor = Color.Red)
//                    ) {
//                        Text("Stop")
//                    }
//                }
//
//                // Third button (Describe Scene)
//                DescribeButton(
//                    tts     = tts,
//                    enabled = !isDetecting,  // only works when not streaming
//                    modifier = Modifier.fillMaxWidth()
//                )
//            }
//        }
//    }
//}
////------------Describebutton-------------------------------
//@Composable
//fun DescribeButton(
//    tts:      TtsManager,
//    enabled:  Boolean,
//    modifier: Modifier = Modifier
//) {
//    val context = LocalContext.current
//    val scope   = rememberCoroutineScope()
//
//    var isDescribing by remember { mutableStateOf(false) }
//
//    Button(
//        onClick = {
//            isDescribing = true
//            scope.launch {
//                val bitmap = latestFrame.get()
//                if (bitmap == null) {
//                    tts.speak("Camera not ready")
//                    isDescribing = false
//                    return@launch
//                }
//
//                try {
//                    val description = describeScene(bitmap)
//                    tts.speak(description)
//                } catch (e: Exception) {
//                    Log.e("DescribeButton", "Error: ${e.message}")
//                    tts.speak("Description failed")
//                } finally {
//                    isDescribing = false
//                }
//            }
//        },
//        enabled  = enabled && !isDescribing,
//        modifier = modifier,
//        colors   = ButtonDefaults.buttonColors(
//            containerColor = Color(0xFF00D4FF),
//            contentColor   = Color.Black
//        )
//    ) {
//        if (isDescribing) {
//            CircularProgressIndicator(
//                color     = Color.Black,
//                modifier  = Modifier.size(20.dp),
//                strokeWidth = 2.dp
//            )
//            Spacer(Modifier.width(8.dp))
//        }
//        Text(
//            text       = if (isDescribing) "Describing..." else "🎙  Describe Scene",
//            fontWeight = FontWeight.SemiBold
//        )
//    }
//}
//
//// ─── Drawer Content ───────────────────────────────────────────────────────────
//
//@Composable
//fun CategoryDrawer(
//    categories:         List<DetectionCategory>,
//    activeCategory:     DetectionCategory,
//    onCategorySelected: (DetectionCategory) -> Unit
//) {
//    ModalDrawerSheet(
//        modifier      = Modifier.width(280.dp),
//        drawerShape   = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
//        drawerContainerColor = Color(0xFF1A1A2E)
//    ) {
//        Spacer(Modifier.height(48.dp))
//
//        // Header
//        Text(
//            text     = "Detection Mode",
//            color    = Color.White,
//            fontSize = 20.sp,
//            fontWeight = FontWeight.Bold,
//            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
//        )
//        Text(
//            text     = "Select what to detect",
//            color    = Color.White.copy(0.5f),
//            fontSize = 13.sp,
//            modifier = Modifier.padding(horizontal = 20.dp)
//        )
//
//        Spacer(Modifier.height(24.dp))
//        HorizontalDivider(color = Color.White.copy(0.1f))
//        Spacer(Modifier.height(12.dp))
//
//        // Category items
//        categories.forEach { category ->
//            val isActive = category.label == activeCategory.label
//            CategoryDrawerItem(
//                category  = category,
//                isActive  = isActive,
//                onClick   = { onCategorySelected(category) }
//            )
//        }
//
//        Spacer(Modifier.weight(1f))
//        HorizontalDivider(color = Color.White.copy(0.1f))
//
//        // Footer note
//        Text(
//            text     = "Tap a mode to switch detection.\nChanges apply instantly.",
//            color    = Color.White.copy(0.35f),
//            fontSize = 11.sp,
//            lineHeight = 16.sp,
//            modifier = Modifier.padding(20.dp)
//        )
//    }
//}
//
//// ─── Single Drawer Item ───────────────────────────────────────────────────────
//
//@Composable
//fun CategoryDrawerItem(
//    category: DetectionCategory,
//    isActive: Boolean,
//    onClick:  () -> Unit
//) {
//    val bgColor   = if (isActive) Color(0xFF00D4FF).copy(0.15f) else Color.Transparent
//    val textColor = if (isActive) Color(0xFF00D4FF) else Color.White.copy(0.8f)
//    val barColor  = if (isActive) Color(0xFF00D4FF) else Color.Transparent
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 12.dp, vertical = 4.dp)
//            .background(bgColor, RoundedCornerShape(10.dp))
//            .clickable(onClick = onClick)
//            .padding(horizontal = 12.dp, vertical = 14.dp),
//        verticalAlignment     = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.spacedBy(14.dp)
//    ) {
//        // Active indicator bar
//        Box(
//            modifier = Modifier
//                .width(3.dp)
//                .height(28.dp)
//                .background(barColor, RoundedCornerShape(2.dp))
//        )
//
//        // Icon
//        Text(category.icon, fontSize = 22.sp)
//
//        // Label + class hint
//        Column {
//            Text(
//                text       = category.label,
//                color      = textColor,
//                fontSize   = 15.sp,
//                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
//            )
//            Text(
//                text     = category.yoloClasses.joinToString(", "),
//                color    = Color.White.copy(0.3f),
//                fontSize = 10.sp
//            )
//        }
//
//        if (isActive) {
//            Spacer(Modifier.weight(1f))
//            Text("✓", color = Color(0xFF00D4FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
//        }
//    }
//}
//
//// ─── Camera Permission ────────────────────────────────────────────────────────
//
//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun CameraScreen() {
//    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
//    if (cameraPermissionState.status.isGranted) {
//        CameraPreview()
//    } else {
//        SideEffect { cameraPermissionState.launchPermissionRequest() }
//        Text("Camera permission is required to use this app.")
//    }
//}
//
//// ─── Camera Preview ───────────────────────────────────────────────────────────
//
//@Composable
//fun CameraPreview() {
//    val context        = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
//
//    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
//    DisposableEffect(Unit) {
//        onDispose { analysisExecutor.shutdown() }
//    }
//
//    AndroidView(
//        factory = { ctx ->
//            val previewView = PreviewView(ctx)
//            cameraProviderFuture.addListener({
//                val cameraProvider = cameraProviderFuture.get()
//                val preview = androidx.camera.core.Preview.Builder().build().also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//                val imageAnalysis = ImageAnalysis.Builder()
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                    .build()
//                    .also { it.setAnalyzer(analysisExecutor, FrameAnalyzer()) }
//
//                try {
//                    cameraProvider.unbindAll()
//                    cameraProvider.bindToLifecycle(
//                        lifecycleOwner,
//                        CameraSelector.DEFAULT_BACK_CAMERA,
//                        preview,
//                        imageAnalysis
//                    )
//                } catch (e: Exception) {
//                    Log.e("CameraPreview", "Binding failed", e)
//                }
//            }, ContextCompat.getMainExecutor(ctx))
//            previewView
//        },
//        modifier = Modifier.fillMaxSize()
//    )
//}
//
package com.example.detectionapplication

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.detectionapplication.ui.theme.DetectionApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = TtsManager(this)

        setContent {
            DetectionApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen()
                    Scaffold(
                        modifier       = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        ControlScreen(
                            tts      = ttsManager,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

// ─── Control Screen (Simplified) ──────────────────────────────────────────────

@Composable
fun ControlScreen(
    tts:      TtsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isDetecting       by remember { mutableStateOf(false) }
    var showDescribeMode  by remember { mutableStateOf(false) }

    // Load dual model detector (on-device)
    val detector = remember {
        try {
            DualModelDetector(context).also {
                Log.i("MainActivity", "✓ Dual model detector loaded")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Detector init failed: ${e.message}", e)
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { detector?.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Detection overlay ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isDetecting,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DetectionStream(
                tts      = tts,
                detector = detector,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Describe Mode Overlay ─────────────────────────────────────────
        if (showDescribeMode) {
            ServerDescribeScreen(
                tts       = tts,
                onDismiss = { showDescribeMode = false }
            )
        }

        // ── Top status bar ────────────────────────────────────────────────
        if (!showDescribeMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Text(
                    text  = if (isDetecting) "● LIVE DETECTION" else "● READY",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            if (isDetecting) Color.Green.copy(0.3f) else Color.Gray.copy(0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ── Bottom buttons ────────────────────────────────────────────────
        if (!showDescribeMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick  = { isDetecting = true },
                        enabled  = !isDetecting && detector != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D4FF)
                        )
                    ) {
                        Text("Start Detection", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick  = { isDetecting = false },
                        enabled  = isDetecting,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (isDetecting) isDetecting = false
                        showDescribeMode = true
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor   = Color.White
                    )
                ) {
                    Text(
                        text       = "🎙  Describe Scene",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─── Server Describe Screen ───────────────────────────────────────────────────

@Composable
fun ServerDescribeScreen(
    tts:       TtsManager,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf("capturing") }
    var description by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)

        val bitmap = latestFrame.get()
        if (bitmap == null) {
            description = "Camera not ready"
            state = "error"
            tts.speak("Camera not ready")
            return@LaunchedEffect
        }

        state = "processing"
        launch {
            for (i in 1..100) {
                kotlinx.coroutines.delay(50)
                progress = i / 100f
                if (state != "processing") break
            }
        }

        try {
            withContext(Dispatchers.IO) {
                description = describeSceneWithBLIP(bitmap)
            }

            state = "complete"
            tts.speak(description)

        } catch (e: Exception) {
            Log.e("ServerDescribe", "Error: ${e.message}", e)
            description = "Description failed: ${e.message}"
            state = "error"
            tts.speak("Description failed")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.95f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            when (state) {
                "capturing" -> {
                    Text("📸", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Capturing frame...", color = Color.White, fontSize = 18.sp)
                }

                "processing" -> {
                    CircularProgressIndicator(
                        progress = progress,
                        color = Color(0xFF4CAF50),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Analyzing scene...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Server processing (${(progress * 100).toInt()}%)",
                        color = Color.White.copy(0.6f),
                        fontSize = 14.sp
                    )
                }

                "complete", "error" -> {
                    if (state == "complete") {
                        Text("✓", color = Color(0xFF4CAF50), fontSize = 64.sp)
                    } else {
                        Text("⚠", color = Color(0xFFFF4444), fontSize = 64.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A2E)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = description,
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Center
                            )

                            if (state == "complete") {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "✓ Generated by BLIP",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (state == "complete" || state == "error") {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.White.copy(0.1f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

// ─── Camera Permission ────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        CameraPreview()
    } else {
        SideEffect { cameraPermissionState.launchPermissionRequest() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Camera permission is required",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Camera Preview ───────────────────────────────────────────────────────────

@Composable
fun CameraPreview() {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, FrameAnalyzer()) }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}