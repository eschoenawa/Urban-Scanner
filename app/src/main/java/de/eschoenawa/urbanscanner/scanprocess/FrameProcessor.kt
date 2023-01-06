package de.eschoenawa.urbanscanner.scanprocess

import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.helper.TimingHelper.withTimer
import de.eschoenawa.urbanscanner.model.FramePointCloud
import de.eschoenawa.urbanscanner.model.Scan
import org.ddogleg.struct.DogArray_I8
import pabeles.concurrency.GrowArray
import kotlin.math.ceil
import kotlin.math.sqrt

class FrameProcessor(private val scan: Scan) {

    private val workArrays = GrowArray(::DogArray_I8)
    private var lastDepthTimestamp = 0L

    var totalPointCount = 0L
        private set

    fun processFrame(arFrame: Frame, earth: Earth?): FramePointCloud? {
        val preconditionsMet = withTimer("checkPreconditions") {
            checkPreconditions(arFrame, earth)
        }
        if (!preconditionsMet) {
            return null
        }
        TimingHelper.startTimer("openImages")
        try {
            arFrame.acquireRawDepthImage16Bits().use { depthImage ->
                if (lastDepthTimestamp == depthImage.timestamp) {
                    TimingHelper.endTimer("openImages")
                    return null
                } else {
                    lastDepthTimestamp = depthImage.timestamp
                }
                arFrame.acquireRawDepthConfidenceImage().use { confidenceImage ->
                    arFrame.acquireCameraImage().use { cameraImage ->
                        TimingHelper.endTimer("openImages")
                        val imageData = withTimer("createImageData") {
                            FrameImageData(
                                arFrame,
                                workArrays,
                                scan,
                                depthImage,
                                confidenceImage,
                                cameraImage,
                                earth
                            )
                        }
                        val step = withTimer("calculateStep") {
                            ceil(sqrt((imageData.width * imageData.height / scan.maxPointsPerFrame.toFloat()))).toInt()
                        }
                        val resultPointCloud = withTimer("initResultPointCloud") {
                            initResultPointCloud(step, imageData)
                        }

                        withTimer("processing") {
                            for (y in 0 until imageData.height step step) {
                                for (x in 0 until imageData.width step step) {
                                    imageData.getPixelDataAt(x, y)?.let { pixelData ->
                                        resultPointCloud.pointCount++
                                        resultPointCloud.data[y * imageData.width + x] = pixelData
                                    }
                                }
                            }
                        }
                        totalPointCount += resultPointCloud.pointCount
                        return resultPointCloud
                    }
                }
            }
        } catch (e: NotYetAvailableException) {
            return null
        }
    }

    private fun checkPreconditions(arFrame: Frame, earth: Earth?): Boolean {
        if (arFrame.camera.trackingState != TrackingState.TRACKING) {
            return false
        }
        if (scan.isGeoReferenced) {
            if (earth?.trackingState != TrackingState.TRACKING) {
                return false
            }
            if (earth.cameraGeospatialPose.horizontalAccuracy > scan.horizontalAccuracyThreshold) {
                return false
            }
            if (earth.cameraGeospatialPose.verticalAccuracy > scan.verticalAccuracyThreshold) {
                return false
            }
            if (earth.cameraGeospatialPose.headingAccuracy > scan.headingAccuracyThreshold) {
                return false
            }
        }
        return true
    }

    private fun initResultPointCloud(step: Int, imageData: FrameImageData): FramePointCloud {
        val potentialPointCount = imageData.width / step * imageData.height / step
        return FramePointCloud(Array(potentialPointCount) { null })
    }
}
