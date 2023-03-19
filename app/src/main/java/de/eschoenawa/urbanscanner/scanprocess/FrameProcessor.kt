package de.eschoenawa.urbanscanner.scanprocess

import com.google.ar.core.Anchor
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.helper.TimingHelper.withTimer
import de.eschoenawa.urbanscanner.helper.getGeoPoseWithHeadingOfLocalPose
import de.eschoenawa.urbanscanner.model.FrameMetaData
import de.eschoenawa.urbanscanner.model.FramePointCloud
import de.eschoenawa.urbanscanner.model.GeoPose
import de.eschoenawa.urbanscanner.model.Scan
import io.github.sceneview.ar.arcore.position
import org.ddogleg.struct.DogArray_I8
import pabeles.concurrency.GrowArray
import kotlin.math.ceil
import kotlin.math.sqrt

class FrameProcessor(private val scan: Scan) {

    private val workArrays = GrowArray(::DogArray_I8)
    private var lastDepthTimestamp = 0L

    var accuracies: FloatArray? = null
    var anchorGeoPose: GeoPose? = null

    fun processFrame(arFrame: Frame, earth: Earth?, anchor: Anchor): FramePointCloud? {
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
                        val cameraLocalPose = anchor.pose.inverse().compose(arFrame.camera.pose)
                        val frameMetaData = withTimer("generateMetaData") {
                            if (scan.continuousGeoReference) {
                                FrameMetaData(scan, cameraLocalPose.position, earth)
                            } else {
                                val cameraGeoPose =
                                    anchorGeoPose!!.getGeoPoseWithHeadingOfLocalPose(
                                        anchor.pose,
                                        cameraLocalPose
                                    )
                                FrameMetaData(
                                    scan,
                                    cameraLocalPose.position,
                                    cameraGeoPose,
                                    accuracies!![0],
                                    accuracies!![1],
                                    accuracies!![2]
                                )
                            }
                        }
                        val imageData = withTimer("createImageData") {
                            FrameImageData(
                                arFrame,
                                workArrays,
                                scan,
                                depthImage,
                                confidenceImage,
                                cameraImage,
                                cameraLocalPose,
                                earth
                            )
                        }
                        val step = withTimer("calculateStep") {
                            ceil(sqrt((imageData.width * imageData.height / scan.maxPointsPerFrame.toFloat()))).toInt()
                        }
                        val resultPointCloud = withTimer("initResultPointCloud") {
                            initResultPointCloud(step, frameMetaData, imageData)
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
        // Only check accuracy if continuously georeferencing
        if (scan.isGeoReferenced && scan.continuousGeoReference) {
            return scan.checkEarthTrackingComplianceWithThresholds(earth)
        }
        return true
    }

    private fun initResultPointCloud(
        step: Int,
        frameMetaData: FrameMetaData,
        imageData: FrameImageData
    ): FramePointCloud {
        val potentialPointCount = imageData.width / step * imageData.height / step
        return FramePointCloud(frameMetaData, Array(potentialPointCount) { null })
    }
}
