package de.eschoenawa.urbanscanner.scanlist

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.FragmentScanListBinding
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.ui.BaseFragment

class ScanListFragment : BaseFragment<FragmentScanListBinding>() {

    companion object {
        private const val TAG = "ScanListFragment"
    }

    private lateinit var adapter: ScanListAdapter

    private val viewModel by viewModels<ScanListViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListeners()
        initAdapter()
        registerObservers()
        viewModel.loadScans(requireContext())
    }

    private fun setClickListeners() {
        binding.fabNewScan.setOnClickListener {
            findNavController().navigate(ScanListFragmentDirections.actionScanListFragmentToCreateScanFragment())
        }
    }

    private fun initAdapter() {
        adapter = ScanListAdapter(::onScanClicked, ::onScanLongClicked)
        binding.rvScanList.adapter = adapter
    }

    private fun onScanClicked(scan: Scan) {
        Log.d(TAG, "Clicked scan ${scan.name}")
        findNavController().navigate(
            ScanListFragmentDirections.actionScanListFragmentToScanDetailFragment(
                scan.name
            )
        )
    }

    private fun onScanLongClicked(scan: Scan) {
        Log.d(TAG, "Long-clicked scan ${scan.name}")
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_title, scan.name))
            .setMessage(getString(R.string.delete_message, scan.name))
            .setPositiveButton(getString(R.string.delete_action)) { _, _ ->
                viewModel.deleteScan(requireContext(), scan)
                viewModel.loadScans(requireContext())
            }
            .setNegativeButton(getString(R.string.delete_cancel), null)
            .show()
    }

    private fun registerObservers() {
        viewModel.scans.observe(viewLifecycleOwner) { newList ->
            with(binding) {
                tvEmptyList.isVisible = newList.isEmpty()
                rvScanList.isVisible = newList.isNotEmpty()
            }
            adapter.submitList(newList)
        }
    }
}
