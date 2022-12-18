package de.eschoenawa.urbanscanner.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorRes
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

    private var lastPointCloudSystemTimestamp = 0L

    init {
        binding = ViewPointCloudStatusBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun update(result: FramePointCloud.PointCloudResult, pointCount: Long) {
        if (result is FramePointCloud.PointCloudResult.PointCloudGeneratedResult) {
            lastPointCloudSystemTimestamp = System.currentTimeMillis()

            //TODO use string template
            binding.tvPointCount.text = pointCount.toString()
        }
        val delta = System.currentTimeMillis() - lastPointCloudSystemTimestamp
        with(binding) {
            ivDepthStatus.setColorFilter(
                ContextCompat.getColor(
                    context,
                    getColorFromDelta(delta)
                )
            )
            tvDepthStatus.text = if (delta <= 1_000L) {
                context.getString(R.string.depth_status_ok)
            } else if (delta <= 2_000L) {
                context.getString(R.string.depth_status_warn)
            } else {
                //TODO don't use classnames
                context.getString(
                    R.string.depth_status_with_error,
                    result::class.simpleName
                )
            }
        }
    }

    @ColorRes
    private fun getColorFromDelta(delta: Long): Int {
        return when {
            delta <= 1_000L -> R.color.okay
            delta <= 2_000L -> R.color.warn
            else -> R.color.error
        }
    }
}
