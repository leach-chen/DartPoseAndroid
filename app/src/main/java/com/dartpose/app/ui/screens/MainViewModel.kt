package com.dartpose.app.ui.screens

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dartpose.app.data.processor.MediaPipeProcessor
import com.dartpose.app.domain.model.HandLandmark
import com.dartpose.app.domain.model.PoseLandmark
import com.dartpose.app.domain.model.TrajectoryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val mediaPipeProcessor = MediaPipeProcessor(context)
    
    private val _poseLandmarks = MutableStateFlow<List<PoseLandmark>>(emptyList())
    val poseLandmarks: StateFlow<List<PoseLandmark>> = _poseLandmarks.asStateFlow()
    
    private val _handLandmarks = MutableStateFlow<List<HandLandmark>>(emptyList())
    val handLandmarks: StateFlow<List<HandLandmark>> = _handLandmarks.asStateFlow()
    
    private val _trajectory = MutableStateFlow<List<TrajectoryPoint>>(emptyList())
    val trajectory: StateFlow<List<TrajectoryPoint>> = _trajectory.asStateFlow()
    
    private val _isTracking = MutableStateFlow(true)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private var lastHandPosition: TrajectoryPoint? = null
    private var lastTimestamp: Long = 0
    private var lastValidTrackTime: Long = 0
    private var lastPoseUpdateTime: Long = 0
    private var currentColorIndex = 0
    private var restCenterX = -1f
    private var restCenterY = -1f
    private var isThrowing = false
    
    init {
        viewModelScope.launch {
            mediaPipeProcessor.poseData.collect { poseData ->
                val landmarks = poseData?.landmarks
                if (!landmarks.isNullOrEmpty()) {
                    val prev = _poseLandmarks.value
                    if (prev.isEmpty()) {
                        _poseLandmarks.value = landmarks
                    } else {
                        _poseLandmarks.value = landmarks.mapIndexed { i, curr ->
                            val p = prev.getOrNull(i) ?: return@mapIndexed curr
                            PoseLandmark(
                                x = p.x * 0.6f + curr.x * 0.4f,
                                y = p.y * 0.6f + curr.y * 0.4f,
                                z = p.z * 0.6f + curr.z * 0.4f,
                                visibility = curr.visibility
                            )
                        }
                    }
                    lastPoseUpdateTime = System.currentTimeMillis()
                }
            }
        }
        
        viewModelScope.launch {
            mediaPipeProcessor.handData.collect { hands ->
                val allLandmarks = mutableListOf<HandLandmark>()
                hands.forEach { hand ->
                    allLandmarks.addAll(hand.landmarks)
                }
                _handLandmarks.value = allLandmarks
                
                if (_isTracking.value) {
                    val currentTime = System.currentTimeMillis()
                    val handLandmarks = hands.firstOrNull()?.landmarks ?: emptyList()
                    val trackingPoint = findTrackingPoint(_poseLandmarks.value, handLandmarks)
                    
                    if (trackingPoint != null) {
                        lastValidTrackTime = currentTime
                        val newPoint = TrajectoryPoint(trackingPoint.x, trackingPoint.y, currentTime)
                        val currentTrajectory = _trajectory.value.toMutableList()
                        
                        if (lastHandPosition != null && lastTimestamp > 0) {
                            val timeDiff = currentTime - lastTimestamp
                            val dx = newPoint.x - lastHandPosition!!.x
                            val dy = newPoint.y - lastHandPosition!!.y
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            
                            if (distance < 0.003f) {
                                lastHandPosition = newPoint
                                lastTimestamp = currentTime
                                return@collect
                            }
                            
                            if (distance > 0.015f && timeDiff > 0 && timeDiff <= 200) {
                                val steps = kotlin.math.min((distance / 0.005f).toInt(), 30)
                                for (i in 1 until steps) {
                                    val fraction = i.toFloat() / steps
                                    currentTrajectory.add(
                                        TrajectoryPoint(
                                            lastHandPosition!!.x + dx * fraction,
                                            lastHandPosition!!.y + dy * fraction,
                                            lastTimestamp + (timeDiff * fraction).toLong(),
                                            currentColorIndex
                                        )
                                    )
                                }
                            }
                        }
                        
                        if (restCenterX < 0 && currentTrajectory.size >= 10) {
                            val recent = currentTrajectory.takeLast(10)
                            restCenterX = recent.map { it.x }.average().toFloat()
                            restCenterY = recent.map { it.y }.average().toFloat()
                        }
                        
                        if (restCenterX >= 0) {
                            val dx = trackingPoint.x - restCenterX
                            val dy = trackingPoint.y - restCenterY
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (dist > 0.06f) isThrowing = true
                            if (isThrowing && dist < 0.05f) {
                                currentColorIndex = (currentColorIndex + 1) % 3
                                isThrowing = false
                                restCenterX = trackingPoint.x
                                restCenterY = trackingPoint.y
                            }
                        }
                        
                        currentTrajectory.add(TrajectoryPoint(
                            trackingPoint.x, trackingPoint.y, currentTime, currentColorIndex
                        ))
                        
                        if (currentTrajectory.size > 800) {
                            currentTrajectory.removeAt(0)
                        }
                        
                        _trajectory.value = currentTrajectory
                        lastHandPosition = newPoint
                        lastTimestamp = currentTime
                    }
                }
            }
        }
        
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                val now = System.currentTimeMillis()
                if (_poseLandmarks.value.isNotEmpty() && now - lastPoseUpdateTime > 500) {
                    _poseLandmarks.value = emptyList()
                }
                if (_trajectory.value.isNotEmpty() && lastValidTrackTime > 0 && now - lastValidTrackTime > 2000) {
                    _trajectory.value = emptyList()
                    lastHandPosition = null
                    lastTimestamp = 0
                    lastValidTrackTime = 0
                    currentColorIndex = 0
                    restCenterX = -1f
                    restCenterY = -1f
                    isThrowing = false
                }
            }
        }
    }
    
    @OptIn(ExperimentalGetImage::class)
    fun processFrame(imageProxy: ImageProxy) {
        mediaPipeProcessor.processFrame(imageProxy)
    }
    
    fun clearTrajectory() {
        _trajectory.value = emptyList()
        lastHandPosition = null
        lastTimestamp = 0
        currentColorIndex = 0
        restCenterX = -1f
        restCenterY = -1f
        isThrowing = false
    }
    
    fun toggleTracking() {
        _isTracking.value = !_isTracking.value
        if (!_isTracking.value) {
            lastHandPosition = null
            lastTimestamp = 0
        }
    }
    
    private fun findTrackingPoint(
        poseLandmarks: List<PoseLandmark>,
        handLandmarks: List<HandLandmark>
    ): HandLandmark? {
        if (handLandmarks.isEmpty()) return null

        val palmCenter = calculatePalmCenter(handLandmarks)
        if (palmCenter != null) return palmCenter

        handLandmarks.getOrNull(0)?.let { return it }

        val bestPoseWrist = listOfNotNull(
            poseLandmarks.getOrNull(15)?.takeIf { it.visibility > 0.5f },
            poseLandmarks.getOrNull(16)?.takeIf { it.visibility > 0.5f }
        ).maxByOrNull { it.visibility }

        if (bestPoseWrist != null) {
            return HandLandmark(bestPoseWrist.x, bestPoseWrist.y, bestPoseWrist.z)
        }

        return handLandmarks.getOrNull(8)
    }

    private fun calculatePalmCenter(landmarks: List<HandLandmark>): HandLandmark? {
        val palmIndices = listOf(0, 5, 9, 13, 17)
        val valid = palmIndices.mapNotNull { landmarks.getOrNull(it) }
        if (valid.size < 3) return null
        return HandLandmark(
            x = valid.sumOf { it.x.toDouble() }.toFloat() / valid.size,
            y = valid.sumOf { it.y.toDouble() }.toFloat() / valid.size,
            z = valid.sumOf { it.z.toDouble() }.toFloat() / valid.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        mediaPipeProcessor.close()
    }
}
