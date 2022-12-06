package de.eschoenawa.urbanscanner.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.Earth
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentArScanBinding
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.helper.configureWindowForArFullscreen
import de.eschoenawa.urbanscanner.helper.unconfigureWindowFromArFullscreen
import de.eschoenawa.urbanscanner.model.FramePointCloud
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.arcore.LightEstimationMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArScanFragment : Fragment() {
    private var _binding: FragmentArScanBinding? = null
    private val binding get() = _binding!!

    //TODO configurable / set depending on current scan name
    private var filename = "scan_no_date.xyz"

    //TODO not in fragment
    private var pointCount = 0L

    //TODO in fragment?
    private var recording = false

    companion object {
        private const val FILE_PREFIX = "scan_"
        private const val FILE_SUFFIX = ".xyz"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArScanBinding.inflate(inflater, container, false)
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
        filename = "$FILE_PREFIX${currentTime.format(formatter)}$FILE_SUFFIX"
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
            onArFrame = ::processNewFrame
            instructions.enabled = false
        }

        binding.recordFab.setOnClickListener {
            recording = !recording
            binding.recordFab.setImageResource(if (recording) R.drawable.ic_pause else R.drawable.ic_start_record)
        }
    }

    override fun onResume() {
        super.onResume()
        //requireActivity().configureWindowForArFullscreen(binding.root)
    }

    override fun onPause() {
        super.onPause()
        //requireActivity().unconfigureWindowFromArFullscreen(binding.root)
    }

    private fun processNewFrame(frame: ArFrame) {
        TimingHelper.startTimer("processNewFrame")
        TimingHelper.startTimer("updateUI1")
        val earth = binding.sceneView.arSession?.earth
        updateGeospatialStatusText(earth)
        TimingHelper.endTimer("updateUI1")
        val framePointCloud = FramePointCloud.createPointCloudIfDataIsAvailable(frame.frame, earth, lifecycleScope)
        //TODO set as field since not changing
        val fullFilename = "${requireContext().getExternalFilesDir(null)?.absolutePath}/$filename"
        if (recording) {
            pointCount += framePointCloud?.persistToFile(fullFilename) ?: 0
        }
        TimingHelper.endTimer("processNewFrame")
        if (framePointCloud != null) {
            //TODO use string template
            binding.tvScanStatus.text = "$pointCount\n${TimingHelper.getTimerInfo()}"
        }
        TimingHelper.reset()
    }

    private fun updateGeospatialStatusText(earth: Earth?) {
        binding.geospatialStatusView.update(earth)
    }
}
