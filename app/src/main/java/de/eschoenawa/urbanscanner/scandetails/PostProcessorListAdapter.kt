package de.eschoenawa.urbanscanner.scandetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.eschoenawa.urbanscanner.databinding.ItemPostprocessorBinding
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.postprocessing.PostProcessorInfo

class PostProcessorListAdapter(
    private val scan: Scan,
    private val postProcessorClickedCallback: (PostProcessorInfo) -> Unit
) : ListAdapter<PostProcessorInfo, PostProcessorListAdapter.PostProcessorInfoViewHolder>(
    PostProcessorDiffUtil()
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PostProcessorListAdapter.PostProcessorInfoViewHolder {
        return PostProcessorInfoViewHolder(
            ItemPostprocessorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(
        holder: PostProcessorListAdapter.PostProcessorInfoViewHolder,
        position: Int
    ) {
        getItem(position)?.let { info -> holder.bind(info) }
    }

    inner class PostProcessorInfoViewHolder(private val binding: ItemPostprocessorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(info: PostProcessorInfo) {
            with(binding.btnProcessor) {
                text = context.getString(info.name)
                isEnabled = info.isApplicable(scan)
                setOnClickListener { postProcessorClickedCallback(info) }
            }
        }
    }

    class PostProcessorDiffUtil : DiffUtil.ItemCallback<PostProcessorInfo>() {
        override fun areItemsTheSame(
            oldItem: PostProcessorInfo,
            newItem: PostProcessorInfo
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: PostProcessorInfo,
            newItem: PostProcessorInfo
        ): Boolean {
            return oldItem == newItem
        }

    }
}
