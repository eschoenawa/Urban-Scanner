package de.eschoenawa.urbanscanner.postprocessing

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.ItemConfigBinding

class ConfigListAdapter(
    private val valueChangeCallback: (PostProcessingConfig, String) -> Unit
) : ListAdapter<PostProcessingConfig, ConfigListAdapter.ConfigViewHolder>(ConfigDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        return ConfigViewHolder(
            ItemConfigBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        getItem(position)?.let { config -> holder.bind(config) }
    }

    inner class ConfigViewHolder(private val binding: ItemConfigBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(config: PostProcessingConfig) {
            with(binding) {
                tvName.text = root.context.getString(config.name)
                editValue.hint =
                    if (config.required) {
                        root.context.getString(R.string.required)
                    } else {
                        root.context.getString(R.string.optional)
                    }
                editValue.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                    override fun afterTextChanged(text: Editable) {
                        val newValue = text.toString()
                        ivWarn.isInvisible = config.validateValue(newValue)
                        valueChangeCallback(config, newValue)
                    }
                })
                ivWarn.isInvisible =
                    config.validateValue(editValue.text.toString()) || (config.required && editValue.text.isBlank())
            }
        }
    }

    class ConfigDiffUtil : DiffUtil.ItemCallback<PostProcessingConfig>() {
        override fun areItemsTheSame(
            oldItem: PostProcessingConfig,
            newItem: PostProcessingConfig
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: PostProcessingConfig,
            newItem: PostProcessingConfig
        ): Boolean {
            return oldItem.name == newItem.name
        }

    }
}
