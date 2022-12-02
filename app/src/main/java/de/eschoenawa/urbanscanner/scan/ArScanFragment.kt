package de.eschoenawa.urbanscanner.scan

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.filament.Texture
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.exceptions.NotYetAvailableException
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentArScanBinding
import de.eschoenawa.urbanscanner.helper.configureWindowForArFullscreen
import de.eschoenawa.urbanscanner.helper.unconfigureWindowFromArFullscreen
import de.eschoenawa.urbanscanner.model.FramePointCloud
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode

class ArScanFragment : Fragment() {
    private var _binding: FragmentArScanBinding? = null
    private val binding get() = _binding!!

    companion object {
        //TODO configurable / set depending on current scan name
        private const val FILENAME = "scan.xyz"
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
        with(binding.sceneView) {
            lightEstimationMode = LightEstimationMode.AMBIENT_INTENSITY
            geospatialEnabled = true
            depthMode = Config.DepthMode.RAW_DEPTH_ONLY
            depthEnabled = true
            focusMode = Config.FocusMode.AUTO
            //TODO configure session needed?
            configureSession { session, config ->
                // Enable Geospatial Mode & Depth API.
                config.apply {
                    geospatialMode = Config.GeospatialMode.ENABLED
                    planeRenderer.isEnabled = false
                    depthMode = Config.DepthMode.RAW_DEPTH_ONLY
                    depthEnabled = true
                }
            }
            onArFrame = ::processNewFrame
            instructions.enabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().configureWindowForArFullscreen(binding.root)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().unconfigureWindowFromArFullscreen(binding.root)
    }

    private fun processNewFrame(frame: ArFrame) {
        val earth = binding.sceneView.arSession?.earth
        updateStatusText(earth)
        val framePointCloud = FramePointCloud.createPointCloudIfDataIsAvailable(frame.frame, earth)
        //TODO set as field since not changing
        val filename = "${requireContext().getExternalFilesDir(null)?.absolutePath}/$FILENAME"
        framePointCloud?.persistToFile(filename)
    }

    private fun updateStatusText(earth: Earth?) {
        val cameraGeospatialPose = earth?.cameraGeospatialPose
        val poseText: String = cameraGeospatialPose?.let {
            resources.getString(
                R.string.geospatial_pose,
                it.latitude,
                it.longitude,
                it.horizontalAccuracy,
                it.altitude,
                it.verticalAccuracy,
                it.heading,
                it.headingAccuracy
            )
        } ?: resources.getString(R.string.vps_unavailable)
        binding.tvStatus.text = resources.getString(
            R.string.earth_state,
            earth?.earthState.toString(),
            earth?.trackingState.toString(),
            poseText
        )
    }
}
