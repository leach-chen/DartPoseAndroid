package com.dartpose.app.domain.model

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class PoseData(
    val landmarks: List<PoseLandmark>,
    val worldLandmarks: List<PoseLandmark>
)

data class HandLandmark(
    val x: Float,
    val y: Float,
    val z: Float
)

data class HandData(
    val landmarks: List<HandLandmark>,
    val handedness: String // "Left" or "Right"
)

data class TrajectoryPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)
