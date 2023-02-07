package de.eschoenawa.urbanscanner.scandetails

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentScanDetailBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.postprocessing.PostProcessorInfo
import de.eschoenawa.urbanscanner.postprocessing.PostProcessorRegistry
import de.eschoenawa.urbanscanner.ui.BaseFragment

class ScanDetailFragment : BaseFragment<FragmentScanDetailBinding>() {
    private lateinit var scanName: String
    private val scanRepository = DependencyProvider.getScanRepository()
    private lateinit var scan: Scan

    companion object {
        private const val ARG_SCAN_NAME = "scanName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            scanName = it.getString(ARG_SCAN_NAME).orEmpty()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scan = scanRepository.getScan(requireContext(), scanName)
        initButtons()
        val rawDataExists = scanRepository.doesScanHaveRawData(requireContext(), scan)
        if (!rawDataExists && (scan.pointCount > 0 || scan.frameCount > 0)) {
            scan.pointCount = 0
            scan.frameCount = 0
            scan.epsgCode = ""
            scanRepository.persistScan(requireContext(), scan)
        }
        setTexts(rawDataExists)
        setupPostProcessorButtons()
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

    private fun setTexts(rawDataExists: Boolean) {
        with(binding) {
            val georeferenceString = getStringForBoolean(scan.isGeoReferenced)
            val georeferenceDetailsString = if (scan.isGeoReferenced) {
                getString(
                    R.string.scan_georeference_details,
                    getStringForBoolean(scan.continuousGeoReference),
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
                georeferenceString,
                georeferenceDetailsString
            )

            tvScanDataDetails.text = getString(
                R.string.scan_data_details,
                getStringForBoolean(rawDataExists),
                scan.currentScanNumber,
                scan.frameCount
            )
            tvScanRawPointCount.text = getString(R.string.scan_raw_point_count, scan.pointCount)
        }
    }

    private fun setupPostProcessorButtons() {
        val adapter = PostProcessorListAdapter(scan, ::onPostProcessorClicked)
        adapter.submitList(PostProcessorRegistry.postProcessorInfos)
        binding.rvPostprocessors.adapter = adapter
    }

    private fun onPostProcessorClicked(postProcessorInfo: PostProcessorInfo) {
        findNavController().navigate(ScanDetailFragmentDirections.actionScanDetailFragmentToPostProcessingFragment(scanName, postProcessorInfo.identifier))
    }

    private fun getStringForBoolean(value: Boolean): String {
        return getString(if (value) R.string.yes else R.string.no)
    }
}
