package de.eschoenawa.urbanscanner.model

import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.google.gson.Gson

data class Scan(
    val name: String,
    val isGeoReferenced: Boolean,
    val continuousGeoReference: Boolean,
    val horizontalAccuracyThreshold: Float,
    val verticalAccuracyThreshold: Float,
    val headingAccuracyThreshold: Float,
    val confidenceCutoff: Float,
    val maxPointsPerFrame: Int,
    val depthLimit: Float,
    var epsgCode: String = "",
    var pointCount: Long = 0,
    var frameCount: Int = 0,
    var currentScanNumber: Int = 0
) {
    companion object {

        fun fromJson(json: String): Scan {
            val gson = Gson()
            return gson.fromJson(json, Scan::class.java)
        }
    }

    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(this)
    }

    fun checkEarthTrackingComplianceWithThresholds(earth: Earth?): Boolean {
        if (earth?.trackingState != TrackingState.TRACKING) {
            return false
        }
        if (earth.cameraGeospatialPose.horizontalAccuracy > horizontalAccuracyThreshold) {
            return false
        }
        if (earth.cameraGeospatialPose.verticalAccuracy > verticalAccuracyThreshold) {
            return false
        }
        if (earth.cameraGeospatialPose.headingAccuracy > headingAccuracyThreshold) {
            return false
        }
        return true
    }
}
