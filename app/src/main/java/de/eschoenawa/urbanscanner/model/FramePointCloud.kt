package de.eschoenawa.urbanscanner.model

import android.media.Image
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import boofcv.alg.color.ColorFormat
import boofcv.android.ConvertCameraImage
import boofcv.struct.image.GrayU8
import boofcv.struct.image.ImageType
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import de.eschoenawa.urbanscanner.helper.TimingHelper
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

class FramePointCloud(depthImage: Image, confidenceImage: Image, cameraImage: Image, camera: Camera, earth: Earth, arFrame: Frame, lifecycleCoroutineScope: LifecycleCoroutineScope) {
    var points: FloatBuffer

    companion object {
        private const val TAG = "FPC"
        //TODO change to useful number and/or make configurable
        private const val EARTH_HORIZONTAL_ACCURACY_THRESHOLD = 15
        //TODO add RGB, Position & Height accuracy, (Calculated Position accuracy (Angle & distance & starting inaccuracy))
        private const val FLOATS_PER_POINT = 7  // X, Y, Z, r, g, b, confidence
        private const val CONFIDENCE_INDEX = 6  // Index of confidence value in float array of point
        //TODO make configurable
        private const val MAX_POINTS_PER_FRAME = 20000
        //TODO make configurable
        private const val CONFIDENCE_CUTOFF = 0.5f

        fun createPointCloudIfDataIsAvailable(arFrame: Frame, earth: Earth?, lifecycleCoroutineScope: LifecycleCoroutineScope): FramePointCloud? {
            TimingHelper.startTimer("prepareForPointCloud")
            if (arFrame.camera.trackingState != TrackingState.TRACKING) {
                Log.w(TAG, "Camera not tracking!")
                return null
            }
            if (earth?.trackingState != TrackingState.TRACKING) {
                Log.w(TAG, "Earth not tracking or null!")
                return null
            }
            if (earth.cameraGeospatialPose.horizontalAccuracy > EARTH_HORIZONTAL_ACCURACY_THRESHOLD) {
                Log.w(TAG, "Horizontal accuracy not good enough!")
                return null
            }
            try {
                arFrame.acquireRawDepthImage16Bits().use { depthImage ->
                    /*
                    if (arFrame.timestamp != depthImage.timestamp) {
                        Log.w(TAG, "Outdated depth data (FrameTime: ${arFrame.timestamp}; ImageTime: ${depthImage.timestamp})!")
                        return null
                    }
                     */
                    arFrame.acquireRawDepthConfidenceImage().use { confidenceImage ->
                        arFrame.acquireCameraImage().use { cameraImage ->
                            val cameraPoseMatrix = FloatArray(16)
                            arFrame.camera.pose.toMatrix(cameraPoseMatrix, 0)
                            TimingHelper.endTimer("prepareForPointCloud")
                            return FramePointCloud(depthImage, confidenceImage, cameraImage, arFrame.camera, earth, arFrame, lifecycleCoroutineScope)
                        }
                    }
                }
            } catch (e: NotYetAvailableException) {
                Log.w(TAG, "Image(s) not yet available!", e)
                return null
            }
        }
    }

    //TODO refactor buffers to their own classes to allow convenience methods for pixel coordinate access etc.
    //TODO split into methods
    init {
        TimingHelper.startTimer("createBuffers")
        val cameraTextureIntrinsics = camera.textureIntrinsics
        val depthBuffer = depthImage.planes[0].buffer.createUsableBufferCopy().asShortBuffer()
        val confidenceBuffer = confidenceImage.planes[0].buffer.createUsableBufferCopy()
        val depthCameraIntrinsics = DepthCameraIntrinsics.scaleTextureIntrinsicsToDepthImageDimensions(
            cameraTextureIntrinsics, depthImage
        )
        TimingHelper.endTimer("createBuffers")
        val step = ceil(sqrt((depthImage.width * depthImage.height / MAX_POINTS_PER_FRAME.toFloat()))).toInt()
        points = FloatBuffer.allocate(depthImage.width / step * depthImage.height / step * FLOATS_PER_POINT)
        TimingHelper.startTimer("convertImage")
        val boofImg = ImageType.pl(3, GrayU8::class.java).createImage(cameraImage.width, cameraImage.height)
        ConvertCameraImage.imageToBoof(cameraImage, ColorFormat.RGB, boofImg, null)
        TimingHelper.endTimer("convertImage")
        TimingHelper.startTimer("getTextureCornerCoordinates")
        val cameraCoordinatesOfDepthTextureCorners = FloatArray(4)
        arFrame.transformCoordinates2d(
            Coordinates2d.TEXTURE_NORMALIZED,
            floatArrayOf(0f, 0f, 1f, 1f),
            Coordinates2d.IMAGE_PIXELS,
            cameraCoordinatesOfDepthTextureCorners
        )
        TimingHelper.endTimer("getTextureCornerCoordinates")
        for (y in 0 until depthImage.height step step) {
            for (x in 0 until depthImage.width step step) {
                TimingHelper.startTimer("getDepthAndConfidence")
                val mmDepth = depthBuffer.get(y * depthImage.width + x)
                if (!mmDepth.isValidDepthData()) {
                    TimingHelper.endTimer("getDepthAndConfidence")
                    continue
                }
                val depthMeters = mmDepth / 1000f
                val depthConfidence = confidenceBuffer.get(y * confidenceImage.planes[0].rowStride
                        + x * confidenceImage.planes[0].pixelStride)
                val normalizedDepthConfidence = depthConfidence.toUByte().toInt() / 255f
                if (normalizedDepthConfidence < CONFIDENCE_CUTOFF) {
                    TimingHelper.endTimer("getDepthAndConfidence")
                    continue
                }
                TimingHelper.endTimer("getDepthAndConfidence")
                TimingHelper.startTimer("getRgbCoordinates")
                val normalizedDepthCoordinates = floatArrayOf(
                    x.toFloat() / depthImage.width.toFloat(),
                    y.toFloat() / depthImage.height.toFloat()
                )
                val cameraImageCoordinates = FloatArray(2)
                cameraImageCoordinates[0] = cameraCoordinatesOfDepthTextureCorners[0] + ((cameraCoordinatesOfDepthTextureCorners[2] - cameraCoordinatesOfDepthTextureCorners[0]) * normalizedDepthCoordinates[0])
                cameraImageCoordinates[1] = cameraCoordinatesOfDepthTextureCorners[1] + ((cameraCoordinatesOfDepthTextureCorners[3] - cameraCoordinatesOfDepthTextureCorners[1]) * normalizedDepthCoordinates[1])
                TimingHelper.endTimer("getRgbCoordinates")
                TimingHelper.startTimer("getRgbValues")
                val rgb = floatArrayOf(
                    boofImg.getBand(0).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat(),
                    boofImg.getBand(1).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat(),
                    boofImg.getBand(2).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat()
                )
                TimingHelper.endTimer("getRgbValues")
                TimingHelper.startTimer("calculateWorldPoints")
                val worldPoint = FloatArray(4)
                depthCameraIntrinsics.calculateWorldPointFromPixelsWithDepth(
                    worldPoint = worldPoint,
                    cameraPose = camera.pose,
                    x = x,
                    y = y,
                    depthMeters = depthMeters
                )
                TimingHelper.endTimer("calculateWorldPoints")
                TimingHelper.startTimer("putInPointsBuffer")
                /*
                val worldPose = Pose.makeTranslation(worldPoint)
                val geospatialWorldPose = earth.getGeospatialPose(worldPose)
                worldPoint[0] = geospatialWorldPose.longitude.toFloat()
                worldPoint[1] = geospatialWorldPose.altitude.toFloat()
                worldPoint[2] = geospatialWorldPose.latitude.toFloat()
                 */
                points.apply {
                    put(worldPoint[0])
                    put(worldPoint[1])
                    put(worldPoint[2])
                    put(rgb[0])
                    put(rgb[1])
                    put(rgb[2])
                    put(normalizedDepthConfidence)
                }
                TimingHelper.endTimer("putInPointsBuffer")
            }
        }
        points.rewind()
    }

    fun persistToFile(filename: String): Int {
        TimingHelper.startTimer("preparePersistToFile")
        var pointCount = 0
        val fileString = buildString {
            while (points.hasRemaining()) {
                val point = FloatArray(FLOATS_PER_POINT) { points.get() }
                if (point[CONFIDENCE_INDEX] == 0f) {
                    //If no confidence is set, ignore point
                    continue
                }
                pointCount++
                point.forEachIndexed { index, datum ->
                    append(datum)
                    if (index < FLOATS_PER_POINT - 1) {
                        append(",")
                    } else {
                        append("\n")
                    }
                }
            }
        }
        TimingHelper.endTimer("preparePersistToFile")
        TimingHelper.startTimer("persistToFile")
        FileWriter(filename, true).use { fw ->
            fw.write(fileString)
        }
        TimingHelper.endTimer("persistToFile")
        return pointCount
    }

    /**
     * This function creates a copy of a given ByteBuffer. Depth data is provided in little endian
     * while Java uses big endian by default, so little endian has to be configured for the copy.
     */
    private fun ByteBuffer.createUsableBufferCopy(): ByteBuffer {
        val result = ByteBuffer.allocate(capacity())
        result.order(ByteOrder.LITTLE_ENDIAN)
        while(hasRemaining()) {
            result.put(get())
        }
        result.rewind()
        return result
    }

    private fun Short.isValidDepthData(): Boolean {
        return this != 0.toShort()
    }
}
