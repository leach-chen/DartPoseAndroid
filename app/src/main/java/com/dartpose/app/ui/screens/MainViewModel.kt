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
    
    init {
        viewModelScope.launch {
            mediaPipeProcessor.poseData.collect { poseData ->
                _poseLandmarks.value = poseData?.landmarks ?: emptyList()
            }
        }
        
        viewModelScope.launch {
            mediaPipeProcessor.handData.collect { hands ->
                val allLandmarks = mutableListOf<HandLandmark>()
                hands.forEach { hand ->
                    allLandmarks.addAll(hand.landmarks)
                }
                _handLandmarks.value = allLandmarks
                
                // Track hand movement for trajectory
                if (_isTracking.value && hands.isNotEmpty()) {
                    val firstHand = hands.firstOrNull()
                    val landmarks = firstHand?.landmarks
                    val currentTime = System.currentTimeMillis()
                    
                    if (landmarks != null && landmarks.isNotEmpty()) {
                        // Use index finger tip (8) or wrist (0)
                        val trackingPoint = landmarks.getOrNull(8) ?: landmarks.getOrNull(0) ?: return@collect
                        
                        val newPoint = TrajectoryPoint(trackingPoint.x, trackingPoint.y, currentTime)
                        val currentTrajectory = _trajectory.value.toMutableList()
                        
                        if (lastHandPosition != null && lastTimestamp > 0) {
                            val timeDiff = currentTime - lastTimestamp
                            val dx = newPoint.x - lastHandPosition!!.x
                            val dy = newPoint.y - lastHandPosition!!.y
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            
                            // Interpolate for fast movement
                            if (distance > 0.015f && timeDiff in 1..100) {
                                val steps = kotlin.math.min((distance / 0.008f).toInt(), 15)
                                for (i in 1 until steps) {
                                    val fraction = i.toFloat() / steps
                                    currentTrajectory.add(
                                        TrajectoryPoint(
                                            lastHandPosition!!.x + dx * fraction,
                                            lastHandPosition!!.y + dy * fraction,
                                            lastTimestamp + (timeDiff * fraction).toLong()
                                        )
                                    )
                                }
                            }
                        }
                        
                        currentTrajectory.add(newPoint)
                        
                        // Keep trajectory length manageable
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
    }
    
    @OptIn(ExperimentalGetImage::class)
    fun processFrame(imageProxy: ImageProxy) {
        mediaPipeProcessor.processFrame(imageProxy)
    }
    
    fun clearTrajectory() {
        _trajectory.value = emptyList()
        lastHandPosition = null
        lastTimestamp = 0
    }
    
    fun toggleTracking() {
        _isTracking.value = !_isTracking.value
        if (!_isTracking.value) {
            lastHandPosition = null
            lastTimestamp = 0
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        mediaPipeProcessor.close()
    }
}
