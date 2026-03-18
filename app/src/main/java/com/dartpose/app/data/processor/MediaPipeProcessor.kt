package com.dartpose.app.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.dartpose.app.domain.model.HandData
import com.dartpose.app.domain.model.HandLandmark
import com.dartpose.app.domain.model.PoseData
import com.dartpose.app.domain.model.PoseLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MediaPipeProcessor(private val context: Context) {
    
    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    
    private val _poseData = MutableStateFlow<PoseData?>(null)
    val poseData: StateFlow<PoseData?> = _poseData.asStateFlow()
    
    private val _handData = MutableStateFlow<List<HandData>>(emptyList())
    val handData: StateFlow<List<HandData>> = _handData.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isInitializing = true
    
    init {
        processorScope.launch {
            try {
                initializePoseLandmarker(context)
                initializeHandLandmarker(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        processorScope.launch {
            while (isInitializing) {
                kotlinx.coroutines.delay(100)
            }
            _isReady.value = true
        }
    }
    
    private fun initializePoseLandmarker(context: Context) {
        try {
            val modelName = "pose_landmarker_lite.task"
            val file = java.io.File(context.cacheDir, modelName)
            if (!file.exists()) {
                copyAssetToCache(context, modelName)
            }
            val modelPath = file.absolutePath
            
            val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun initializeHandLandmarker(context: Context) {
        try {
            val modelName = "hand_landmarker.task"
            val file = java.io.File(context.cacheDir, modelName)
            if (!file.exists()) {
                copyAssetToCache(context, modelName)
            }
            val modelPath = file.absolutePath
            
            val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()
            
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2)
                .build()
            
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isInitializing = false
        } catch (e: Exception) {
            e.printStackTrace()
            isInitializing = false
        }
    }
    
    private fun copyAssetToCache(context: Context, fileName: String) {
        val file = java.io.File(context.cacheDir, fileName)
        if (!file.exists()) {
            try {
                context.assets.open(fileName).use { inputStream ->
                    java.io.FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    @OptIn(ExperimentalGetImage::class)
    fun processFrame(imageProxy: ImageProxy) {
        if (poseLandmarker == null || handLandmarker == null) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        
        try {
            val bitmap = imageProxyToBitmap(mediaImage, imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                return
            }
            
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestamp = imageProxy.imageInfo.timestamp / 1_000_000 // Convert nanoseconds to milliseconds
            
            try {
                val poseResult = poseLandmarker?.detectForVideo(mpImage, timestamp)
                if (poseResult != null && poseResult.landmarks().isNotEmpty() && poseResult.landmarks()[0].isNotEmpty()) {
                    val landmarks = poseResult.landmarks()[0].map { 
                        PoseLandmark(it.x(), it.y(), it.z(), 1.0f)
                    }
                    val worldLandmarks = if (poseResult.worldLandmarks().isNotEmpty()) {
                        poseResult.worldLandmarks()[0].map {
                            PoseLandmark(it.x(), it.y(), it.z(), 1.0f)
                        }
                    } else emptyList()
                    _poseData.value = PoseData(landmarks, worldLandmarks)
                } else {
                    _poseData.value = null
                }
            } catch (e: Exception) {
                _poseData.value = null
            }
            
            try {
                val handResult = handLandmarker?.detectForVideo(mpImage, timestamp)
                if (handResult != null && handResult.landmarks().isNotEmpty()) {
                    val hands = mutableListOf<HandData>()
                    
                    for (i in handResult.landmarks().indices) {
                        val landmarks = handResult.landmarks().getOrNull(i)?.map {
                            HandLandmark(it.x(), it.y(), it.z())
                        } ?: continue
                        
                        val handedness = try {
                            handResult.handednesses().getOrNull(i)?.getOrNull(0)?.categoryName() ?: "Unknown"
                        } catch (e: Exception) {
                            "Unknown"
                        }
                        
                        hands.add(HandData(landmarks, handedness))
                    }
                    _handData.value = hands
                } else {
                    _handData.value = emptyList()
                }
            } catch (e: Exception) {
                _handData.value = emptyList()
            }
            
            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBitmap(image: Image, imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            
            val imageBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun close() {
        try {
            poseLandmarker?.close()
            handLandmarker?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
