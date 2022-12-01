package de.eschoenawa.urbanscanner.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.ar.core.Config
import de.eschoenawa.urbanscanner.databinding.FragmentArScanBinding
import io.github.sceneview.ar.arcore.ArFrame

class ArScanFragment : Fragment() {
    private var _binding: FragmentArScanBinding? = null
    private val binding get() = _binding!!

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
            configureSession { session, config ->
                // Enable Geospatial Mode.
                config.apply {
                    geospatialMode = Config.GeospatialMode.ENABLED
                    planeRenderer.isEnabled = false
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                }
            }
            onArFrame = ::processNewFrame
            instructions.enabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        configureWindow()
    }

    override fun onPause() {
        super.onPause()
        unconfigureWindow()
    }

    private fun processNewFrame(frame: ArFrame) {

    }

    private fun configureWindow() {
        with(requireActivity().window) {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val windowInsetsController =
                WindowCompat.getInsetsController(this, binding.root)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

    }

    private fun unconfigureWindow() {
        with(requireActivity().window) {
            clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val windowInsetsController =
                WindowCompat.getInsetsController(this, binding.root)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }

    }
}