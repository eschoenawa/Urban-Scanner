package de.eschoenawa.urbanscanner.model

import android.media.Image
import android.opengl.Matrix
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Pose

data class DepthCameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float
) {
    private val cameraPoint = FloatArray(4)
    private val modelMatrix = FloatArray(16)

    companion object {
        fun scaleTextureIntrinsicsToDepthImageDimensions(cameraTextureIntrinsics: CameraIntrinsics, depthImage: Image): DepthCameraIntrinsics {
            val intrinsicDimensions = cameraTextureIntrinsics.imageDimensions
            val depthWidth = depthImage.width
            val depthHeight = depthImage.height
            val fx = cameraTextureIntrinsics.focalLength[0] * depthWidth / intrinsicDimensions[0]
            val fy = cameraTextureIntrinsics.focalLength[1] * depthHeight / intrinsicDimensions[1]
            val cx = cameraTextureIntrinsics.principalPoint[0] * depthWidth / intrinsicDimensions[0]
            val cy = cameraTextureIntrinsics.principalPoint[1] * depthHeight / intrinsicDimensions[1]
            return DepthCameraIntrinsics(fx, fy, cx, cy)
        }
    }

    fun calculateWorldPointFromPixelsWithDepth(worldPoint: FloatArray, cameraPose: Pose, x: Int, y: Int, depthMeters: Float) {
        cameraPoint[0] = depthMeters * (x -  cx) / fx
        cameraPoint[1] = depthMeters * (cy - y) / fy
        cameraPoint[2] = -depthMeters
        cameraPoint[3] = 1f

        cameraPose.toMatrix(modelMatrix, 0)
        Matrix.multiplyMV(worldPoint, 0, modelMatrix, 0, cameraPoint, 0)
    }
}
