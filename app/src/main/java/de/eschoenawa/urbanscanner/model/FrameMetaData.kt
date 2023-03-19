package de.eschoenawa.urbanscanner.model

import com.google.ar.core.Earth
import de.eschoenawa.urbanscanner.helper.toGeoPose
import dev.romainguy.kotlin.math.Float3

data class FrameMetaData(
    val id: Int,
    val scanNumber: Int,
    val cameraPosition: Float3,
    val cameraGeoPose: GeoPose? = null,
    val horizontalAccuracy: Float? = null,
    val verticalAccuracy: Float? = null,
    val headingAccuracy: Float? = null
) {

    val isGeoReferenced = cameraGeoPose != null

    constructor(scan: Scan, cameraPosition: Float3, earth: Earth?) : this(
        scan.frameCount,
        scan.currentScanNumber,
        cameraPosition,
        earth?.cameraGeospatialPose?.toGeoPose(),
        earth?.cameraGeospatialPose?.horizontalAccuracy?.toFloat(),
        earth?.cameraGeospatialPose?.verticalAccuracy?.toFloat(),
        earth?.cameraGeospatialPose?.headingAccuracy?.toFloat()
    )

    constructor(
        scan: Scan,
        cameraPosition: Float3,
        cameraGeoPose: GeoPose,
        horizontalAccuracy: Float,
        verticalAccuracy: Float,
        headingAccuracy: Float
    ) : this(
        scan.frameCount,
        scan.currentScanNumber,
        cameraPosition,
        cameraGeoPose,
        horizontalAccuracy,
        verticalAccuracy,
        headingAccuracy
    )

    companion object {
        fun fromCsvString(source: String): FrameMetaData {
            val components = source.split(",")
            val cameraPosition = Float3(
                components[2].toFloat(),
                components[3].toFloat(),
                components[4].toFloat()
            )
            return when (components.size) {
                12 -> {
                    val cameraGeoPose = GeoPose(
                        components[5].toDouble(),
                        components[6].toDouble(),
                        components[7].toDouble(),
                        components[8].toDouble()
                    )
                    FrameMetaData(
                        components[0].toInt(),
                        components[1].toInt(),
                        cameraPosition,
                        cameraGeoPose,
                        components[9].toFloat(),
                        components[10].toFloat(),
                        components[11].toFloat()
                    )
                }
                5 -> {
                    FrameMetaData(
                        components[0].toInt(),
                        components[1].toInt(),
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
            return "$id,$scanNumber,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z},${geoPose.toCsvString()},$horizontalAccuracy,$verticalAccuracy,$headingAccuracy"
        }
        return "$id,$scanNumber,${cameraPosition.x},${cameraPosition.y},${cameraPosition.z}"
    }
}
