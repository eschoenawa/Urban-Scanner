package de.eschoenawa.urbanscanner.scandetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentScanDetailBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.model.Scan

class ScanDetailFragment : Fragment() {
    private var scanName: String? = null
    private val scanRepository = DependencyProvider.getScanRepository()
    private lateinit var scan: Scan

    private var _binding: FragmentScanDetailBinding? = null
    private val binding get() = _binding!!

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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scan = scanName?.let { scanRepository.getScan(requireContext(), it) }
            ?: throw IllegalArgumentException("No scan name provided!")
        initButtons()
        setTexts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initButtons() {
        with(binding.btnScan) {
            isEnabled = scan.isGeoReferenced || !scanRepository.doesScanHaveRawData(requireContext(), scan)
            setOnClickListener {
                findNavController().navigate(
                    ScanDetailFragmentDirections.actionScanDetailFragmentToArScanFragment(
                        scan.name
                    )
                )
            }
        }
    }

    private fun setTexts() {
        with(binding) {
            val storeVpsString = getStringForBoolean(scan.storeVpsPoints)
            val georeferenceString = getStringForBoolean(scan.isGeoReferenced)
            val georeferenceDetailsString = if (scan.isGeoReferenced) {
                getString(
                    R.string.scan_georeference_details,
                    scan.horizontalAccuracyThreshold,
                    scan.verticalAccuracyThreshold,
                    scan.headingAccuracyThreshold,
                    scan.epsgCode.ifEmpty { getString(R.string.auto_epsg) }
                )
            } else ""
            tvScanDetails.text = getString(
                R.string.scan_details,
                scan.name,
                scan.confidenceCutoff,
                scan.maxPointsPerFrame,
                scan.depthLimit,
                storeVpsString,
                georeferenceString,
                georeferenceDetailsString
            )
            tvScanDataDetails.text = getString(
                R.string.scan_data_details,
                getStringForBoolean(scanRepository.doesScanHaveRawData(requireContext(), scan))
            )
        }
    }

    private fun getStringForBoolean(value: Boolean): String {
        return getString(if (value) R.string.yes else R.string.no)
    }
}
