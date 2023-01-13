package de.eschoenawa.urbanscanner.helper

import com.google.ar.core.GeospatialPose
import de.eschoenawa.urbanscanner.model.GeoPose
import de.eschoenawa.urbanscanner.model.PixelData
import dev.romainguy.kotlin.math.Float3
import java.lang.Math.toDegrees
import kotlin.math.cos

private const val EARTH_RADIUS = 6_371_000  // meters

fun PixelData.getGeoPose(cameraPosition: Float3, cameraGeoPose: GeoPose): GeoPose {
    // Points are given in EUS coordinate system (East-Up-South)
    val pointPosition = Float3(this.x, this.y, this.z)
    val offset = pointPosition - cameraPosition
    // Negate offset.z because offset formula expects offset in north direction
    val latOffsetRadians = -offset.z / EARTH_RADIUS
    val longOffsetRadians = offset.x / (EARTH_RADIUS * cos(cameraGeoPose.latitudeRad))
    val resultLatDeg = toDegrees(cameraGeoPose.latitudeRad + latOffsetRadians)
    val resultLongDeg = toDegrees(cameraGeoPose.longitudeRad + longOffsetRadians)
    val pointAltitude = cameraGeoPose.altitude + (pointPosition.y - cameraPosition.y)
    return GeoPose(
        resultLatDeg,
        resultLongDeg,
        pointAltitude
    )
}

fun GeospatialPose.toGeoPose(): GeoPose {
    return GeoPose(
        latitude,
        longitude,
        altitude,
        heading
    )
}
