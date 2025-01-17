package de.eschoenawa.urbanscanner.createscan

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentCreateScanBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.ui.BaseFragment

class CreateScanFragment : BaseFragment<FragmentCreateScanBinding>() {

    companion object {
        private const val DEFAULT_GEOREFERENCE = true
        private const val DEFAULT_CONTINUOUS_GEOREFERENCE = true
        private const val DEFAULT_HORIZONTAL = 2f
        private const val DEFAULT_VERTICAL = 2f
        private const val DEFAULT_HEADING = 5f
        private const val DEFAULT_CONFIDENCE_CUTOFF = 0.5f
        private const val DEFAULT_MAX_POINTS_PER_FRAME = 20000
        private const val DEFAULT_DEPTH_LIMIT = 25
    }

    private val scanRepository = DependencyProvider.getScanRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initClickListener()
        fillDefaultValues()
        initGeoreferenceCheckbox()
    }

    private fun initClickListener() {
        binding.btnCreateScan.setOnClickListener {
            if (checkValues()) {
                val scan = createScan()
                scanRepository.persistScan(requireContext(), scan)
                findNavController().navigate(
                    CreateScanFragmentDirections.actionCreateScanFragmentToScanDetailFragment(
                        scan.name
                    )
                )
            }
        }
    }

    private fun checkValues(): Boolean {
        with(binding) {
            if (editName.text.isBlank()) {
                tvError.text = getString(R.string.error_no_name)
                return false
            }
            if (checkboxGeoreference.isChecked) {
                val horizontal = editHorizontal.text.toString().toFloatOrNull()
                val vertical = editVertical.text.toString().toFloatOrNull()
                val heading = editVertical.text.toString().toFloatOrNull()
                if (horizontal == null || vertical == null || heading == null) {
                    tvError.text = getString(R.string.error_no_accuracy)
                    return false
                } else if (horizontal <= 0 || vertical <= 0 || heading <= 0) {
                    tvError.text = getString(R.string.error_negative_accuracy)
                    return false
                }
            } else {
                fillDefaultValuesForGeoreferenceFields()
            }
            val confidence = editConfidenceCutoff.text.toString().toFloatOrNull()
            val pointsPerFrame = editMaxPointsPerFrame.text.toString().toIntOrNull()
            val depthLimit = editDepthLimit.text.toString().toFloatOrNull()
            if (confidence == null || confidence < 0 || confidence > 1) {
                tvError.text = getString(R.string.error_invalid_confidence_cutoff)
                return false
            }
            if (pointsPerFrame == null || pointsPerFrame < 0) {
                tvError.text = getString(R.string.error_invalid_points_per_frame)
                return false
            }
            if (depthLimit == null || depthLimit < 0 || depthLimit > 65) {
                tvError.text = getString(R.string.error_invalid_depth_limit)
                return false
            }
            return true
        }
    }

    private fun createScan(): Scan {
        with(binding) {
            return Scan(
                name = editName.text.toString().trim(),
                isGeoReferenced = checkboxGeoreference.isChecked,
                continuousGeoReference = checkboxContinuousGeoreference.isChecked,
                horizontalAccuracyThreshold = editHorizontal.text.toString().toFloat(),
                verticalAccuracyThreshold = editVertical.text.toString().toFloat(),
                headingAccuracyThreshold = editHeading.text.toString().toFloat(),
                confidenceCutoff = editConfidenceCutoff.text.toString().toFloat(),
                maxPointsPerFrame = editMaxPointsPerFrame.text.toString().toInt(),
                depthLimit = editDepthLimit.text.toString().toFloat()
            )
        }
    }

    private fun fillDefaultValues() {
        with(binding) {
            checkboxGeoreference.isChecked = DEFAULT_GEOREFERENCE
            checkboxContinuousGeoreference.isChecked = DEFAULT_CONTINUOUS_GEOREFERENCE
            editConfidenceCutoff.setText(DEFAULT_CONFIDENCE_CUTOFF.toString())
            editMaxPointsPerFrame.setText(DEFAULT_MAX_POINTS_PER_FRAME.toString())
            editDepthLimit.setText(DEFAULT_DEPTH_LIMIT.toString())
        }
        fillDefaultValuesForGeoreferenceFields()
    }

    private fun fillDefaultValuesForGeoreferenceFields() {
        with(binding) {
            editHorizontal.setText(DEFAULT_HORIZONTAL.toString())
            editVertical.setText(DEFAULT_VERTICAL.toString())
            editHeading.setText(DEFAULT_HEADING.toString())
        }
    }

    private fun initGeoreferenceCheckbox() {
        with(binding) {
            checkboxGeoreference.setOnCheckedChangeListener { _, newValue ->
                setEnabledForGeoreferencingFields(newValue)
            }
            setEnabledForGeoreferencingFields(checkboxGeoreference.isChecked)
        }
    }

    private fun setEnabledForGeoreferencingFields(enabled: Boolean) {
        with(binding) {
            checkboxContinuousGeoreference.isEnabled = enabled
            editHorizontal.isEnabled = enabled
            editVertical.isEnabled = enabled
            editHeading.isEnabled = enabled
        }
    }
}
