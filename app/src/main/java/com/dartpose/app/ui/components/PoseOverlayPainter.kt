package com.dartpose.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import com.dartpose.app.domain.model.HandLandmark
import com.dartpose.app.domain.model.PoseLandmark
import com.dartpose.app.domain.model.TrajectoryPoint
import kotlin.math.max

class PoseOverlayPainter(
    private var imageWidth: Int = 1,
    private var imageHeight: Int = 1
) {
    private val posePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val poseLinePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val handPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val trajectoryPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val trajectoryPointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    fun drawPoseLandmarks(bitmap: Bitmap, landmarks: List<PoseLandmark>): Bitmap {
        val canvas = android.graphics.Canvas(bitmap)
        
        if (landmarks.isEmpty()) return bitmap

        val connections = listOf(
            // Face
            listOf(0, 1), listOf(1, 2), listOf(2, 3), listOf(3, 7),
            listOf(0, 4), listOf(4, 5), listOf(5, 6), listOf(6, 8),
            listOf(9, 10),
            // Body
            listOf(11, 12), listOf(11, 13), listOf(13, 15), listOf(15, 17),
            listOf(15, 19), listOf(15, 21), listOf(17, 19),
            listOf(12, 14), listOf(14, 16), listOf(16, 18), listOf(16, 20),
            listOf(16, 22), listOf(18, 20),
            listOf(11, 23), listOf(12, 24), listOf(23, 24),
            listOf(23, 25), listOf(25, 27), listOf(27, 29), listOf(29, 31),
            listOf(31, 33), listOf(31, 35),
            listOf(24, 26), listOf(26, 28), listOf(28, 30), listOf(30, 32),
            listOf(32, 34), listOf(32, 36)
        )

        // Draw connections
        for (connection in connections) {
            if (connection[0] < landmarks.size && connection[1] < landmarks.size) {
                val start = landmarks[connection[0]]
                val end = landmarks[connection[1]]
                
                if (start.visibility > 0.5f && end.visibility > 0.5f) {
                    canvas.drawLine(
                        start.x * imageWidth,
                        start.y * imageHeight,
                        end.x * imageWidth,
                        end.y * imageHeight,
                        poseLinePaint
                    )
                }
            }
        }

        // Draw landmarks
        for (landmark in landmarks) {
            if (landmark.visibility > 0.5f) {
                canvas.drawCircle(
                    landmark.x * imageWidth,
                    landmark.y * imageHeight,
                    8f,
                    posePaint
                )
            }
        }

        return bitmap
    }

    fun drawHandLandmarks(bitmap: Bitmap, handLandmarks: List<HandLandmark>): Bitmap {
        val canvas = android.graphics.Canvas(bitmap)
        
        if (handLandmarks.isEmpty()) return bitmap

        val connections = listOf(
            // Palm
            listOf(0, 1), listOf(1, 2), listOf(2, 3), listOf(3, 4),
            listOf(0, 5), listOf(5, 6), listOf(6, 7), listOf(7, 8),
            listOf(0, 9), listOf(9, 10), listOf(10, 11), listOf(11, 12),
            listOf(0, 13), listOf(13, 14), listOf(14, 15), listOf(15, 16),
            listOf(0, 17), listOf(17, 18), listOf(18, 19), listOf(19, 20)
        )

        // Draw connections
        for (connection in connections) {
            if (connection[0] < handLandmarks.size && connection[1] < handLandmarks.size) {
                val start = handLandmarks[connection[0]]
                val end = handLandmarks[connection[1]]
                
                canvas.drawLine(
                    start.x * imageWidth,
                    start.y * imageHeight,
                    end.x * imageWidth,
                    end.y * imageHeight,
                    poseLinePaint
                )
            }
        }

        // Draw landmarks
        for (landmark in handLandmarks) {
            canvas.drawCircle(
                landmark.x * imageWidth,
                landmark.y * imageHeight,
                6f,
                handPaint
            )
        }

        return bitmap
    }

    fun drawTrajectory(bitmap: Bitmap, trajectory: List<TrajectoryPoint>): Bitmap {
        val canvas = android.graphics.Canvas(bitmap)
        
        if (trajectory.size < 2) return bitmap

        // Draw trajectory line
        val path = android.graphics.Path()
        path.moveTo(trajectory[0].x * imageWidth, trajectory[0].y * imageHeight)
        
        for (i in 1 until trajectory.size) {
            path.lineTo(trajectory[i].x * imageWidth, trajectory[i].y * imageHeight)
        }
        
        canvas.drawPath(path, trajectoryPaint)

        // Draw trajectory points
        for (point in trajectory) {
            canvas.drawCircle(
                point.x * imageWidth,
                point.y * imageHeight,
                5f,
                trajectoryPointPaint
            )
        }

        return bitmap
    }

    fun drawAll(
        bitmap: Bitmap,
        poseLandmarks: List<PoseLandmark>?,
        handLandmarks: List<HandLandmark>?,
        trajectory: List<TrajectoryPoint>
    ): Bitmap {
        var result = bitmap
        
        if (!poseLandmarks.isNullOrEmpty()) {
            result = drawPoseLandmarks(result, poseLandmarks)
        }
        
        if (!handLandmarks.isNullOrEmpty()) {
            result = drawHandLandmarks(result, handLandmarks)
        }
        
        if (trajectory.isNotEmpty()) {
            result = drawTrajectory(result, trajectory)
        }
        
        return result
    }
}
