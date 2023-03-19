package de.eschoenawa.urbanscanner.postprocessing

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import de.eschoenawa.urbanscanner.databinding.FragmentPostProcessingBinding
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.ui.BaseFragment


class PostProcessingFragment : BaseFragment<FragmentPostProcessingBinding>() {

    private lateinit var scanName: String
    private lateinit var processName: String
    private lateinit var scan: Scan
    private lateinit var processor: PostProcessor

    //TODO viewmodel?
    private val configMap = emptyMap<PostProcessingConfig, String>().toMutableMap()
    private val scanRepository = DependencyProvider.getScanRepository()

    companion object {
        private const val ARG_SCAN_NAME = "scanName"
        private const val ARG_PROCESS_NAME = "processName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            scanName = it.getString(ARG_SCAN_NAME).orEmpty()
            processName = it.getString(ARG_PROCESS_NAME).orEmpty()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //TODO show something when no config available?
        scan = scanRepository.getScan(requireContext(), scanName)
        processor = PostProcessorRegistry.getProcessor(processName)
            ?: throw IllegalArgumentException("Unknown process '$processName'!")
        val config = processor.getConfig()
        config.forEach {
            configMap[it] = ""
        }
        val adapter = ConfigListAdapter(::onConfigValueChanged)
        adapter.submitList(config)
        binding.rvConfigList.adapter = adapter
        binding.btnProcess.setOnClickListener { onStartProcess() }
        checkConfigValues()
    }

    private fun onStartProcess() {
        with(binding) {
            //TODO abort button?
            btnProcess.isEnabled = false
            //TODO display some other info?
            rvConfigList.isVisible = false
        }
        processor.configure(configMap)
        lifecycleScope.launchWhenResumed {
            processor.process(requireContext(), scan, scanRepository).collect { progress ->
                with(binding) {
                    progressBar.progress = progress.progress
                    tvProgress.text = progress.infoText
                    if (progress.progress == 100) {
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun onConfigValueChanged(config: PostProcessingConfig, newValue: String) {
        configMap[config] = newValue
        checkConfigValues()
    }

    private fun checkConfigValues() {
        var allValuesValid = true
        configMap.entries.forEach { entry ->
            if (!entry.key.validateValue(entry.value) || (entry.key.required && entry.value.isBlank())) {
                allValuesValid = false
            }
        }
        binding.btnProcess.isEnabled = allValuesValid
    }
}
