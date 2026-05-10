package com.dartpose.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dartpose.app.domain.model.HandLandmark
import com.dartpose.app.domain.model.PoseLandmark
import com.dartpose.app.domain.model.TrajectoryPoint
import java.util.concurrent.Executors

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    val poseLandmarks by viewModel.poseLandmarks.collectAsState()
    val handLandmarks by viewModel.handLandmarks.collectAsState()
    val trajectory by viewModel.trajectory.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == 
            PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewView(
                onFrameAnalyzed = { imageProxy ->
                    viewModel.processFrame(imageProxy)
                }
            )
            
            DetectionOverlay(
                poseLandmarks = poseLandmarks,
                handLandmarks = handLandmarks,
                trajectory = trajectory
            )
            
            ControlPanel(
                onClearTrajectory = { viewModel.clearTrajectory() },
                onToggleTracking = { viewModel.toggleTracking() },
                isTracking = isTracking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera permission required",
                    color = Color.White
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun CameraPreviewView(
    onFrameAnalyzed: (ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    
    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        onFrameAnalyzed(imageProxy)
                    }
                }
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
        
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun DetectionOverlay(
    poseLandmarks: List<PoseLandmark>,
    handLandmarks: List<HandLandmark>,
    trajectory: List<TrajectoryPoint>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        if (poseLandmarks.isNotEmpty()) {
            poseLandmarks.forEach { landmark ->
                drawCircle(
                    color = Color.Cyan,
                    radius = 8f,
                    center = Offset(landmark.x * canvasWidth, landmark.y * canvasHeight)
                )
            }
            
            val connections = listOf(
                listOf(11, 12), listOf(11, 13), listOf(13, 15), listOf(15, 17),
                listOf(12, 14), listOf(14, 16), listOf(16, 18),
                listOf(11, 23), listOf(12, 24), listOf(23, 24),
                listOf(23, 25), listOf(25, 27), listOf(27, 29), listOf(29, 31),
                listOf(24, 26), listOf(26, 28), listOf(28, 30), listOf(30, 32)
            )
            
            connections.forEach { connection ->
                if (connection[0] < poseLandmarks.size && connection[1] < poseLandmarks.size) {
                    val start = poseLandmarks[connection[0]]
                    val end = poseLandmarks[connection[1]]
                    
                    if (start.visibility > 0.5f && end.visibility > 0.5f) {
                        drawLine(
                            color = Color.Cyan,
                            start = Offset(start.x * canvasWidth, start.y * canvasHeight),
                            end = Offset(end.x * canvasWidth, end.y * canvasHeight),
                            strokeWidth = 4f
                        )
                    }
                }
            }
        }
        
        if (handLandmarks.isNotEmpty()) {
            handLandmarks.forEach { landmark ->
                drawCircle(
                    color = Color.Yellow,
                    radius = 6f,
                    center = Offset(landmark.x * canvasWidth, landmark.y * canvasHeight)
                )
            }
        }
        
        if (trajectory.size >= 2) {
            val colors = listOf(Color.Green, Color(0xFFFF9800), Color(0xFFF44336))
            trajectory.groupBy { it.colorIndex }.forEach { (colorIdx, pts) ->
                if (pts.size >= 2) {
                    val path = Path()
                    path.moveTo(pts[0].x * canvasWidth, pts[0].y * canvasHeight)
                    for (i in 1 until pts.size) {
                        path.lineTo(pts[i].x * canvasWidth, pts[i].y * canvasHeight)
                    }
                    drawPath(
                        path = path,
                        color = colors.getOrElse(colorIdx) { Color.Green },
                        style = Stroke(width = 6f)
                    )
                }
                pts.forEach { point ->
                    drawCircle(
                        color = colors.getOrElse(colorIdx) { Color.Green },
                        radius = 5f,
                        center = Offset(point.x * canvasWidth, point.y * canvasHeight)
                    )
                }
            }
        }
    }
}

@Composable
fun ControlPanel(
    onClearTrajectory: () -> Unit,
    onToggleTracking: () -> Unit,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onClearTrajectory,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("Clear Trajectory")
        }
        
        Button(
            onClick = onToggleTracking,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color.Green else Color.Gray
            )
        ) {
            Text(if (isTracking) "Tracking On" else "Tracking Off")
        }
    }
}
