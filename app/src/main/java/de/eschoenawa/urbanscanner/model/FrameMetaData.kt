package de.eschoenawa.urbanscanner.model

import com.google.ar.core.Earth
import de.eschoenawa.urbanscanner.helper.toGeoPose
import dev.romainguy.kotlin.math.Float3

data class FrameMetaData(
    val id: Int,
    val cameraPosition: Float3,
    val cameraGeoPose: GeoPose? = null,
    val horizontalAccuracy: Float? = null,
    val verticalAccuracy: Float? = null,
    val headingAccuracy: Float? = null
) {

    val isGeoReferenced = cameraGeoPose != null

    constructor(scan: Scan, cameraPosition: Float3, earth: Earth?) : this(
        scan.frameCount,
        cameraPosition,
        earth?.cameraGeospatialPose?.toGeoPose(),
        earth?.cameraGeospatialPose?.horizontalAccuracy?.toFloat(),
        earth?.cameraGeospatialPose?.verticalAccuracy?.toFloat(),
        earth?.cameraGeospatialPose?.headingAccuracy?.toFloat()
    )

    companion object {
        fun fromCsvString(source: String): FrameMetaData {
            val components = source.split(",")
            val cameraPosition = Float3(
                components[1].toFloat(),
                components[2].toFloat(),
                components[3].toFloat()
            )
            return when (components.size) {
                11 -> {
                    val cameraGeoPose = GeoPose(
                        components[4].toDouble(),
                        components[5].toDouble(),
                        components[6].toDouble(),
                        components[7].toDouble()
                    )
                    FrameMetaData(
                        components[0].toInt(),
                        cameraPosition,
                        cameraGeoPose,
                        components[8].toFloat(),
                        components[9].toFloat(),
                        components[10].toFloat()
                    )
                }
                4 -> {
                    FrameMetaData(
                        components[0].toInt(),
                        cameraPosition
                    )
                }
                else -> {
                    throw IllegalArgumentException("Invalid FrameMetaData!")
                }
            }
        }
    }

    fun getCsvString(): String {
        cameraGeoPose?.let { geoPose ->
            return "$id,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z},${geoPose.toCsvString()},$horizontalAccuracy,$verticalAccuracy,$headingAccuracy"
        }
        return "$id,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z}"
    }
}
