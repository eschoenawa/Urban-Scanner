package de.eschoenawa.urbanscanner.scanprocess

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentArScanBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.model.FramePointCloud
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.scandetails.ScanDetailFragment
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArScanFragment : Fragment() {
    private var _binding: FragmentArScanBinding? = null
    private val binding get() = _binding!!

    //TODO not in fragment
    private var pointCount = 0L

    //TODO in fragment?
    private var recording = false

    //TODO move to new processing class
    private var lastDepthTimestamp = 0L

    //TODO in fragment?
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //TODO gracefully handle crash
        scan = scanName?.let { scanRepository.getScan(requireContext(), it) }
            ?: throw IllegalArgumentException("No scan name provided!")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun processNewFrame(frame: ArFrame) {
        TimingHelper.startTimer("processNewFrame")
        TimingHelper.startTimer("updateUI1")
        val earth = binding.sceneView.arSession?.earth
        updateGeospatialStatusText(earth)
        TimingHelper.endTimer("updateUI1")
        if (shouldCreateAnchor && earth != null) {
            val cameraPose = earth.cameraGeospatialPose
            geoAnchor = earth.createAnchor(cameraPose.latitude, cameraPose.longitude, cameraPose.altitude, cameraPose.eastUpSouthQuaternion)
            shouldCreateAnchor = false
            binding.scanFab.setImageResource(R.drawable.ic_start_record)
        }
        val pointCloudResult = FramePointCloud.createPointCloudIfDataIsAvailable(frame.frame, earth, lastDepthTimestamp, scan)
        TimingHelper.endTimer("processNewFrame")
        if (pointCloudResult is FramePointCloud.PointCloudResult.PointCloudGeneratedResult) {
            val pointCloud = pointCloudResult.framePointCloud
            lastDepthTimestamp = pointCloud.timestamp

            //TODO move to utm post processing
            /*
            pointCloud.coordinateConverter?.let { coordinateConverter ->
                if (scan.epsgCode.isEmpty()) {
                    scan.epsgCode = coordinateConverter.targetEpsgCode
                    scanRepository.persistScan(requireContext(), scan)
                }
            }
             */

            //TODO set as field since not changing
            val fullFilename = scanRepository.getRawDataFilePath(requireContext(), scan)
            if (recording) {
                pointCount += pointCloud.persistToFile(fullFilename, scan)
            }
            //TODO use string template
            binding.tvScanStatus.text = TimingHelper.getTimerInfo()
            TimingHelper.reset()
        }
        binding.pointCloudStatusView.update(pointCloudResult, pointCount)
    }

    private fun updateGeospatialStatusText(earth: Earth?) {
        binding.geospatialStatusView.update(earth)
    }

    private fun initGeospatialStatusView() {
        with(binding.geospatialStatusView) {
            isVisible = scan.isGeoReferenced
            //TODO add a yellow state?
            setHorizontalAccuracyThresholds(DoubleArray(2) { scan.horizontalAccuracyThreshold.toDouble() })
            setVerticalAccuracyThresholds(DoubleArray(2) { scan.verticalAccuracyThreshold.toDouble() })
            setHeadingAccuracyThresholds(DoubleArray(2) { scan.headingAccuracyThreshold.toDouble() })
        }
    }
}
