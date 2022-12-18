package de.eschoenawa.urbanscanner.scanlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import de.eschoenawa.urbanscanner.databinding.ItemScanBinding
import de.eschoenawa.urbanscanner.model.Scan

class ScanListAdapter(
    private val scanClickCallback: (Scan) -> Unit,
    private val scanLongClickCallback: (Scan) -> Unit
): ListAdapter<Scan, ScanListAdapter.ScanViewHolder>(ScanDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        return ScanViewHolder(
            ItemScanBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        getItem(position)?.let { scan -> holder.bind(scan) }
    }

    inner class ScanViewHolder(private val binding: ItemScanBinding): ViewHolder(binding.root) {
        fun bind(scan: Scan) {
            with(binding) {
                tvScanName.text = scan.name
                tvScanName.setOnClickListener {
                    scanClickCallback(scan)
                }
                tvScanName.setOnLongClickListener {
                    scanLongClickCallback(scan)
                    true
                }
            }
        }
    }

    class ScanDiffUtil: ItemCallback<Scan>() {
        override fun areItemsTheSame(oldItem: Scan, newItem: Scan): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Scan, newItem: Scan): Boolean {
            return oldItem == newItem
        }
    }
}
