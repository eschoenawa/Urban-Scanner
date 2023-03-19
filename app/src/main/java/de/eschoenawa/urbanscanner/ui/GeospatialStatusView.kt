package de.eschoenawa.urbanscanner.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.databinding.ViewGeospatialStatusBinding

class GeospatialStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {
    private val binding: ViewGeospatialStatusBinding

    private var horizontalAccuracyThresholds = doubleArrayOf(1.0, 3.0)
    private var verticalAccuracyThresholds = doubleArrayOf(1.0, 3.0)
    private var headingAccuracyThresholds = doubleArrayOf(2.0, 3.0)

    init {
        binding = ViewGeospatialStatusBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setHorizontalAccuracyThresholds(thresholds: DoubleArray) {
        if (thresholds.size != 2) {
            throw IllegalArgumentException("Need 2 threshold values!")
        }
        this.horizontalAccuracyThresholds = thresholds
    }

    fun setVerticalAccuracyThresholds(thresholds: DoubleArray) {
        if (thresholds.size != 2) {
            throw IllegalArgumentException("Need 2 threshold values!")
        }
        this.verticalAccuracyThresholds = thresholds
    }

    fun setHeadingAccuracyThresholds(thresholds: DoubleArray) {
        if (thresholds.size != 2) {
            throw IllegalArgumentException("Need 2 threshold values!")
        }
        this.headingAccuracyThresholds = thresholds
    }

    fun update(nullableEarth: Earth?) {
        nullableEarth?.let { earth ->
            val trackingStateColor = when (earth.trackingState) {
                TrackingState.TRACKING -> R.color.okay
                TrackingState.PAUSED -> R.color.warn
                TrackingState.STOPPED -> R.color.error
            }
            val horizontalColor: Int
            val verticalColor: Int
            val headingColor: Int
            with(earth.cameraGeospatialPose) {
                horizontalColor =
                    getColorFromThresholds(horizontalAccuracy, horizontalAccuracyThresholds)
                verticalColor = getColorFromThresholds(verticalAccuracy, verticalAccuracyThresholds)
                headingColor = getColorFromThresholds(headingAccuracy, headingAccuracyThresholds)
            }
            with(binding) {
                ivEarthTrackingState.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        trackingStateColor
                    )
                )
                tvEarthTrackingState.text = when (earth.trackingState) {
                    TrackingState.TRACKING -> earth.trackingState.toString()
                    else -> context.getString(
                        R.string.geospatial_unavailable_status,
                        earth.earthState,
                        earth.trackingState
                    )
                }
                ivHorizontal.setColorFilter(ContextCompat.getColor(context, horizontalColor))
                ivVertical.setColorFilter(ContextCompat.getColor(context, verticalColor))
                ivHeading.setColorFilter(ContextCompat.getColor(context, headingColor))
                setTexts(earth)
            }
        } ?: run {
            with(binding) {
                ivEarthTrackingState.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.error
                    )
                )
                tvEarthTrackingState.text = context.getString(R.string.geospatial_earth_null_status)
            }
        }
    }

    @ColorRes
    private fun getColorFromThresholds(value: Double, thresholds: DoubleArray): Int {
        return when {
            value <= thresholds[0] -> R.color.okay
            value <= thresholds[1] -> R.color.warn
            else -> R.color.error
        }
    }

    private fun setTexts(earth: Earth) {
        with(binding) {
            tvHorizontal.text = context.getString(
                R.string.meter_value,
                earth.cameraGeospatialPose.horizontalAccuracy
            )
            tvVertical.text =
                context.getString(R.string.meter_value, earth.cameraGeospatialPose.verticalAccuracy)
            tvHeading.text =
                context.getString(R.string.degree_value, earth.cameraGeospatialPose.headingAccuracy)
            tvLocation.text = context.getString(
                R.string.geospatial_pose,
                earth.cameraGeospatialPose.latitude,
                earth.cameraGeospatialPose.longitude,
                earth.cameraGeospatialPose.altitude,
                earth.cameraGeospatialPose.heading
            )
        }
    }
}
