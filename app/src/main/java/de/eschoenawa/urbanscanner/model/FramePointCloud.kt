package de.eschoenawa.urbanscanner.model

import android.media.Image
import android.util.Log
import boofcv.alg.color.ColorFormat
import boofcv.android.ConvertCameraImage
import boofcv.struct.image.GrayU8
import boofcv.struct.image.ImageType
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.sceneform.math.Vector3
import org.ddogleg.struct.DogArray_I8
import pabeles.concurrency.GrowArray
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.experimental.and
import kotlin.math.ceil
import kotlin.math.sqrt

class FramePointCloud(depthImage: Image, confidenceImage: Image, cameraImage: Image, camera: Camera, earth: Earth, arFrame: Frame) {
    var points: FloatBuffer

    companion object {
        private const val TAG = "FPC"
        //TODO change to useful number and/or make configurable
        private const val EARTH_HORIZONTAL_ACCURACY_THRESHOLD = 3
        //TODO add RGB, Position & Height accuracy, (Calculated Position accuracy (Angle & distance & starting inaccuracy))
        private const val FLOATS_PER_POINT = 7  // X, Y, Z, r, g, b, confidence
        //TODO make configurable
        private const val MAX_POINTS_PER_FRAME = 20000
        //TODO make configurable
        private const val CONFIDENCE_CUTOFF = 0.1f

        fun createPointCloudIfDataIsAvailable(arFrame: Frame, earth: Earth?): FramePointCloud? {
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
                            return FramePointCloud(depthImage, confidenceImage, cameraImage, arFrame.camera, earth, arFrame)
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
        val cameraTextureIntrinsics = camera.textureIntrinsics
        val depthBuffer = depthImage.planes[0].buffer.createUsableBufferCopy().asShortBuffer()
        val confidenceBuffer = confidenceImage.planes[0].buffer.createUsableBufferCopy()
        val depthCameraIntrinsics = DepthCameraIntrinsics.scaleTextureIntrinsicsToDepthImageDimensions(
            cameraTextureIntrinsics, depthImage
        )
        val step = ceil(sqrt((depthImage.width * depthImage.height / MAX_POINTS_PER_FRAME.toFloat()))).toInt()
        points = FloatBuffer.allocate(depthImage.width / step * depthImage.height / step * FLOATS_PER_POINT)
        val worldPoint = FloatArray(4)
        val boofImg = ImageType.pl(3, GrayU8::class.java).createImage(cameraImage.width, cameraImage.height)
        ConvertCameraImage.imageToBoof(cameraImage, ColorFormat.RGB, boofImg, null)
        for (y in 0 until depthImage.height step step) {
            for (x in 0 until depthImage.width step step) {
                val mmDepth = depthBuffer.get(y * depthImage.width + x)
                if (!mmDepth.isValidDepthData()) {
                    continue
                }
                val depthMeters = mmDepth / 1000f
                val depthConfidence = confidenceBuffer.get(y * confidenceImage.planes[0].rowStride
                        + x * confidenceImage.planes[0].pixelStride)
                //TODO depthConfidence & 0xff?
                val normalizedDepthConfidence = depthConfidence.toUByte().toInt() / 255f
                if (normalizedDepthConfidence < CONFIDENCE_CUTOFF) {
                    continue
                }
                val normalizedDepthCoordinates = floatArrayOf(
                    x.toFloat() / depthImage.width.toFloat(),
                    y.toFloat() / depthImage.height.toFloat()
                )
                val cameraImageCoordinates = FloatArray(2)
                arFrame.transformCoordinates2d(
                    Coordinates2d.TEXTURE_NORMALIZED,
                    normalizedDepthCoordinates,
                    Coordinates2d.IMAGE_PIXELS,
                    cameraImageCoordinates
                )
                val rgb = floatArrayOf(
                    boofImg.getBand(0).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat(),
                    boofImg.getBand(1).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat(),
                    boofImg.getBand(2).get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()).toFloat()
                )
                depthCameraIntrinsics.calculateWorldPointFromPixelsWithDepth(
                    worldPoint = worldPoint,
                    cameraPose = camera.pose,
                    x = x,
                    y = y,
                    depthMeters = depthMeters
                )
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
            }
        }
        points.rewind()
    }

    fun persistToFile(filename: String) {
        FileWriter(filename, true).use { fw ->
            while (points.hasRemaining()) {
                val point = FloatArray(7) { points.get() }
                if (point[6] == 0f) {
                    //If no confidence is set, ignore point
                    continue
                }
                val line = "${point[0]},${point[1]},${point[2]},${point[3]},${point[4]},${point[5]},${point[6]}\n"
                fw.write(line)
            }
        }
        Log.d(TAG, "Point data exported to file '$filename'!")
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
