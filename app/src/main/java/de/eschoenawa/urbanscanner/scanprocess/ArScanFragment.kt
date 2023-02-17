package de.eschoenawa.urbanscanner.scanprocess

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Earth
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentArScanBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.helper.toGeoPoseWithoutHeading
import de.eschoenawa.urbanscanner.model.FramePointCloud
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.ui.BaseFragment
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode

class ArScanFragment : BaseFragment<FragmentArScanBinding>() {

    //TODO in fragment?
    private var recording = false

    private lateinit var frameProcessor: FrameProcessor

    private var shouldCreateAnchor = false
    private var geoAnchor: Anchor? = null

    private var scanName: String? = null
    private val scanRepository = DependencyProvider.getScanRepository()
    private lateinit var scan: Scan

    companion object {
        private const val ARG_SCAN_NAME = "scanName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            scanName = it.getString(ARG_SCAN_NAME)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scan = scanName?.let { scanRepository.getScan(requireContext(), it) }
            ?: throw IllegalArgumentException("No scan name provided!")
        frameProcessor = FrameProcessor(scan)
        initGeospatialStatusView()
        with(binding.sceneView) {
            lightEstimationMode = LightEstimationMode.AMBIENT_INTENSITY
            geospatialEnabled = true
            depthMode = Config.DepthMode.RAW_DEPTH_ONLY
            depthEnabled = true
            focusMode = Config.FocusMode.AUTO
            onArFrame = ::processNewFrame
            instructions.enabled = false
            planeRenderer.isEnabled = false
        }

        binding.scanFab.setOnClickListener {
            if (geoAnchor == null && !shouldCreateAnchor) {
                shouldCreateAnchor = true
            } else {
                recording = !recording
                binding.scanFab.setImageResource(if (recording) R.drawable.ic_pause else R.drawable.ic_start_record)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        scan.currentScanNumber++
        scanRepository.persistScan(requireContext(), scan)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanRepository.persistScan(requireContext(), scan)
    }

    private fun processNewFrame(frame: ArFrame) {
        val pointCloud = TimingHelper.withTimer("processNewFrame") {
            val earth = binding.sceneView.arSession?.earth
            TimingHelper.withTimer("updateGeoUI") {
                updateGeospatialStatusText(earth)
            }
            createAnchorIfApplicable(earth, frame.camera)

            geoAnchor?.let { anchor ->
                frameProcessor.processFrame(frame.frame, earth, anchor)
            }
        }

        persistPointCloudIfRecording(pointCloud)
        updateUIAfterProcessing(pointCloud)
    }

    private fun updateGeospatialStatusText(earth: Earth?) {
        //TODO verify visibility works for non-continuous (invisible after anchor place)
        if (scan.isGeoReferenced && scan.continuousGeoReference || (scan.isGeoReferenced && geoAnchor == null)) {
            binding.geospatialStatusView.update(earth)
        } else {
            binding.geospatialStatusView.isVisible = false
        }
    }

    private fun initGeospatialStatusView() {
        with(binding.geospatialStatusView) {
            isVisible = scan.isGeoReferenced
            setHorizontalAccuracyThresholds(DoubleArray(2) { scan.horizontalAccuracyThreshold.toDouble() })
            setVerticalAccuracyThresholds(DoubleArray(2) { scan.verticalAccuracyThreshold.toDouble() })
            setHeadingAccuracyThresholds(DoubleArray(2) { scan.headingAccuracyThreshold.toDouble() })
        }
    }

    private fun createAnchorIfApplicable(earth: Earth?, camera: Camera) {
        if (shouldCreateAnchor && earth != null) {
            if (!scan.isGeoReferenced) {
                geoAnchor = binding.sceneView.arSession!!.createAnchor(camera.pose)
            } else if (!scan.checkEarthTrackingComplianceWithThresholds(earth)) {
                //TODO string ref
                Toast.makeText(requireContext(), "Not accurate enough!", Toast.LENGTH_SHORT).show()
                shouldCreateAnchor = false
                return
            } else {
                val cameraPose = earth.cameraGeospatialPose
                geoAnchor = if (scan.continuousGeoReference) {
                    earth.createAnchor(
                        cameraPose.latitude,
                        cameraPose.longitude,
                        cameraPose.altitude,
                        0f,
                        0f,
                        0f,
                        1f
                    )
                } else {
                    frameProcessor.anchorGeoPose =
                        earth.cameraGeospatialPose.toGeoPoseWithoutHeading()
                    frameProcessor.accuracies = floatArrayOf(
                        earth.cameraGeospatialPose.horizontalAccuracy.toFloat(),
                        earth.cameraGeospatialPose.verticalAccuracy.toFloat(),
                        earth.cameraGeospatialPose.headingAccuracy.toFloat()
                    )
                    // Create normal anchor (doesn't update position when camera geopose updates)
                    binding.sceneView.arSession!!.createAnchor(
                        earth.getPose(
                            cameraPose.latitude,
                            cameraPose.longitude,
                            cameraPose.altitude,
                            0f,
                            0f,
                            0f,
                            1f
                        )
                    )
                }
            }
            shouldCreateAnchor = false
            binding.scanFab.setImageResource(R.drawable.ic_start_record)
        }
    }

    private fun persistPointCloudIfRecording(nullablePointCloud: FramePointCloud?) {
        nullablePointCloud?.let { pointCloud ->
            if (recording) {
                scanRepository.persistRawData(requireContext(), scan, pointCloud)
                scan.pointCount += pointCloud.pointCount
                scan.frameCount++
            }
        }
    }

    private fun updateUIAfterProcessing(pointCloud: FramePointCloud?) {
        if (pointCloud != null) {
            //TODO use string template
            binding.tvScanStatus.text = TimingHelper.getTimerInfo()
        }
        TimingHelper.reset()
        binding.pointCloudStatusView.update(pointCloud != null, scan.pointCount)
    }
}
