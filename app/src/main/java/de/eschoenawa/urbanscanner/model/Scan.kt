package de.eschoenawa.urbanscanner.model

import com.google.gson.Gson

data class Scan(
    val name: String,
    val isGeoReferenced: Boolean,
    val horizontalAccuracyThreshold: Float,
    val verticalAccuracyThreshold: Float,
    val headingAccuracyThreshold: Float,
    val confidenceCutoff: Float,
    val maxPointsPerFrame: Int,
    val depthLimit: Float,
    var epsgCode: String = "",
    var pointCount: Long = 0,
    var frameCount: Int = 0
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
}
