package de.eschoenawa.urbanscanner.scanprocess

import android.media.Image
import boofcv.alg.color.ColorFormat
import boofcv.android.ConvertCameraImage
import boofcv.struct.image.GrayU8
import boofcv.struct.image.ImageType
import boofcv.struct.image.Planar
import com.google.ar.core.*
import de.eschoenawa.urbanscanner.helper.TimingHelper.withTimer
import de.eschoenawa.urbanscanner.helper.fromWorldToGeoPose
import de.eschoenawa.urbanscanner.helper.toGeoPose
import de.eschoenawa.urbanscanner.model.DepthCameraIntrinsics
import de.eschoenawa.urbanscanner.model.GeoPose
import de.eschoenawa.urbanscanner.model.RawPixelData
import de.eschoenawa.urbanscanner.model.Scan
import org.ddogleg.struct.DogArray_I8
import pabeles.concurrency.GrowArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class FrameImageData(
    arFrame: Frame,
    workArrays: GrowArray<DogArray_I8>,
    private val scan: Scan,
    private val depthImage: Image,
    private val confidenceImage: Image,
    private val cameraImage: Image,
    private val earth: Earth?
) {
    private lateinit var depthBuffer: ShortBuffer
    private lateinit var confidenceBuffer: ByteBuffer
    private lateinit var boofImage: Planar<GrayU8>
    private lateinit var cameraCoordinatesOfDepthTextureCorners: FloatArray
    private lateinit var depthCameraIntrinsics: DepthCameraIntrinsics
    private val cameraPose: Pose
    private val cameraGeoPose: GeoPose?

    val width: Int
        get() = depthImage.width

    val height: Int
        get() = depthImage.height

    init {
        initBuffers()
        initBoofImage(workArrays)
        initCornerCoordinates(arFrame)
        initDepthCameraIntrinsics(arFrame.camera)
        cameraPose = arFrame.camera.pose
        cameraGeoPose = earth?.cameraGeospatialPose?.toGeoPose()
    }

    fun getPixelDataAt(x: Int, y: Int): RawPixelData? {
        val mmDepth = withTimer("getPixelDepth") {
            depthBuffer.get(y * depthImage.width + x)
        }
        if (!mmDepth.isValidDepthData()) {
            return null
        }
        val depthMeters = mmDepth / 1000f
        if (depthMeters > scan.depthLimit) {
            return null
        }
        val depthConfidence = withTimer("getPixelConfidence") {
            confidenceBuffer.get(
                y * confidenceImage.planes[0].rowStride
                        + x * confidenceImage.planes[0].pixelStride
            ).toUByte()
        }
        val normalizedDepthConfidence = depthConfidence.toInt() / 255f
        if (normalizedDepthConfidence < scan.confidenceCutoff) {
            return null
        }
        val cameraImageCoordinates = FloatArray(2)
        withTimer("calculateImageCoordinates") {
            val normalizedDepthCoordinates = floatArrayOf(
                x.toFloat() / depthImage.width.toFloat(),
                y.toFloat() / depthImage.height.toFloat()
            )
            cameraImageCoordinates[0] =
                cameraCoordinatesOfDepthTextureCorners[0] + ((cameraCoordinatesOfDepthTextureCorners[2] - cameraCoordinatesOfDepthTextureCorners[0]) * normalizedDepthCoordinates[0])
            cameraImageCoordinates[1] =
                cameraCoordinatesOfDepthTextureCorners[1] + ((cameraCoordinatesOfDepthTextureCorners[3] - cameraCoordinatesOfDepthTextureCorners[1]) * normalizedDepthCoordinates[1])
        }
        val rgb = withTimer("getRgbValues") {
            intArrayOf(
                boofImage.getBand(0)
                    .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()),
                boofImage.getBand(1)
                    .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt()),
                boofImage.getBand(2)
                    .get(cameraImageCoordinates[0].toInt(), cameraImageCoordinates[1].toInt())
            )
        }
        val worldPoint = FloatArray(4)
        withTimer("calculateWorldCoordinates") {
            depthCameraIntrinsics.calculateWorldPointFromPixelsWithDepth(
                worldPoint = worldPoint,
                cameraPose = cameraPose,
                x = x,
                y = y,
                depthMeters = depthMeters
            )
        }
        return if (scan.isGeoReferenced) {
            if (earth == null) throw IllegalStateException("Earth cannot be null when georeferencing!")
            val geoPose = withTimer("georeference") {
                worldPoint.fromWorldToGeoPose(cameraPose, cameraGeoPose!!)
                //TODO remove val worldPose = Pose.makeTranslation(worldPoint)
                //TODO remove earth.getGeospatialPose(worldPose)
            }
            RawPixelData(
                worldPoint[0],
                worldPoint[1],
                worldPoint[2],
                depthConfidence,
                rgb[0].toUByte(),
                rgb[1].toUByte(),
                rgb[2].toUByte(),
                geoPose.latitude,
                geoPose.longitude,
                geoPose.altitude
            )
        } else {
            RawPixelData(
                worldPoint[0],
                worldPoint[1],
                worldPoint[2],
                depthConfidence,
                rgb[0].toUByte(),
                rgb[1].toUByte(),
                rgb[2].toUByte()
            )
        }
    }

    private fun initBuffers() {
        depthBuffer = depthImage.planes[0].buffer.createUsableBufferCopy().asShortBuffer()
        confidenceBuffer = confidenceImage.planes[0].buffer.createUsableBufferCopy()
    }

    private fun initBoofImage(workArrays: GrowArray<DogArray_I8>) {
        boofImage =
            ImageType.pl(3, GrayU8::class.java).createImage(cameraImage.width, cameraImage.height)
        ConvertCameraImage.imageToBoof(cameraImage, ColorFormat.RGB, boofImage, workArrays)
    }

    private fun initCornerCoordinates(arFrame: Frame) {
        cameraCoordinatesOfDepthTextureCorners = FloatArray(4)
        arFrame.transformCoordinates2d(
            Coordinates2d.TEXTURE_NORMALIZED,
            floatArrayOf(0f, 0f, 1f, 1f),
            Coordinates2d.IMAGE_PIXELS,
            cameraCoordinatesOfDepthTextureCorners
        )
    }

    private fun initDepthCameraIntrinsics(camera: Camera) {
        val cameraTextureIntrinsics = camera.textureIntrinsics
        depthCameraIntrinsics =
            DepthCameraIntrinsics.scaleTextureIntrinsicsToDepthImageDimensions(
                cameraTextureIntrinsics, depthImage
            )
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
}
