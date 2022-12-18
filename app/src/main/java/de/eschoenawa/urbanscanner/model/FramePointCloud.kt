package de.eschoenawa.urbanscanner.model

import android.media.Image
import boofcv.alg.color.ColorFormat
import boofcv.android.ConvertCameraImage
import boofcv.struct.image.GrayU8
import boofcv.struct.image.ImageType
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.helper.UtmCoordinateConverter
import org.cts.CRSFactory
import org.cts.crs.CoordinateReferenceSystem
import org.cts.op.projection.UniversalTransverseMercator
import org.cts.util.UTMUtils
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

class FramePointCloud(
    depthImage: Image,
    confidenceImage: Image,
    cameraImage: Image,
    camera: Camera,
    earth: Earth?,
    arFrame: Frame,
    timestamp: Long,
    scan: Scan
) {
    var points: FloatBuffer
    val timestamp: Long
    //TODO expose point count & rejected point count?

    var coordinateConverter: UtmCoordinateConverter? = null

    companion object {
        private const val TAG = "FPC"

        //TODO add RGB, Position & Height accuracy, (Calculated Position accuracy (Angle & distance & starting inaccuracy))
        private const val FLOATS_PER_POINT = 7  // X, Y, Z, r, g, b, confidence
        private const val CONFIDENCE_INDEX = 6  // Index of confidence value in float array of point

        fun createPointCloudIfDataIsAvailable(
            arFrame: Frame,
            earth: Earth?,
            lastDepthTimestamp: Long,
            scan: Scan
        ): PointCloudResult {
            TimingHelper.startTimer("prepareForPointCloud")
            if (arFrame.camera.trackingState != TrackingState.TRACKING) {
                return PointCloudResult.CameraNotTrackingResult
            }
            if (scan.isGeoReferenced) {
                if (earth?.trackingState != TrackingState.TRACKING) {
                    return if (earth == null) PointCloudResult.EarthNullResult else PointCloudResult.EarthNotTrackingResult
                }
                if (earth.cameraGeospatialPose.horizontalAccuracy > scan.horizontalAccuracyThreshold) {
                    return PointCloudResult.HorizontalAccuracyTooBadResult
                }
                if (earth.cameraGeospatialPose.verticalAccuracy > scan.verticalAccuracyThreshold) {
                    return PointCloudResult.VerticalAccuracyTooBadResult
                }
                if (earth.cameraGeospatialPose.headingAccuracy > scan.headingAccuracyThreshold) {
                    return PointCloudResult.HeadingAccuracyTooBadResult
                }
            }
            try {
                arFrame.acquireRawDepthImage16Bits().use { depthImage ->
                    if (lastDepthTimestamp == depthImage.timestamp) {
                        return PointCloudResult.OnlyReprojectedDataResult
                    }
                    arFrame.acquireRawDepthConfidenceImage().use { confidenceImage ->
                        arFrame.acquireCameraImage().use { cameraImage ->
                            val cameraPoseMatrix = FloatArray(16)
                            arFrame.camera.pose.toMatrix(cameraPoseMatrix, 0)
                            TimingHelper.endTimer("prepareForPointCloud")
                            val pointCloud = FramePointCloud(
                                depthImage,
                                confidenceImage,
                                cameraImage,
                                arFrame.camera,
                                earth,
                                arFrame,
                                depthImage.timestamp,
                                scan
                            )
                            return PointCloudResult.PointCloudGeneratedResult(
                                pointCloud
                            )
                        }
                    }
                }
            } catch (e: NotYetAvailableException) {
                return PointCloudResult.ImagesNotAvailableResult
            }
        }
    }

    //TODO refactor buffers to their own classes to allow convenience methods for pixel coordinate access etc.
    //TODO split into methods
    init {
        this.timestamp = timestamp
        TimingHelper.startTimer("createBuffers")
        val cameraTextureIntrinsics = camera.textureIntrinsics
        val depthBuffer = depthImage.planes[0].buffer.createUsableBufferCopy().asShortBuffer()
        val confidenceBuffer = confidenceImage.planes[0].buffer.createUsableBufferCopy()
        val depthCameraIntrinsics =
            DepthCameraIntrinsics.scaleTextureIntrinsicsToDepthImageDimensions(
                cameraTextureIntrinsics, depthImage
            )
        TimingHelper.endTimer("createBuffers")
        val step =
            ceil(sqrt((depthImage.width * depthImage.height / scan.maxPointsPerFrame.toFloat()))).toInt()
        points =
            FloatBuffer.allocate(depthImage.width / step * depthImage.height / step * FLOATS_PER_POINT)
        TimingHelper.startTimer("convertImage")
        val boofImg =
            ImageType.pl(3, GrayU8::class.java).createImage(cameraImage.width, cameraImage.height)
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
                if (depthMeters > scan.depthLimit) {
                    TimingHelper.endTimer("getDepthAndConfidence")
                    continue
                }
                val depthConfidence = confidenceBuffer.get(
                    y * confidenceImage.planes[0].rowStride
                            + x * confidenceImage.planes[0].pixelStride
                )
                val normalizedDepthConfidence = depthConfidence.toUByte().toInt() / 255f
                if (normalizedDepthConfidence < scan.confidenceCutoff) {
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
                cameraImageCoordinates[0] =
                    cameraCoordinatesOfDepthTextureCorners[0] + ((cameraCoordinatesOfDepthTextureCorners[2] - cameraCoordinatesOfDepthTextureCorners[0]) * normalizedDepthCoordinates[0])
                cameraImageCoordinates[1] =
                    cameraCoordinatesOfDepthTextureCorners[1] + ((cameraCoordinatesOfDepthTextureCorners[3] - cameraCoordinatesOfDepthTextureCorners[1]) * normalizedDepthCoordinates[1])
                TimingHelper.endTimer("getRgbCoordinates")
                TimingHelper.startTimer("getRgbValues")
                val rgb = floatArrayOf(
                    boofImg.getBand(0)
                        .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt())
                        .toFloat(),
                    boofImg.getBand(1)
                        .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt())
                        .toFloat(),
                    boofImg.getBand(2)
                        .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt())
                        .toFloat()
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
                if (scan.isGeoReferenced) {
                    if (earth == null) throw IllegalStateException("Earth cannot be null when georeferencing!")
                    TimingHelper.startTimer("calculateLatLongCoordinates")
                    val worldPose = Pose.makeTranslation(worldPoint)
                    val geospatialWorldPose = earth.getGeospatialPose(worldPose)
                    TimingHelper.endTimer("calculateLatLongCoordinates")
                    TimingHelper.startTimer("calculateUtmCoordinates")
                    val utmPosition = geospatialWorldPose.toUtm(scan)
                    worldPoint[0] = utmPosition[0]
                    worldPoint[1] = utmPosition[1]
                    worldPoint[2] = utmPosition[2]
                    TimingHelper.endTimer("calculateUtmCoordinates")
                }
                //TODO store VPS points (do refactor to some form of processor first)
                TimingHelper.startTimer("putInPointsBuffer")
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
        while (hasRemaining()) {
            result.put(get())
        }
        result.rewind()
        return result
    }

    private fun Short.isValidDepthData(): Boolean {
        return this != 0.toShort()
    }

    private fun GeospatialPose.toUtm(scan: Scan): FloatArray {
        if (coordinateConverter == null) {
            coordinateConverter = if (scan.epsgCode.isEmpty()) {
                UtmCoordinateConverter.fromLatLong(latitude.toFloat(), longitude.toFloat())
            } else {
                UtmCoordinateConverter(scan.epsgCode)
            }
        }
        val result = coordinateConverter!!.getUtmCoordinates(latitude, longitude)

        return floatArrayOf(result[0].toFloat(), altitude.toFloat(), result[1].toFloat())
    }

    sealed interface PointCloudResult {
        object CameraNotTrackingResult : PointCloudResult
        object EarthNullResult : PointCloudResult
        object EarthNotTrackingResult : PointCloudResult
        object HorizontalAccuracyTooBadResult : PointCloudResult
        object VerticalAccuracyTooBadResult : PointCloudResult
        object HeadingAccuracyTooBadResult : PointCloudResult
        object OnlyReprojectedDataResult : PointCloudResult
        object ImagesNotAvailableResult : PointCloudResult
        class PointCloudGeneratedResult(val framePointCloud: FramePointCloud) : PointCloudResult
    }
}
