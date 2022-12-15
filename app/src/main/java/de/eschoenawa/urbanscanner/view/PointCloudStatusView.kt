package de.eschoenawa.urbanscanner.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.ViewPointCloudStatusBinding
import de.eschoenawa.urbanscanner.model.FramePointCloud

class PointCloudStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
): ConstraintLayout(context, attrs, defStyle) {
    private val binding: ViewPointCloudStatusBinding

    init {
        binding = ViewPointCloudStatusBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun update(result: FramePointCloud.PointCloudResult, pointCount: Long) {
        if (result is FramePointCloud.PointCloudResult.PointCloudGeneratedResult) {
            with(binding) {
                ivDepthStatus.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.okay
                    )
                )
                //TODO use string
                tvDepthStatus.text = "Generated"
            }
        } else {
            with(binding) {
                ivDepthStatus.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.error
                    )
                )
                //TODO use strings
                tvDepthStatus.text = result::class.simpleName
            }
        }
        //TODO use string template
        binding.tvPointCount.text = pointCount.toString()
    }
}
